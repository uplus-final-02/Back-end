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
import content.entity.Content;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.entity.UserWatchHistory;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import content.repository.UserWatchHistoryRepository;
import core.security.principal.JwtPrincipal;
import core.storage.service.HlsUrlProvider;
import core.storage.ObjectStorageService;
import core.storage.service.HlsUrlProvider;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.content.dto.UserContentDeleteResponse;
import org.backend.userapi.content.dto.UserContentUpdateRequest;
import org.backend.userapi.content.dto.UserContentUpdateResponse;
import org.backend.userapi.video.dto.VideoPlayDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import user.entity.User;
import user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserContentService {

    private final UserRepository userRepository;
    private final UserWatchHistoryRepository userWatchHistoryRepository;
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

    @Transactional
    public VideoPlayDto getPlayInfo(Long userContentId, JwtPrincipal jwtPrincipal) {

        // 1. 유저 콘텐츠 기본 정보 조회
        UserContent userContent = userContentRepository.findById(userContentId)
                                                       .orElseThrow(() -> new IllegalArgumentException("유저 콘텐츠를 찾을 수 없습니다."));
        UserVideoFile userVideoFile = userVideoFileRepository.findByContent_Id(userContentId).orElse(null);


        // [인가] 회원(jwtPrincipal 존재)이라면 누구나 열람 가능하므로 특별한 권한 체크 생략
        // (단, 비공개 처리되거나 삭제된 상태인지 검증하는 로직 정도만 추가)
        if (userContent.getContentStatus() == ContentStatus.DELETED) {
            throw new IllegalStateException("삭제된 콘텐츠입니다.");
        }
        if (userContent.getContentStatus() != ContentStatus.ACTIVE) {
            throw new IllegalStateException("서비스 불가능한 콘텐츠입니다.");
        }
        if (userVideoFile.getVideoStatus() != VideoStatus.PUBLIC) {
            throw new IllegalStateException("비공개 비디오입니다.");
        }
        if (userVideoFile.getTranscodeStatus() != TranscodeStatus.DONE) {
            throw new IllegalStateException("아직 처리 중인 비디오입니다.");
        }

        Long userId = jwtPrincipal.getUserId();

        // 2. HLS URL 및 영상 길이 세팅
        // (프로젝트 구조에 따라 UserContent 엔티티 자체에 있거나, 연관된 UserContentFile 등에서 가져옴)
        String hlsUrl = null;
        if (userVideoFile != null && userVideoFile.getHlsUrl() != null) {
            String[] parts = userVideoFile.getHlsUrl().split("/");

            try {
                Long extractedId = Long.parseLong(parts[1]);
                hlsUrl = hlsUrlProvider.getHlsUserUrl(extractedId);
            } catch (Exception e) {
                // 파싱 실패 등 예외 발생 시 안전하게 기존 id로 폴백(Fallback)
                hlsUrl = hlsUrlProvider.getHlsUserUrl(userVideoFile.getId());
            }
        }
        long durationSec = (userVideoFile != null) ? userVideoFile.getDurationSec() : 0L;

        // 3. 사용자 이어보기(History) 정보 처리
        // 유저 콘텐츠(보통 숏폼 형태)는 이어보기 기능이 필요 없을 확률이 높습니다.
        // 항상 처음부터 재생되도록 기본값 0 세팅. (만약 이력 관리가 필요하다면 기존 로직처럼 Repository 조회 추가)
        VideoPlayDto.PlaybackState playbackState = VideoPlayDto.PlaybackState.builder()
                                                                             .startPositionSec(0L)
                                                                             .lastUpdated(null)
                                                                             .build();

        // 유저콘텐츠 시청기록 저장
        Optional<UserWatchHistory> history = userWatchHistoryRepository.findByUserIdAndContentId(userId, userContentId);
        if (history.isPresent()) {
            history.get().updateLastWatchedAt(LocalDateTime.now());
        } else {
            UserWatchHistory newHistory = UserWatchHistory.builder()
                                                          .userId(userId)
                                                          .userContent(userContent)
                                                          .lastWatchedAt(LocalDateTime.now())
                                                          .build();

            userWatchHistoryRepository.save(newHistory);
        }

        // 4. 업로더 정보 조회
        String uploaderNickname = userRepository.findById(userContent.getUploaderId())
                                                .map(User::getNickname)
                                                .orElse("Unknown");

        // 5. 북마크(또는 좋아요) 여부 조회 (해당 Repository가 있다면 주입받아 사용)
        //boolean isBookmarked = userContentBookmarkRepository.existsByUserIdAndUserContentId(userId, userContentId);

        // 6. 태그 리스트 조회
        //List<String> tags = userContentTagRepository.findTagNamesByUserContentId(userContentId).stream().toList();

        // 🌟 부모 콘텐츠 정보 추출 (Lazy Loading 초기화)
        VideoPlayDto.ParentContentInfo parentInfo = null;
        if (userContent.getParentContent() != null) {
            Content parent = userContent.getParentContent();
            parentInfo = VideoPlayDto.ParentContentInfo.builder()
                                                       .contentId(parent.getId())
                                                       .title(parent.getTitle()) // 여기서 부모 콘텐츠 SELECT 쿼리 발생
                                                       .thumbnailUrl(parent.getThumbnailUrl())
                                                       .build();
        }

        // 7. 최종 DTO 조립 및 반환 (VideoPlayDto 껍데기를 그대로 재사용)
        return VideoPlayDto.builder()
                           .videoId(userContent.getId())             // DTO의 videoId 자리에 userContentId 삽입
                           .title(userContent.getTitle())
                           .description(userContent.getDescription())
                           .thumbnailUrl(userContent.getThumbnailUrl())
                           .viewCount(userContent.getTotalViewCount())
                           .durationSec(durationSec)
                           .createdAt(userContent.getCreatedAt())
                           .status(null)
                           //.tags(tags)
                           .uploaderType("USER")                     // 유저가 올렸으므로 고정
                           .uploaderNickname(uploaderNickname)
                           .url(hlsUrl)
                           //.IsBookmarked(isBookmarked)
                           .playbackState(playbackState)
                           .contentStatus(userContent.getContentStatus())
                           .parentContent(parentInfo)
                           .context(VideoPlayDto.Context.builder()
                                                        .isSeries(false)                  // 유저 콘텐츠는 단건
                                                        .contentsId(userContent.getId())
                                                        .episodeNumber(1)
                                                        .nextVideoId(null)                // 이전/다음 에피소드 없음
                                                        .prevVideoId(null)
                                                        .build())
                           .build();
    }
}