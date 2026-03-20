package org.backend.admin.kafka.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final int BATCH_SIZE = 100;
    private static final long KAFKA_ACK_TIMEOUT_SEC = 10;

    @Scheduled(fixedDelay = 5_000)
    public void pollAndPublish() {
        var rows = outboxRepository.findOldest(BATCH_SIZE);
        if (rows.isEmpty()) return;

        for (var row : rows) {
            try {
                kafkaTemplate
                        .send(row.topic(), String.valueOf(row.targetId()), row.payload())
                        .get(KAFKA_ACK_TIMEOUT_SEC, TimeUnit.SECONDS);

                outboxRepository.deleteById(row.id());

                log.info("[OUTBOX][PUBLISHED] id={}, eventId={}, topic={}, targetType={}, targetId={}",
                        row.id(), row.eventId(), row.topic(), row.targetType(), row.targetId());

            } catch (Exception e) {
                log.error("[OUTBOX][FAILED] id={}, eventId={}, topic={}, targetId={} - retry: {}",
                        row.id(), row.eventId(), row.topic(), row.targetId(), e.getMessage(), e);
            }
        }
    }
}