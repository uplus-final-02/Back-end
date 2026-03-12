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

    /**
     * Kafka 메시지 수신 후 트랜스코딩 서비스로 위임.
     *
     * <p>[예외 처리 전략]
     * <ul>
     *   <li>JSON 파싱 실패 → {@link IllegalArgumentException} 변환
     *       → {@link KafkaConsumerConfig}에서 non-retryable로 등록 → DLQ 즉시 이동
     *   <li>트랜스코딩 실패 → 예외 그대로 전파
     *       → {@link KafkaConsumerConfig} {@code DefaultErrorHandler}가 ExponentialBackOff로 재시도
     *       → 재시도 소진 시 DLQ 이동
     * </ul>
     */
    @KafkaListener(
            topics = "${app.kafka.topics.video-transcode}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(String message) {
        VideoTranscodeRequestedEvent event;
        try {
            event = objectMapper.readValue(message, VideoTranscodeRequestedEvent.class);
        } catch (Exception e) {
            log.error("[TRANSCODE][INVALID_MESSAGE] 메시지 형식 오류 — DLQ 이동: message={}", message, e);
            throw new IllegalArgumentException("INVALID_MESSAGE_FORMAT", e);
        }

        videoTranscodeService.transcode(event);
    }
}