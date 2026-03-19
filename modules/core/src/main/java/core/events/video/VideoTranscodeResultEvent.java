package core.events.video;

import java.util.UUID;

public record VideoTranscodeResultEvent(
        String eventId,
        long occurredAtEpochMillis,
        String requestType,      // HLS_ADMIN / HLS_USER
        Long contentId,
        Long videoId,
        Long videoFileId,
        String transcodeStatus,  // DONE / FAILED
        String hlsMasterKey,
        Integer durationSec,
        String reason            // 실패 사유(optional)
) {
    public static VideoTranscodeResultEvent done(VideoTranscodeRequestedEvent req, String hlsKey, int durationSec) {
        return new VideoTranscodeResultEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                req.requestType(),
                req.contentId(),
                req.videoId(),
                req.videoFileId(),
                "DONE",
                hlsKey,
                durationSec,
                null
        );
    }

    public static VideoTranscodeResultEvent failed(VideoTranscodeRequestedEvent req, String reason) {
        return new VideoTranscodeResultEvent(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                req.requestType(),
                req.contentId(),
                req.videoId(),
                req.videoFileId(),
                "FAILED",
                null,
                null,
                reason
        );
    }
}