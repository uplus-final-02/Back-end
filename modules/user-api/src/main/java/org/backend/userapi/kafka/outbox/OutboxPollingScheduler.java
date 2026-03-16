package org.backend.userapi.kafka.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPollingScheduler {

    private final VideoTranscodeOutboxJdbcRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.video-transcode-user}")
    private String topic;

    private static final int BATCH_SIZE = 100;
    private static final long KAFKA_ACK_TIMEOUT_SEC = 10;

    @Scheduled(fixedDelay = 5_000)
    public void pollAndPublish() {
        var rows = outboxRepository.findOldestByTopic(topic, BATCH_SIZE);
        if (rows.isEmpty()) return;

        for (var row : rows) {
            try {
                kafkaTemplate
                        .send(row.topic(), String.valueOf(row.targetId()), row.payload())
                        .get(KAFKA_ACK_TIMEOUT_SEC, TimeUnit.SECONDS);

                outboxRepository.deleteById(row.id());

                log.info("[OUTBOX][PUBLISHED][USER] id={}, eventId={}, topic={}, targetId={}",
                        row.id(), row.eventId(), row.topic(), row.targetId());

            } catch (Exception e) {
                log.error("[OUTBOX][FAILED][USER] id={}, eventId={} — retry later: {}",
                        row.id(), row.eventId(), e.getMessage(), e);
            }
        }
    }
}