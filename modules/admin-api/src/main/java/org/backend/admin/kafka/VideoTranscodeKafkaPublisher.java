package org.backend.admin.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.events.video.VideoTranscodeEventPublisher;
import core.events.video.VideoTranscodeRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoTranscodeKafkaPublisher implements VideoTranscodeEventPublisher {

    public static final String TOPIC = "video.transcode.requested";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(VideoTranscodeRequestedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, String.valueOf(event.videoFileId()), payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("KAFKA_PUBLISH_FAILED: json serialize error", e);
        }
    }
}