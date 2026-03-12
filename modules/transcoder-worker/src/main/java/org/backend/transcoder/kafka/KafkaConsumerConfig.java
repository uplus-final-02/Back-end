package org.backend.transcoder.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Kafka Consumer 오류 처리 설정.
 *
 * <p>[재시도 전략 — ExponentialBackOff]
 * <pre>
 *   1차 재시도:  1s
 *   2차 재시도:  2s
 *   3차 재시도:  4s
 *   4차 재시도:  8s
 *   5차 재시도: 16s
 *   → 5회 재시도 후 DLQ(video.transcode.dlt)로 이동
 * </pre>
 *
 * <p>[비재시도 예외]
 * {@code IllegalArgumentException}: 메시지 형식 오류(잘못된 JSON) — 재시도해도 해결 불가 → DLQ 즉시 이동
 *
 * <p>[Spring Boot 자동 적용]
 * {@code DefaultErrorHandler} 빈이 단독으로 등록되면 {@code KafkaListenerContainerFactoryConfigurer}가
 * 자동으로 {@code ConcurrentKafkaListenerContainerFactory}에 적용한다.
 * {@code ConcurrentKafkaListenerContainerFactory} 빈 재정의 불필요.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    private static final String DLT_TOPIC = "video.transcode.dlt";

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {

        // 실패 메시지를 DLT 토픽으로 발행
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    log.error("[DLQ] 메시지 DLT 이동 — topic={}, offset={}, cause={}",
                            record.topic(), record.offset(), ex.getMessage());
                    return new TopicPartition(DLT_TOPIC, -1);  // -1: Kafka가 파티션 결정
                }
        );

        // 1s → 2s → 4s → 8s → 16s (5회 재시도)
        ExponentialBackOffWithMaxRetries backoff = new ExponentialBackOffWithMaxRetries(5);
        backoff.setInitialInterval(1_000L);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(30_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backoff);

        // JSON 파싱 오류 — 재시도 불가, DLQ 즉시 이동
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

        return errorHandler;
    }
}
