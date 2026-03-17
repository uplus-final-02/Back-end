package org.backend.admin.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import core.events.video.VideoTranscodeRequestedEvent;
import core.storage.ObjectNotFoundException;
import core.storage.ObjectStorageService;
import core.security.principal.JwtPrincipal;
import core.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.backend.admin.exception.UploadNotCompletedException;
import org.backend.admin.kafka.outbox.VideoTranscodeOutboxJdbcRepository;
import org.backend.admin.video.dto.AdminVideoUploadConfirmRequest;
import org.backend.admin.video.dto.AdminVideoUploadConfirmResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminVideoUploadService {

    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;
    private final ObjectStorageService objectStorageService;
    private final ObjectMapper objectMapper;
    private final VideoTranscodeOutboxJdbcRepository outboxRepository;

    @Value("${app.kafka.topics.video-transcode-admin}")
    private String adminTopic;

    /**
     * 업로드 확정 처리.
     *
     * <p>[Outbox 패턴]
     * VideoFile 상태 변경과 outbox 행 삽입을 동일 트랜잭션으로 묶는다.
     * 커밋 후 {@link org.backend.admin.kafka.outbox.OutboxPollingScheduler}가
     * outbox를 폴링해 Kafka에 발행한다.
     * — DB 커밋 성공 → 반드시 Kafka 발행 보장 (at-least-once)
     * — Kafka 장애 시 API는 정상 응답, 복구 후 자동 재발행
     */
    @Transactional
    public AdminVideoUploadConfirmResponse confirmUpload(JwtPrincipal principal, AdminVideoUploadConfirmRequest req) {
        if (principal == null) {
            throw new IllegalStateException("UNAUTHORIZED: JwtPrincipal is null");
        }

        validate(req);

        var stat = safeStat(req.objectKey());
        if (stat.size() <= 0) {
            throw new UploadNotCompletedException();
        }

        Video video = videoRepository.findById(req.videoId())
                .orElseThrow(() -> new RuntimeException("VIDEO_NOT_FOUND"));

        if (!video.getContent().getId().equals(req.contentId())) {
            throw new RuntimeException("VIDEO_CONTENT_MISMATCH");
        }

        if (video.getStatus() != VideoStatus.DRAFT) {
            throw new RuntimeException("VIDEO_NOT_DRAFT");
        }

        video.updateStatus(VideoStatus.PRIVATE);

        VideoFile vf = videoFileRepository.findByVideoId(video.getId())
                .orElseThrow(() -> new RuntimeException("VIDEO_FILE_NOT_FOUND"));

        vf.updateOriginalKey(req.objectKey());
        vf.updateTranscodeStatus(TranscodeStatus.WAITING);

        // Outbox 삽입 — JPA 변경과 동일 트랜잭션 (JpaTransactionManager가 JDBC 커넥션 공유)
        VideoTranscodeRequestedEvent event = VideoTranscodeRequestedEvent.of(
                video.getContent().getId(),
                video.getId(),
                vf.getId(),
                vf.getOriginalUrl()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(event.eventId(), "ADMIN", vf.getId(), adminTopic, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("이벤트 직렬화 실패: " + event.eventId(), e);
        }

        return new AdminVideoUploadConfirmResponse(
                video.getContent().getId(),
                video.getId(),
                vf.getId(),
                vf.getOriginalUrl(),
                vf.getTranscodeStatus().name()
        );
    }

    private void validate(AdminVideoUploadConfirmRequest req) {
        if (req == null || req.contentId() == null) {
            throw new IllegalArgumentException("contentId는 필수입니다.");
        }
        if (req.videoId() == null) {
            throw new IllegalArgumentException("videoId는 필수입니다.");
        }
        if (!StringUtils.hasText(req.objectKey())) {
            throw new IllegalArgumentException("objectKey는 필수입니다.");
        }
    }

    private ObjectStorageService.ObjectStat safeStat(String objectKey) {
        try {
            return objectStorageService.statObject(objectKey);
        } catch (ObjectNotFoundException e) {
            throw new UploadNotCompletedException();
        } catch (StorageException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("NoSuchKey") || msg.contains("Object does not exist"))) {
                throw new UploadNotCompletedException();
            }
            throw e;
        }
    }
}
