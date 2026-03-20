// modules/transcoder-worker/src/main/java/org/backend/transcoder/kafka/KafkaConsumerConfig.java
package org.backend.transcoder.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.backend.transcoder.exception.TranscodeNonRetryableException;
import org.backend.transcoder.exception.TranscodeRetryableException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    String dltTopic = record.topic() + ".dlt";
                    log.error("[DLQ] publish to {} (from topic={}, partition={}, offset={}) cause={}",
                            dltTopic, record.topic(), record.partition(), record.offset(), ex.getMessage());
                    return new TopicPartition(dltTopic, record.partition());
                }
        );

        ExponentialBackOffWithMaxRetries backoff = new ExponentialBackOffWithMaxRetries(4);
        backoff.setInitialInterval(1_000L);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(10_000L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backoff);

        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                TranscodeNonRetryableException.class
        );

        // Retryable은 여기 추가할 필요 없음(기본: retry 후 DLQ)
        // TranscodeRetryableException은 retry하고, 결국 안 되면 DLQ로 감

        return errorHandler;
    }
}