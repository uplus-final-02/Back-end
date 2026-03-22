// modules/transcoder-worker/src/main/java/org/backend/transcoder/kafka/VideoTranscodeKafkaConsumer.java
package org.backend.transcoder.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.events.video.VideoTranscodeRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.transcoder.service.VideoTranscodeService;
import org.backend.transcoder.support.WorkerId;
import org.springframework.kafka.annotation.KafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodeKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final VideoTranscodeService videoTranscodeService;

    @KafkaListener(
            topics = "${app.kafka.topics.video-transcode}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        final String workerId = WorkerId.get();

        VideoTranscodeRequestedEvent event;
        try {
            event = objectMapper.readValue(record.value(), VideoTranscodeRequestedEvent.class);
        } catch (Exception e) {
            log.error("[TRANSCODE][INVALID_MESSAGE][{}] topic={}, partition={}, offset={}, key={}, payload={}",
                    workerId, record.topic(), record.partition(), record.offset(), record.key(), record.value(), e);
            throw new IllegalArgumentException("INVALID_MESSAGE_FORMAT", e);
        }

        log.info("[KAFKA][RECV][{}] topic={}, partition={}, offset={}, key={}, eventId={}, requestType={}, videoFileId={}, originalKey={}",
                workerId,
                record.topic(), record.partition(), record.offset(), record.key(),
                event.eventId(), event.requestType(), event.videoFileId(), event.originalKey()
        );

        try {
            videoTranscodeService.transcode(event);
        } catch (Exception e) {
            log.error("[TRANSCODE][FATAL_ERROR][{}] 인코딩 중 치명적 예외 발생! eventId={}, error={}",
                workerId, event.eventId(), e.getMessage(), e);
            throw e; // 카프카가 재시도하거나 DLQ로 보낼 수 있게 에러를 다시 밖으로 던져줌
        }

        log.info("[KAFKA][ACK][{}] topic={}, partition={}, offset={}, eventId={}",
                workerId, record.topic(), record.partition(), record.offset(), event.eventId());
    }
}