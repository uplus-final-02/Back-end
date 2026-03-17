package org.backend.transcoder.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.events.video.VideoTranscodeResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoTranscodeResultPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.video-transcode-result}")
    private String resultTopic;

    public void publish(VideoTranscodeResultEvent event) {
        String key = (event.videoFileId() != null) ? String.valueOf(event.videoFileId()) : event.eventId();
        kafkaTemplate.send(resultTopic, key, toJson(event));
    }

    private String toJson(VideoTranscodeResultEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("KAFKA_EVENT_JSON_SERIALIZE_FAILED", e);
        }
    }
}