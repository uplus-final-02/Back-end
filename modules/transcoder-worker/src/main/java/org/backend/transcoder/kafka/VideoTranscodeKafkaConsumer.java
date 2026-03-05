package org.backend.transcoder.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.enums.TranscodeStatus;
import content.entity.VideoFile;
import content.repository.VideoFileRepository;
import core.events.video.VideoTranscodeRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodeKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final VideoFileRepository videoFileRepository;

    @KafkaListener(
            topics = "${app.kafka.topics.video-transcode:video.transcode.requested}",
            groupId = "${spring.kafka.consumer.group-id:transcoder-worker}"
    )
    @Transactional
    public void onMessage(String message) {
        try {
            VideoTranscodeRequestedEvent event =
                    objectMapper.readValue(message, VideoTranscodeRequestedEvent.class);

            VideoFile vf = videoFileRepository.findById(event.videoFileId())
                    .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + event.videoFileId()));

            if (vf.getTranscodeStatus() == TranscodeStatus.DONE || vf.getTranscodeStatus() == TranscodeStatus.FAILED) {
                log.info("[TRANSCODE][SKIP] already finished. videoFileId={}, status={}",
                        vf.getId(), vf.getTranscodeStatus());
                return;
            }

            // 1) PROCESSING
            vf.updateTranscodeStatus(TranscodeStatus.PROCESSING);

            log.info("[TRANSCODE][START] eventId={}, contentId={}, videoId={}, videoFileId={}, originalKey={}",
                    event.eventId(), event.contentId(), event.videoId(), event.videoFileId(), event.originalKey());

            // 2) mock 트랜스코딩 시간
            Thread.sleep(1500L);

            // 3) mock 결과 저장 (hls_url, duration_sec)
            int duration = ThreadLocalRandom.current().nextInt(30, 300);
            String hlsObjectKey = "hls/" + event.videoFileId() + "/master.m3u8";

            vf.updateHlsKey(hlsObjectKey);
            vf.updateDurationSec(duration);
            vf.updateTranscodeStatus(TranscodeStatus.DONE);

            log.info("[TRANSCODE][DONE][MOCK] videoFileId={}, hlsKey={}, durationSec={}",
                    vf.getId(), hlsObjectKey, duration);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("[TRANSCODE][INTERRUPTED] message={}", message, ie);
            throw new IllegalStateException("TRANSCODE_INTERRUPTED", ie);
        } catch (Exception e) {
            log.error("[TRANSCODE][CONSUME_FAIL] message={}", message, e);
            throw new IllegalStateException("KAFKA_CONSUME_FAILED", e);
        }
    }
}