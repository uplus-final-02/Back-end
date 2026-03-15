package content.entity;

import common.entity.BaseTimeEntity;
import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "user_video_files",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_user_video_files_content_id", columnNames = "content_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserVideoFile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false, unique = true)
    private UserContent content;

    @Column(name = "original_url", length = 500)
    private String originalUrl;

    @Column(name = "hls_url", length = 500)
    private String hlsUrl;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_status", nullable = false, length = 20)
    private VideoStatus videoStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "transcode_status", nullable = false, length = 20)
    private TranscodeStatus transcodeStatus;

    @Builder
    public UserVideoFile(
            UserContent content,
            String originalUrl,
            String hlsUrl,
            Integer durationSec,
            VideoStatus videoStatus,
            TranscodeStatus transcodeStatus
    ) {
        this.content = content;
        this.originalUrl = originalUrl;
        this.hlsUrl = hlsUrl;
        this.durationSec = (durationSec != null) ? durationSec : 0;

        this.videoStatus = (videoStatus != null) ? videoStatus : VideoStatus.DRAFT;
        this.transcodeStatus = (transcodeStatus != null) ? transcodeStatus : TranscodeStatus.PENDING_UPLOAD;
    }

    public void updateOriginalKey(String objectKey) {
        this.originalUrl = objectKey;
    }

    public void updateHlsKey(String hlsObjectKey) {
        this.hlsUrl = hlsObjectKey;
    }

    public void updateDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }

    public void updateTranscodeStatus(TranscodeStatus status) {
        this.transcodeStatus = status;
    }

    public void updateVideoStatus(VideoStatus status) {
        this.videoStatus = status;
    }
}