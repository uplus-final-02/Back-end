package org.backend.admin.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.events.video.VideoTranscodeResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.admin.sse.AdminPublishSseService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTranscodeResultConsumer {

    private final ObjectMapper objectMapper;
    private final AdminPublishSseService sseService;

    @KafkaListener(
            topics = "${app.kafka.topics.video-transcode-admin-result}",
            groupId = "${app.kafka.consumer.admin-result-group}"
    )
    public void onMessage(String message) {
        VideoTranscodeResultEvent event;
        try {
            event = objectMapper.readValue(message, VideoTranscodeResultEvent.class);
        } catch (Exception e) {
            log.error("[ADMIN_RESULT][INVALID] message={}", message, e);
            return;
        }

        log.info("[ADMIN_RESULT][RECV] eventId={}, status={}, contentId={}, videoFileId={}",
                event.eventId(), event.transcodeStatus(), event.contentId(), event.videoFileId());

        if (event.contentId() != null) {
            sseService.publish(event.contentId(), "TRANSCODE_RESULT", message);
        }
    }
}