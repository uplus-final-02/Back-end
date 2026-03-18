package org.backend.transcoder.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.events.video.VideoTranscodeResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodeResultPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.video-transcode-result:}")
    private String commonResultTopic;

    @Value("${app.kafka.topics.admin-result:}")
    private String adminResultTopic;

    @Value("${app.kafka.topics.user-result:}")
    private String userResultTopic;

    public void publish(VideoTranscodeResultEvent event) {
        String topic = resolveTopic(event);

        String key = (event.videoFileId() != null)
                ? String.valueOf(event.videoFileId())
                : event.eventId();

        String payload = toJson(event);

        log.info("[KAFKA][RESULT][PUBLISH] topic={}, key={}, eventId={}, requestType={}",
                topic, key, event.eventId(), event.requestType());

        kafkaTemplate.send(topic, key, payload)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("[KAFKA][RESULT][FAIL] topic={}, key={}, eventId={}",
                                topic, key, event.eventId(), ex);
                    } else {
                        log.info("[KAFKA][RESULT][OK] topic={}, partition={}, offset={}, eventId={}",
                                topic,
                                res.getRecordMetadata().partition(),
                                res.getRecordMetadata().offset(),
                                event.eventId());
                    }
                });
    }

    private String resolveTopic(VideoTranscodeResultEvent event) {
        String rt = (event.requestType() == null) ? "HLS_ADMIN" : event.requestType();

        boolean hasUser = userResultTopic != null && !userResultTopic.isBlank();
        boolean hasAdmin = adminResultTopic != null && !adminResultTopic.isBlank();

        if (hasUser || hasAdmin) {
            if ("HLS_USER".equalsIgnoreCase(rt)) {
                if (!hasUser) throw new IllegalStateException("USER_RESULT_TOPIC_NOT_CONFIGURED");
                return userResultTopic;
            }
            if (!hasAdmin) throw new IllegalStateException("ADMIN_RESULT_TOPIC_NOT_CONFIGURED");
            return adminResultTopic;
        }

        if (commonResultTopic == null || commonResultTopic.isBlank()) {
            throw new IllegalStateException("COMMON_RESULT_TOPIC_NOT_CONFIGURED (app.kafka.topics.video-transcode-result)");
        }
        return commonResultTopic;
    }

    private String toJson(VideoTranscodeResultEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("KAFKA_EVENT_JSON_SERIALIZE_FAILED", e);
        }
    }
}