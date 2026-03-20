package core.events.video;

import java.util.UUID;

public record VideoTranscodeRequestedEvent(
        String eventId,
        long occurredAtEpochMillis,
        Long contentId,
        Long videoId,
        Long videoFileId,
        String originalKey,
        String requestType // HLS_ADMIN / HLS_USER
) {

    public static VideoTranscodeRequestedEvent ofAdmin(Long contentId, Long videoId, Long videoFileId, String originalKey) {
        return new VideoTranscodeRequestedEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                contentId,
                videoId,
                videoFileId,
                originalKey,
                "HLS_ADMIN"
        );
    }

    public static VideoTranscodeRequestedEvent ofUser(Long userContentId, Long userVideoFileId, String originalKey) {
        return new VideoTranscodeRequestedEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                userContentId,
                null,
                userVideoFileId,
                originalKey,
                "HLS_USER"
        );
    }

    // 기존 호출부 호환이 필요하면 남겨두되 ADMIN으로 취급
    public static VideoTranscodeRequestedEvent of(Long contentId, Long videoId, Long videoFileId, String originalKey) {
        return ofAdmin(contentId, videoId, videoFileId, originalKey);
    }
}