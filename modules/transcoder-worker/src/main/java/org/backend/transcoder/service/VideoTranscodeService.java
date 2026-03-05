package org.backend.transcoder.service;

import common.enums.TranscodeStatus;
import content.entity.VideoFile;
import content.repository.VideoFileRepository;
import core.events.video.VideoTranscodeRequestedEvent;
import core.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.*;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTranscodeService {

    private final VideoFileRepository videoFileRepository;
    private final ObjectStorageService objectStorageService;

    @Transactional
    public void transcode(VideoTranscodeRequestedEvent event) {
        VideoFile vf = videoFileRepository.findById(event.videoFileId())
                .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + event.videoFileId()));

        if (vf.getTranscodeStatus() == TranscodeStatus.DONE) {
            log.info("[TRANSCODE][SKIP] already DONE. videoFileId={}", vf.getId());
            return;
        }

        vf.updateTranscodeStatus(TranscodeStatus.PROCESSING);

        Path workDir = null;
        try {
            log.info("[TRANSCODE][START] eventId={}, videoFileId={}, originalKey={}",
                    event.eventId(), event.videoFileId(), event.originalKey());

            workDir = Files.createTempDirectory("transcode-" + event.videoFileId() + "-");
            Path inputMp4 = workDir.resolve("input.mp4");
            Path hlsDir = workDir.resolve("hls");
            Files.createDirectories(hlsDir);

            // 1) MinIO -> 로컬 다운로드
            objectStorageService.downloadToFile(event.originalKey(), inputMp4);

            // 2) FFmpeg로 HLS 생성
            Path master = hlsDir.resolve("master.m3u8");
            runFfmpegHls(inputMp4, master);

            // 3) duration 추출(ffprobe)
            int durationSec = probeDurationSec(inputMp4);

            // 4) HLS 결과물 업로드 (폴더 전체)
            String baseKey = "hls/" + event.videoFileId();
            uploadDirectoryToMinio(hlsDir, baseKey);

            String hlsMasterKey = baseKey + "/master.m3u8";

            // 5) DB 업데이트
            vf.updateHlsKey(hlsMasterKey);
            vf.updateDurationSec(durationSec);
            vf.updateTranscodeStatus(TranscodeStatus.DONE);

            log.info("[TRANSCODE][DONE] videoFileId={}, hlsKey={}, durationSec={}",
                    vf.getId(), hlsMasterKey, durationSec);

        } catch (Exception e) {
            log.error("[TRANSCODE][FAILED] videoFileId={}, cause={}", event.videoFileId(), e.getMessage(), e);
            vf.updateTranscodeStatus(TranscodeStatus.FAILED);
            throw new IllegalStateException("TRANSCODE_FAILED", e);
        } finally {
            if (workDir != null) {
                try {
                    deleteRecursively(workDir);
                } catch (Exception ignore) {}
            }
        }
    }

    private void runFfmpegHls(Path inputMp4, Path masterM3u8) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", inputMp4.toAbsolutePath().toString(),
                "-c:v", "libx264",
                "-c:a", "aac",
                "-preset", "veryfast",
                "-g", "48",
                "-sc_threshold", "0",
                "-hls_time", "4",
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", masterM3u8.getParent().resolve("seg_%05d.ts").toString(),
                masterM3u8.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("FFMPEG_FAILED code=" + code + "\n" + out);
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

                        String contentType = guessContentType(path);
                        objectStorageService.uploadFromFile(key, path, contentType);

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private String guessContentType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (name.endsWith(".ts")) return "video/mp2t";
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