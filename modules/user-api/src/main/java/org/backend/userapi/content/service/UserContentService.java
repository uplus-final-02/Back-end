package org.backend.userapi.content.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

import org.backend.userapi.content.dto.UserContentDeleteResponse;
import org.backend.userapi.content.dto.UserContentPlayResponse;
import org.backend.userapi.content.dto.UserContentUpdateRequest;
import org.backend.userapi.content.dto.UserContentUpdateResponse;
import org.backend.userapi.content.dto.UserThumbnailUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import common.enums.ContentStatus;
import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import core.security.principal.JwtPrincipal;
import core.storage.ObjectStorageService;
import core.storage.service.HlsUrlProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserContentService {

    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;
    private final ObjectStorageService objectStorageService;
    private final HlsUrlProvider hlsUrlProvider;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");



    @Transactional
    public UserContentUpdateResponse updateMetadata(JwtPrincipal principal, Long userContentId, UserContentUpdateRequest req) {
        requireLogin(principal);

        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException("USER_CONTENT_NOT_FOUND: " + userContentId));

        if (!uc.getUploaderId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN: not owner");
        }

        if (uc.getContentStatus() == ContentStatus.DELETED) {
            throw new IllegalStateException("USER_CONTENT_ALREADY_DELETED");
        }

        if (req != null) {
            if (StringUtils.hasText(req.title())) {
                uc.updateTitle(req.title());
            }
            if (req.description() != null) {
                uc.updateDescription(req.description());
            }

            if (req.contentStatus() != null) {
                if (req.contentStatus() == ContentStatus.DELETED) {
                    throw new IllegalArgumentException("INVALID_CONTENT_STATUS: use DELETE API");
                }
                uc.updateContentStatus(req.contentStatus());
            }
        }

        UserVideoFile uvf = userVideoFileRepository.findByContent_Id(uc.getId())
                .orElseThrow(() -> new IllegalStateException("USER_VIDEO_FILE_NOT_FOUND: contentId=" + uc.getId()));

        if (req != null && req.videoStatus() != null) {
            if (req.videoStatus() == VideoStatus.PUBLIC && uvf.getTranscodeStatus() != common.enums.TranscodeStatus.DONE) {
                throw new IllegalStateException("VIDEO_NOT_READY: transcodeStatus=" + uvf.getTranscodeStatus());
            }
            uvf.updateVideoStatus(req.videoStatus());

            if (req.videoStatus() == VideoStatus.PUBLIC) {
                uc.updateContentStatus(ContentStatus.ACTIVE);
            } else if (req.videoStatus() == VideoStatus.PRIVATE) {
                uc.updateContentStatus(ContentStatus.HIDDEN);
            }
        }

        return new UserContentUpdateResponse(
                uc.getId(),
                uc.getContentStatus(),
                uvf.getVideoStatus()
        );
    }

    @Transactional
    public UserContentDeleteResponse delete(JwtPrincipal principal, Long userContentId) {
        requireLogin(principal);

        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException("USER_CONTENT_NOT_FOUND: " + userContentId));

        if (!uc.getUploaderId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN: not owner");
        }

        if (uc.getContentStatus() == ContentStatus.DELETED) {
            return UserContentDeleteResponse.of(uc.getId(), uc.getContentStatus());
        }

        uc.markDeleted();

        userVideoFileRepository.findByContent_Id(uc.getId())
                .ifPresent(vf -> vf.updateVideoStatus(VideoStatus.PRIVATE));

        return UserContentDeleteResponse.of(uc.getId(), uc.getContentStatus());
    }
    
    @Transactional
    public UserThumbnailUploadResponse uploadThumbnail(
            JwtPrincipal principal, Long userContentId, MultipartFile file) {
        requireLogin(principal);

        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "USER_CONTENT_NOT_FOUND: " + userContentId));

        if (!uc.getUploaderId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN: not owner");
        }

        validateFile(file);

        String extension = resolveExtension(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType(), extension);

        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uuid = UUID.randomUUID().toString();
        String objectPath = "images/thumbnails/user/%s/%d/%s%s"
                .formatted(date, userContentId, uuid, extension);

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("user-thumbnail-", extension);
            file.transferTo(tempFile.toFile());

            objectStorageService.uploadFromFile(objectPath, tempFile, contentType);
            String publicUrl = objectStorageService.buildPublicUrl(objectPath);

            uc.updateThumbnailUrl(publicUrl);

            return new UserThumbnailUploadResponse(uc.getId(), publicUrl);

        } catch (IOException e) {
            throw new IllegalStateException("썸네일 파일 처리 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            throw new IllegalStateException("썸네일 업로드에 실패했습니다.", e);
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 썸네일 파일이 비어 있습니다.");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("png, jpg, jpeg, webp 이미지 파일만 업로드 가능합니다.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
    }

    private String resolveExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new IllegalArgumentException("파일 확장자를 확인할 수 없습니다.");
        }
        return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
    }

    private String resolveContentType(String contentType, String extension) {
        if (contentType != null && contentType.startsWith("image/")) {
            return contentType;
        }
        return switch (extension) {
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".webp" -> "image/webp";
            default -> throw new IllegalArgumentException("지원하지 않는 썸네일 형식입니다.");
        };
    }

    @Transactional(readOnly = true)
    public UserContentPlayResponse play(JwtPrincipal principal, Long userContentId) {
        requireLogin(principal);

        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "USER_CONTENT_NOT_FOUND: " + userContentId));

        if (uc.getContentStatus() != ContentStatus.ACTIVE) {
            throw new IllegalStateException("CONTENT_NOT_AVAILABLE");
        }

        UserVideoFile uvf = userVideoFileRepository.findByContent_Id(userContentId)
                .orElseThrow(() -> new IllegalStateException(
                        "USER_VIDEO_FILE_NOT_FOUND: contentId=" + userContentId));

        validatePlayable(uvf);

        String hlsUrl = hlsUrlProvider.getHlsUrl(uvf.getId());

        return new UserContentPlayResponse(
                uc.getId(),
                uc.getTitle(),
                uc.getThumbnailUrl(),
                hlsUrl,
                uvf.getDurationSec()
        );
    }

    /**
     * 실제 재생 가능한 상태인지 검증.
     * PUBLIC + DONE + hlsUrl 세 조건이 모두 충족되어야 HLS URL을 발급한다.
     */
    private void validatePlayable(UserVideoFile uvf) {
        if (uvf.getVideoStatus() != VideoStatus.PUBLIC) {
            throw new IllegalStateException("VIDEO_NOT_PUBLIC");
        }
        if (uvf.getTranscodeStatus() != TranscodeStatus.DONE) {
            throw new IllegalStateException("VIDEO_NOT_READY: transcodeStatus=" + uvf.getTranscodeStatus());
        }
        if (uvf.getHlsUrl() == null || uvf.getHlsUrl().isBlank()) {
            throw new IllegalStateException("HLS_URL_NOT_READY");
        }
    }

    private void requireLogin(JwtPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
    }
}