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

    private final VideoFileStatusService statusService;

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

            statusService.markDone(event.videoFileId(), hlsMasterKey, durationSec);

            log.info("[TRANSCODE][DONE] videoFileId={}, hlsKey={}, durationSec={}",
                    event.videoFileId(), hlsMasterKey, durationSec);

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

        String segPattern = hlsDir.resolve("v%v").resolve("seg_%05d.ts").toString();
        String variantPlaylist = hlsDir.resolve("v%v").resolve("prog_index.m3u8").toString();

        String filter =
                "[0:v]" +
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

                "-analyzeduration", "100M",
                "-probesize", "100M",

                "-i", inputMp4.toAbsolutePath().toString(),
                "-filter_complex", filter,

                "-map", "[v1080o]", "-map", "0:a:0?",
                "-map", "[v720o]",  "-map", "0:a:0?",
                "-map", "[v480o]",  "-map", "0:a:0?",

                "-c:v", "libx264",
                "-preset", "veryfast",
                "-profile:v", "high",
                "-crf", "21",

                "-b:v:0", "2000k", "-maxrate:v:0", "2400k", "-bufsize:v:0", "4000k",
                "-b:v:1", "1100k", "-maxrate:v:1", "1400k", "-bufsize:v:1", "2200k",
                "-b:v:2", "650k",  "-maxrate:v:2", "850k",  "-bufsize:v:2", "1300k",

                "-c:a", "aac",
                "-b:a:0", "128k",
                "-b:a:1", "96k",
                "-b:a:2", "64k",
                "-ac", "2",

                "-sc_threshold", "0",
                "-force_key_frames", "expr:gte(t,n_forced*6)",

                "-f", "hls",
                "-hls_time", "6",
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-hls_segment_filename", segPattern,

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