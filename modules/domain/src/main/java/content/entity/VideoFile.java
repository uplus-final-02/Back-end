package content.entity;

import common.enums.TranscodeStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "video_files")
public class VideoFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false, unique = true)
    private Video video;

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
    public VideoFile(Video video, String originalUrl, String hlsUrl, int durationSec, TranscodeStatus transcodeStatus) {
        this.video = video;
        this.originalUrl = originalUrl;
        this.hlsUrl = hlsUrl;
        this.durationSec = durationSec;
        this.transcodeStatus = (transcodeStatus != null) ? transcodeStatus : TranscodeStatus.WAITING;
    }

    // objectKey 저장(컬럼명은 original_url이지만 key로 운용)
    public void updateOriginalKey(String objectKey) {
        this.originalUrl = objectKey;
    }

    // 트랜스코딩 상태 업데이트 등 필요한 비즈니스 메서드 추가 가능
    public void updateTranscodeStatus(TranscodeStatus status) {
        this.transcodeStatus = status;
    }

    // HLS objectKey 저장(컬럼명은 hls_url)
    public void updateHlsKey(String hlsObjectKey) {
        this.hlsUrl = hlsObjectKey;
    }

    // duration 저장
    public void updateDurationSec(int durationSec) {
        this.durationSec = durationSec;
    }
}