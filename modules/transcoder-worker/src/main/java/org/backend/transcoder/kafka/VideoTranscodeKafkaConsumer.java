package org.backend.transcoder.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.events.video.VideoTranscodeRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.transcoder.service.VideoTranscodeService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodeKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final VideoTranscodeService videoTranscodeService;

    @KafkaListener(
            topics = "${app.kafka.topics.video-transcode}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(String message) {
        try {
            VideoTranscodeRequestedEvent event =
                    objectMapper.readValue(message, VideoTranscodeRequestedEvent.class);

            videoTranscodeService.transcode(event);

        } catch (Exception e) {
            log.error("[TRANSCODE][CONSUME_FAIL] message={}", message, e);
            throw new IllegalStateException("KAFKA_CONSUME_FAILED", e);
        }
    }
}