package org.backend.transcoder.service;

import common.enums.TranscodeStatus;
import content.entity.VideoFile;
import content.repository.VideoFileRepository;
import core.events.video.VideoTranscodeRequestedEvent;
import core.storage.MinioObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.transcoder.kafka.ProcessedKafkaEventJdbcRepository;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTranscodeService {

    private final VideoFileRepository videoFileRepository;
    private final ObjectStorageService objectStorageService;
    private final ProcessedKafkaEventJdbcRepository processedEventRepository;
    private final MinioObjectStorageService objectStorageService;
    private final VideoFileStatusService statusService;
    private final ProcessedKafkaEventJdbcRepository processedEventRepository;

    public void transcode(VideoTranscodeRequestedEvent event) {
        // ① 이벤트 레벨 멱등성: 동일 eventId 중복 처리 방지 (Outbox at-least-once 보상)
        if (processedEventRepository.isProcessed(event.eventId())) {
            log.info("[TRANSCODE][SKIP_DUPLICATE] 이미 처리된 이벤트 — eventId={}", event.eventId());
            return;
        }

        VideoFile vf = videoFileRepository.findById(event.videoFileId())
                .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + event.videoFileId()));

        // ② 상태 레벨 멱등성: TranscodeStatus.DONE 이면 processed 기록 후 스킵
        if (vf.getTranscodeStatus() == TranscodeStatus.DONE) {
            log.info("[TRANSCODE][SKIP] already DONE. videoFileId={}", vf.getId());
            processedEventRepository.markProcessed(event.eventId(), event.videoId());
            return;
        }

        Path workDir = null;
        try {
            log.info("[TRANSCODE][START] eventId={}, videoFileId={}, originalKey={}",
                    event.eventId(), event.videoFileId(), event.originalKey());

            statusService.markProcessing(event.videoFileId());

            workDir = Files.createTempDirectory("transcode-" + event.videoFileId() + "-");
            Path inputMp4 = workDir.resolve("input.mp4");
            Path hlsDir = workDir.resolve("hls");
            Files.createDirectories(hlsDir);

            objectStorageService.downloadToFile(event.originalKey(), inputMp4);

            Path master = hlsDir.resolve("master.m3u8");
            runFfmpegAbrHls(inputMp4, hlsDir, master);

            int durationSec = probeDurationSec(inputMp4);

            String baseKey = "hls/" + event.videoFileId();
            uploadDirectoryToMinio(hlsDir, baseKey);

            String hlsMasterKey = baseKey + "/master.m3u8";

            // 5) DB 업데이트 + 멱등성 키 기록 (동일 트랜잭션으로 커밋)
            vf.updateHlsKey(hlsMasterKey);
            vf.updateDurationSec(durationSec);
            vf.updateTranscodeStatus(TranscodeStatus.DONE);
            processedEventRepository.markProcessed(event.eventId(), event.videoId());
            statusService.markDone(event.videoFileId(), hlsMasterKey, durationSec);
            processedEventRepository.markProcessed(event.eventId(), event.videoFileId());

            log.info("[TRANSCODE][DONE] eventId={}, videoFileId={}, hlsKey={}, durationSec={}",
                    event.eventId(), event.videoFileId(), hlsMasterKey, durationSec);

        } catch (Exception e) {
            log.error("[TRANSCODE][FAILED] videoFileId={}, cause={}", event.videoFileId(), e.getMessage(), e);

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

    private void runFfmpegAbrHls(Path inputMp4, Path hlsDir, Path masterM3u8) throws Exception {
        Files.createDirectories(hlsDir.resolve("v0"));
        Files.createDirectories(hlsDir.resolve("v1"));
        Files.createDirectories(hlsDir.resolve("v2"));
        Files.createDirectories(hlsDir.resolve("v3"));
        Files.createDirectories(hlsDir.resolve("v4"));
        Files.createDirectories(hlsDir.resolve("v5"));

        String segPattern = hlsDir.resolve("v%v").resolve("seg_%05d.ts").toString();
        String variantPlaylist = hlsDir.resolve("v%v").resolve("prog_index.m3u8").toString();

        String filter =
                "[0:v]" +
                        "split=6[v1080][v720][v480][v360][v240][v144];" +

                        "[v1080]scale=w=1920:h=1080:force_original_aspect_ratio=decrease:flags=lanczos," +
                        "pad=1920:1080:(ow-iw)/2:(oh-ih)/2,format=yuv420p[v1080o];" +

                        "[v720]scale=w=1280:h=720:force_original_aspect_ratio=decrease:flags=lanczos," +
                        "pad=1280:720:(ow-iw)/2:(oh-ih)/2,format=yuv420p[v720o];" +

                        "[v480]scale=w=854:h=480:force_original_aspect_ratio=decrease:flags=lanczos," +
                        "pad=854:480:(ow-iw)/2:(oh-ih)/2,format=yuv420p[v480o];" +

                        "[v360]scale=w=640:h=360:force_original_aspect_ratio=decrease:flags=lanczos," +
                        "pad=640:360:(ow-iw)/2:(oh-ih)/2,format=yuv420p[v360o];" +

                        "[v240]scale=w=426:h=240:force_original_aspect_ratio=decrease:flags=lanczos," +
                        "pad=426:240:(ow-iw)/2:(oh-ih)/2,format=yuv420p[v240o];" +

                        "[v144]scale=w=256:h=144:force_original_aspect_ratio=decrease:flags=lanczos," +
                        "pad=256:144:(ow-iw)/2:(oh-ih)/2,format=yuv420p[v144o]";

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",

                "-analyzeduration", "100M",
                "-probesize", "100M",

                "-i", inputMp4.toAbsolutePath().toString(),

                "-filter_complex", filter,

                "-map", "[v1080o]", "-map", "0:a:0?",
                "-map", "[v720o]",  "-map", "0:a:0?",
                "-map", "[v480o]",  "-map", "0:a:0?",
                "-map", "[v360o]",  "-map", "0:a:0?",
                "-map", "[v240o]",  "-map", "0:a:0?",
                "-map", "[v144o]",  "-map", "0:a:0?",

                "-c:v", "libx264",
                "-preset", "veryfast",
                "-profile:v", "high",
                "-crf", "20",

                "-b:v:0", "2400k", "-maxrate:v:0", "3000k", "-bufsize:v:0", "4800k",
                "-b:v:1", "1200k", "-maxrate:v:1", "1500k", "-bufsize:v:1", "2400k",
                "-b:v:2", "700k",  "-maxrate:v:2", "900k",  "-bufsize:v:2", "1400k",
                "-b:v:3", "450k",  "-maxrate:v:3", "600k",  "-bufsize:v:3", "900k",
                "-b:v:4", "300k",  "-maxrate:v:4", "400k",  "-bufsize:v:4", "600k",
                "-b:v:5", "180k",  "-maxrate:v:5", "250k",  "-bufsize:v:5", "400k",

                "-c:a", "aac",
                "-ac", "2",

                "-b:a:0", "128k",
                "-b:a:1", "96k",
                "-b:a:2", "64k",
                "-b:a:3", "56k",
                "-b:a:4", "48k",
                "-b:a:5", "32k",

                "-g", "48",
                "-keyint_min", "48",
                "-sc_threshold", "0",

                "-f", "hls",
                "-hls_time", "4",
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-hls_segment_filename", segPattern,

                "-master_pl_name", masterM3u8.getFileName().toString(),
                "-var_stream_map",
                "v:0,a:0 v:1,a:1 v:2,a:2 v:3,a:3 v:4,a:4 v:5,a:5",

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