package org.backend.admin.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.events.video.VideoTranscodeEventPublisher;
import core.events.video.VideoTranscodeRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoTranscodeKafkaPublisher implements VideoTranscodeEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.video-transcode-admin}")
    private String adminTopic;

    @Override
    public void publish(VideoTranscodeRequestedEvent event) {
        String key = (event.videoFileId() != null) ? String.valueOf(event.videoFileId())
                : (event.contentId() != null ? String.valueOf(event.contentId()) : event.eventId());

        kafkaTemplate.send(adminTopic, key, toJson(event));
    }

    private String toJson(VideoTranscodeRequestedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("KAFKA_EVENT_JSON_SERIALIZE_FAILED", e);
        }
    }
}