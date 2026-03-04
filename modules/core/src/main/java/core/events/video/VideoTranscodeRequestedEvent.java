package core.events.video;

import java.time.Instant;
import java.util.UUID;

public record VideoTranscodeRequestedEvent(
        String eventId,
        Instant occurredAt,
        Long contentId,
        Long videoId,
        Long videoFileId,
        String originalKey,
        String requestType // "HLS"
) {
    public static VideoTranscodeRequestedEvent of(Long contentId, Long videoId, Long videoFileId, String originalKey) {
        return new VideoTranscodeRequestedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                contentId,
                videoId,
                videoFileId,
                originalKey,
                "HLS"
        );
    }
}