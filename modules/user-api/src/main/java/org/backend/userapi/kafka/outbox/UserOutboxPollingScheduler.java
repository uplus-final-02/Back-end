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
public class UserOutboxPollingScheduler {

    private final UserVideoTranscodeOutboxJdbcRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.video-transcode-user}")
    private String topic;

    private static final int BATCH_SIZE = 100;
    private static final long KAFKA_ACK_TIMEOUT_SEC = 10;

    @Scheduled(fixedDelay = 5_000)
    public void pollAndPublish() {
        var rows = outboxRepository.findOldest(BATCH_SIZE);
        if (rows.isEmpty()) return;

        for (var row : rows) {
            try {
                kafkaTemplate
                        .send(topic, String.valueOf(row.userVideoFileId()), row.payload())
                        .get(KAFKA_ACK_TIMEOUT_SEC, TimeUnit.SECONDS);

                outboxRepository.deleteById(row.id());

            } catch (Exception e) {
                log.error("[OUTBOX][USER] 발행 실패 id={}, eventId={} — 재시도: {}",
                        row.id(), row.eventId(), e.getMessage(), e);
            }
        }
    }
}