package core.events.video;

import java.util.UUID;

public record VideoTranscodeRequestedEvent(
        String eventId,
        long occurredAtEpochMillis,
        Long contentId,
        Long videoId,
        Long videoFileId,
        String originalKey,
        String requestType
) {
    public static VideoTranscodeRequestedEvent of(Long contentId, Long videoId, Long videoFileId, String originalKey) {
        return new VideoTranscodeRequestedEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                contentId,
                videoId,
                videoFileId,
                originalKey,
                "HLS"
        );
    }
}