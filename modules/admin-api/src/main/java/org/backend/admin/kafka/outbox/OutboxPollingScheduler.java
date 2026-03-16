package org.backend.admin.kafka.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.admin.kafka.VideoTranscodeKafkaPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 테이블을 폴링하여 미발행 이벤트를 Kafka로 발행하는 스케줄러.
 *
 * <p>[흐름]
 * <pre>
 *   1. video_transcode_outbox에서 생성순 최대 100행 조회
 *   2. kafkaTemplate.send().get(10s) — 브로커 ACK 확인
 *   3. ACK 확인 후 outbox 행 삭제 (at-least-once 보장)
 *   4. 발행 실패 시 행을 남겨두고 다음 폴링 주기에 재시도
 * </pre>
 *
 * <p>[at-least-once]
 * ACK 확인 후 삭제 직전 크래시 시 같은 이벤트가 재발행될 수 있다.
 * Consumer 측 멱등성(processed_kafka_events)으로 중복 처리를 방지한다.
 *
 * <p>[단일 인스턴스 가정]
 * admin-api는 단일 인스턴스로 운영하므로 ShedLock 미적용.
 * 다중 인스턴스 전환 시 ShedLock 추가 필요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPollingScheduler {

    private final VideoTranscodeOutboxJdbcRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.video-transcode-admin}")
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
                        .send(topic, String.valueOf(row.videoFileId()), row.payload())
                        .get(KAFKA_ACK_TIMEOUT_SEC, TimeUnit.SECONDS);

                outboxRepository.deleteById(row.id());

            } catch (Exception e) {
                log.error("[OUTBOX] 발행 실패 id={}, eventId={} — 재시도: {}",
                        row.id(), row.eventId(), e.getMessage(), e);
            }
        }
    }
}
