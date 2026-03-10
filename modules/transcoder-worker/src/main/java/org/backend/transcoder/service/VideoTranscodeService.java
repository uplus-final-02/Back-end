package org.backend.transcoder.service;

import common.enums.TranscodeStatus;
import content.entity.VideoFile;
import content.repository.VideoFileRepository;
import core.events.video.VideoTranscodeRequestedEvent;
import core.storage.MinioObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTranscodeService {

    private final VideoFileRepository videoFileRepository;
    private final MinioObjectStorageService objectStorageService;

    // ✅ 상태 업데이트는 REQUIRES_NEW로 확정 커밋되도록 분리
    private final VideoFileStatusService statusService;

    /**
     * ✅ @Transactional 제거!
     * 트랜스코딩(FFmpeg)은 오래 걸릴 수 있으니 트랜잭션 밖에서 수행하고,
     * 상태 변경은 statusService(각각 REQUIRES_NEW)로만 처리합니다.
     */
    public void transcode(VideoTranscodeRequestedEvent event) {
        VideoFile vf = videoFileRepository.findById(event.videoFileId())
                .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + event.videoFileId()));

        if (vf.getTranscodeStatus() == TranscodeStatus.DONE) {
            log.info("[TRANSCODE][SKIP] already DONE. videoFileId={}", vf.getId());
            return;
        }

        Path workDir = null;
        try {
            log.info("[TRANSCODE][START] eventId={}, videoFileId={}, originalKey={}",
                    event.eventId(), event.videoFileId(), event.originalKey());

            // 0) 상태: PROCESSING (✅ 이건 반드시 DB에 남게)
            statusService.markProcessing(event.videoFileId());

            workDir = Files.createTempDirectory("transcode-" + event.videoFileId() + "-");
            Path inputMp4 = workDir.resolve("input.mp4");
            Path hlsDir = workDir.resolve("hls");
            Files.createDirectories(hlsDir);

            // 1) MinIO -> 로컬 다운로드
            objectStorageService.downloadToFile(event.originalKey(), inputMp4);

            // 2) FFmpeg로 ABR HLS(3단) 생성
            Path master = hlsDir.resolve("master.m3u8");
            runFfmpegAbrHls(inputMp4, hlsDir, master);

            // 3) duration 추출(ffprobe)
            int durationSec = probeDurationSec(inputMp4);

            // 4) HLS 결과물 업로드 (폴더 전체)
            String baseKey = "hls/" + event.videoFileId();
            uploadDirectoryToMinio(hlsDir, baseKey);

            String hlsMasterKey = baseKey + "/master.m3u8";

            // 5) 상태: DONE (✅ 이 또한 반드시 DB에 남게)
            statusService.markDone(event.videoFileId(), hlsMasterKey, durationSec);

            log.info("[TRANSCODE][DONE] videoFileId={}, hlsKey={}, durationSec={}",
                    event.videoFileId(), hlsMasterKey, durationSec);

        } catch (Exception e) {
            log.error("[TRANSCODE][FAILED] videoFileId={}, cause={}", event.videoFileId(), e.getMessage(), e);

            // ✅ 실패 상태도 REQUIRES_NEW로 “확정 커밋”
            try {
                statusService.markFailed(event.videoFileId());
            } catch (Exception markFailEx) {
                log.error("[TRANSCODE][FAILED][MARK_FAIL_ERROR] videoFileId={}, cause={}",
                        event.videoFileId(), markFailEx.getMessage(), markFailEx);
            }

            throw new IllegalStateException("TRANSCODE_FAILED", e);

        } finally {
            if (workDir != null) {
                try {
                    log.info("[TRANSCODE][WORKDIR] {}", workDir);
                    //deleteRecursively(workDir);
                } catch (Exception ignore) {}
            }
        }
    }

    /**
     * 3단(1080/720/480) ABR HLS 생성
     *
     * ✅ iPhone HEVC Main10 (10bit) 대응:
     *  - filter에 format=yuv420p를 넣어 8bit로 내려서 x264(main/high) 인코딩 가능하게 함
     *
     * ✅ 회전(rotate) 이슈:
     *  - iPhone 영상에 displaymatrix(rotate -90) 자주 붙음
     *  - 필요하면 transpose=1(or 2) 처리해야 정상 방향
     *  - 아래는 “일단 가로로 보정(90도)” 예시를 포함해둔 형태
     *    (만약 이미 정상 방향이면 transpose를 제거하세요)
     */
    private void runFfmpegAbrHls(Path inputMp4, Path hlsDir, Path masterM3u8) throws Exception {
        Files.createDirectories(hlsDir.resolve("v0"));
        Files.createDirectories(hlsDir.resolve("v1"));
        Files.createDirectories(hlsDir.resolve("v2"));

        String segPattern = hlsDir.resolve("v%v").resolve("seg_%05d.ts").toString();
        String variantPlaylist = hlsDir.resolve("v%v").resolve("prog_index.m3u8").toString();

        // ✅ 10bit -> 8bit 변환: format=yuv420p
        // (회전 필요 없으면 transpose=1, 를 지우세요)
        String filter =
                "[0:v]" +
                        // "transpose=1," + // 필요 없으면 주석/삭제
                        "split=3[v1080][v720][v480];" +

                        "[v1080]scale=w=1920:h=1080:force_original_aspect_ratio=decrease:flags=lanczos," +
                        "pad=1920:1080:(ow-iw)/2:(oh-ih)/2," +
                        "format=yuv420p[v1080o];" +

                        "[v720]scale=w=1280:h=720:force_original_aspect_ratio=decrease:flags=lanczos," +
                        "pad=1280:720:(ow-iw)/2:(oh-ih)/2," +
                        "format=yuv420p[v720o];" +

                        "[v480]scale=w=854:h=480:force_original_aspect_ratio=decrease:flags=lanczos," +
                        "pad=854:480:(ow-iw)/2:(oh-ih)/2," +
                        "format=yuv420p[v480o]";

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",

                // ✅ 분석 여유(가끔 iPhone 파일에서 stream 분석 부족 경고 대응)
                "-analyzeduration", "100M",
                "-probesize", "100M",

                "-i", inputMp4.toAbsolutePath().toString(),

                "-filter_complex", filter,

                // map (각 화질 + 오디오 1개 공유)
                "-map", "[v1080o]", "-map", "0:a:0?",
                "-map", "[v720o]",  "-map", "0:a:0?",
                "-map", "[v480o]",  "-map", "0:a:0?",

                // codec (video)
                "-c:v", "libx264",
                "-preset", "veryfast",

                // ✅ main도 가능하지만, 보통 ABR은 high로 많이 둡니다(선택)
                "-profile:v", "high",

                // ✅ CRF는 “품질 기준”, bitrate는 “대역폭 제한”이므로 같이 쓸 때는 ladder 조정이 중요
                "-crf", "20",

                // bitrate ladder (예시: 필요하면 조정)
                "-b:v:0", "5000k", "-maxrate:v:0", "5350k", "-bufsize:v:0", "7500k",
                "-b:v:1", "2800k", "-maxrate:v:1", "3000k", "-bufsize:v:1", "4200k",
                "-b:v:2", "1400k", "-maxrate:v:2", "1600k", "-bufsize:v:2", "2100k",

                // audio (각 rendition에 대응)
                "-c:a", "aac",
                "-b:a:0", "192k",
                "-b:a:1", "128k",
                "-b:a:2", "96k",
                "-ac", "2",

                // keyframe/GOP alignment (4초 세그먼트)
                "-g", "48",
                "-keyint_min", "48",
                "-sc_threshold", "0",

                // HLS
                "-f", "hls",
                "-hls_time", "4",
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-hls_segment_filename", segPattern,

                // master + variants
                "-master_pl_name", masterM3u8.getFileName().toString(),
                "-var_stream_map", "v:0,a:0 v:1,a:1 v:2,a:2",

                variantPlaylist
        );

        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("FFMPEG_ABR_FAILED code=" + code + "\n" + out);
        }
    }

    private int probeDurationSec(Path inputMp4) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                inputMp4.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        int code = p.waitFor();
        if (code != 0 || out.isBlank()) {
            throw new IllegalStateException("FFPROBE_FAILED code=" + code + ", out=" + out);
        }
        double sec = Double.parseDouble(out);
        return (int) Math.round(sec);
    }

    private void uploadDirectoryToMinio(Path dir, String baseKey) throws Exception {
        Files.walk(dir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String relative = dir.relativize(path).toString().replace("\\", "/");
                        String key = baseKey + "/" + relative;

                        String contentType = detectContentType(path);
                        objectStorageService.uploadFromFile(key, path, contentType);

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private String detectContentType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (name.endsWith(".ts")) return "video/MP2T";
        if (name.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }

    private void deleteRecursively(Path root) throws Exception {
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignore) {}
                });
    }
}