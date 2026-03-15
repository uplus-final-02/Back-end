package content.entity;

import common.enums.TranscodeStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_video_files",
        indexes = {
                @Index(name = "idx_user_video_files_user_content", columnList = "user_content_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_video_files_user_content", columnNames = {"user_content_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserVideoFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_file_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_content_id", nullable = false)
    private UserContent userContent;

    @Column(name = "original_url", length = 500)
    private String originalUrl;

    @Column(name = "hls_url", length = 500)
    private String hlsUrl;

    @Column(name = "duration_sec", nullable = false)
    private int durationSec;

    @Enumerated(EnumType.STRING)
    @Column(name = "transcode_status", nullable = false, length = 20)
    private TranscodeStatus transcodeStatus;

    @Builder
    public UserVideoFile(UserContent userContent, String originalUrl, String hlsUrl, int durationSec, TranscodeStatus transcodeStatus) {
        this.userContent = userContent;
        this.originalUrl = originalUrl;
        this.hlsUrl = hlsUrl;
        this.durationSec = durationSec;
        this.transcodeStatus = (transcodeStatus != null) ? transcodeStatus : TranscodeStatus.PENDING_UPLOAD;
    }

    public void updateOriginalKey(String objectKey) { this.originalUrl = objectKey; }

    public void updateHlsKey(String hlsKey) { this.hlsUrl = hlsKey; }

    public void updateDurationSec(int sec) { this.durationSec = sec; }

    public void updateTranscodeStatus(TranscodeStatus status) { this.transcodeStatus = status; }
}