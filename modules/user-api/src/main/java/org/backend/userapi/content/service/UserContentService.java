package org.backend.userapi.content.service;

import common.enums.ContentStatus;
import common.enums.VideoStatus;
import content.entity.Content;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import core.security.principal.JwtPrincipal;
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

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserContentService {

    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;
    private final UserRepository userRepository;
    private final HlsUrlProvider hlsUrlProvider;

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

    private void requireLogin(JwtPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }
    }

    @Transactional(readOnly = true)
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
                           .thumbnailUrl(null)
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