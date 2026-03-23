package org.backend.userapi.upload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import common.enums.ContentStatus;
import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import core.events.video.VideoTranscodeRequestedEvent;
import core.security.principal.JwtPrincipal;
import core.storage.ObjectNotFoundException;
import core.storage.ObjectStorageService;
import core.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.exception.ContentNotFoundException;
import org.backend.userapi.common.exception.ConflictException;
import org.backend.userapi.kafka.outbox.VideoTranscodeOutboxJdbcRepository;
import org.backend.userapi.upload.dto.UserUploadConfirmRequest;
import org.backend.userapi.upload.dto.UserUploadConfirmResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserUploadConfirmService {

    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;
    private final ObjectStorageService objectStorageService;

    private final VideoTranscodeOutboxJdbcRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.video-transcode-user-requested}")
    private String userTopic;

    @Transactional
    public UserUploadConfirmResponse confirm(JwtPrincipal principal, UserUploadConfirmRequest req) {
        if (principal == null || principal.getUserId() == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        validate(req);

        UserContent uc = userContentRepository.findById(req.userContentId())
                .orElseThrow(() -> new ContentNotFoundException("콘텐츠를 찾을 수 없습니다."));

        if (!uc.getUploaderId().equals(principal.getUserId())) {
            throw new AccessDeniedException("콘텐츠에 대한 접근 권한이 없습니다.");
        }

        var stat = safeStat(req.objectKey());
        if (stat.size() <= 0) {
            throw new ConflictException("파일이 아직 업로드되지 않았습니다. 업로드를 먼저 완료해 주세요.");
        }

        UserVideoFile uvf = userVideoFileRepository.findByContent_Id(uc.getId())
                .orElseThrow(() -> new ContentNotFoundException("영상 파일 정보를 찾을 수 없습니다."));

        uvf.updateOriginalKey(req.objectKey());
        uvf.updateTranscodeStatus(TranscodeStatus.WAITING);
        uvf.updateVideoStatus(VideoStatus.PRIVATE);
        uc.updateContentStatus(ContentStatus.HIDDEN);

        VideoTranscodeRequestedEvent event = VideoTranscodeRequestedEvent.ofUser(
                uc.getId(),
                uvf.getId(),
                uvf.getOriginalUrl()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.saveUser(event.eventId(), uvf.getId(), userTopic, payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 처리 중 오류가 발생했습니다.", e);
        }

        return new UserUploadConfirmResponse(
                uc.getId(),
                uvf.getId(),
                uvf.getOriginalUrl(),
                uvf.getTranscodeStatus().name()
        );
    }

    private void validate(UserUploadConfirmRequest req) {
        if (req == null || req.userContentId() == null) {
            throw new IllegalArgumentException("userContentId는 필수입니다.");
        }
        if (!StringUtils.hasText(req.objectKey())) {
            throw new IllegalArgumentException("objectKey는 필수입니다.");
        }
    }

    private ObjectStorageService.ObjectStat safeStat(String objectKey) {
        try {
            return objectStorageService.statObject(objectKey);
        } catch (ObjectNotFoundException e) {
            throw new ConflictException("파일이 아직 업로드되지 않았습니다. 업로드를 먼저 완료해 주세요.");
        } catch (StorageException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("NoSuchKey") || msg.contains("Object does not exist"))) {
                throw new ConflictException("파일이 아직 업로드되지 않았습니다. 업로드를 먼저 완료해 주세요.");
            }
            throw e;
        }
    }
}