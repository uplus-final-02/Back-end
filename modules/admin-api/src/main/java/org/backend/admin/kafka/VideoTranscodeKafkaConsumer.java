package org.backend.admin.kafka;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodeKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final VideoFileRepository videoFileRepository;

    @KafkaListener(
            topics = "video.transcode.requested",
            groupId = "${app.kafka.transcode.group-id:transcoder-dev}"
    )
    @Transactional
    public void onMessage(String message) {
        VideoTranscodeRequestedEvent event;
        VideoFile vf;

        try {
            event = objectMapper.readValue(message, VideoTranscodeRequestedEvent.class);

            vf = videoFileRepository.findById(event.videoFileId())
                    .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + event.videoFileId()));

            if (vf.getTranscodeStatus() == TranscodeStatus.DONE || vf.getTranscodeStatus() == TranscodeStatus.FAILED) {
                log.info("[TRANSCODE][SKIP] already finished. videoFileId={}, status={}",
                        vf.getId(), vf.getTranscodeStatus());
                return;
            }

            vf.updateTranscodeStatus(TranscodeStatus.PROCESSING);

            log.info("[TRANSCODE][START] eventId={}, contentId={}, videoId={}, videoFileId={}, originalKey={}",
                    event.eventId(), event.contentId(), event.videoId(), event.videoFileId(), event.originalKey());

        } catch (Exception e) {
            log.error("[TRANSCODE][CONSUME_FAIL] message={}", message, e);
            throw new IllegalStateException("KAFKA_CONSUME_FAILED", e);
        }

        // 2단계(개발용) 모의 트랜스코딩
        // - 실제 FFmpeg/업로드 전까지 DONE 흐름만 확인
        try {
            Thread.sleep(1500);

            // HLS 결과물 objectKey(있는 척)
            String mockHlsKey = "videos/hls/" + event.videoFileId() + "/master.m3u8";
            int mockDurationSec = 120;

            vf.updateHlsKey(mockHlsKey);
            vf.updateDurationSec(mockDurationSec);
            vf.updateTranscodeStatus(TranscodeStatus.DONE);

            log.info("[TRANSCODE][DONE] eventId={}, videoFileId={}, hlsKey={}, durationSec={}",
                    event.eventId(), event.videoFileId(), mockHlsKey, mockDurationSec);

        } catch (Exception e) {
            vf.updateTranscodeStatus(TranscodeStatus.FAILED);
            log.error("[TRANSCODE][FAILED] eventId={}, videoFileId={}", event.eventId(), event.videoFileId(), e);
            throw new IllegalStateException("TRANSCODE_SIMULATION_FAILED", e);
        }
    }
}