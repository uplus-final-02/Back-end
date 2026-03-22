package org.backend.userapi.user.service;

import java.util.*;
import java.util.stream.Collectors;

import org.backend.userapi.common.exception.BookmarkNotFoundException;
import org.backend.userapi.content.dto.DefaultContentResponse;
import org.backend.userapi.content.service.ContentService;
import org.backend.userapi.user.dto.response.BookmarkListResponse;
import org.backend.userapi.user.dto.response.BookmarkListResponse.BookmarkItemResponse;
import org.backend.userapi.user.dto.response.BookmarkPlaylistResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.enums.ContentStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.WatchHistory;
import content.repository.ContentRepository;
import content.repository.VideoRepository;
import content.repository.WatchHistoryRepository;
import interaction.entity.Bookmark;
import interaction.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final ContentService contentService;

    /**
     * AE2-44: 찜하기 등록
     */
    @Transactional
    public void addBookmark(Long userId, Long contentId) {
        if (bookmarkRepository.existsByUserIdAndContentId(userId, contentId)) {
            return;
        }

        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }

        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다."));

        if (content.getStatus() != ContentStatus.ACTIVE) {
            throw new IllegalArgumentException("콘텐츠를 찾을 수 없습니다."); 
        }

        bookmarkRepository.save(Bookmark.builder()
                .userId(userId)
                .contentId(contentId)
                .build());

        // [수정] read-modify-write 대신 DB 원자 UPDATE 사용 → 동시성 안전
        contentRepository.incrementBookmarkCount(contentId);
    }

    /**
     * AE2-214: 찜하기 삭제
     */
    @Transactional
    public void removeBookmark(Long userId, Long contentId) {
        if (!bookmarkRepository.existsByUserIdAndContentId(userId, contentId)) {
            throw new BookmarkNotFoundException("찜하지 않은 콘텐츠입니다.");
        }
        bookmarkRepository.deleteByUserIdAndContentId(userId, contentId);

        // [수정] read-modify-write 대신 DB 원자 UPDATE 사용 → 동시성 안전
        // (ContentRepository.decrementBookmarkCount 내부에서 bookmarkCount > 0 조건으로 음수 방지)
        contentRepository.decrementBookmarkCount(contentId);
    }

    /**
     * AE2-43: 찜 목록 조회 (Cursor 기반 페이징)
     */
    @Transactional(readOnly = true)
    public BookmarkListResponse getMyBookmarks(Long userId, Long cursorId, int size) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserIdWithCursor(
                userId, cursorId, PageRequest.of(0, size + 1));

        boolean hasNext = bookmarks.size() > size;
        if (hasNext) {
            bookmarks = bookmarks.subList(0, size);
        }

        List<Long> contentIds = bookmarks.stream()
                .map(Bookmark::getContentId)
                .toList();

        Map<Long, Content> contentMap = contentRepository.findAllById(contentIds).stream()
                .collect(Collectors.toMap(Content::getId, c -> c));

        List<BookmarkItemResponse> items = bookmarks.stream().map(b -> {
            Content content = contentMap.get(b.getContentId());
            // [수정] DELETED 뿐 아니라 ACTIVE가 아닌 모든 상태(HIDDEN 포함)를 재생 불가로 표시
            boolean isUnavailable = content == null || content.getStatus() != ContentStatus.ACTIVE;
            return new BookmarkItemResponse(
                b.getId(),
                b.getContentId(),
                content != null ? content.getTitle() : "알 수 없는 콘텐츠",
                content != null ? content.getThumbnailUrl() : null,
                content != null ? content.getType().name() : "UNKNOWN",
                "전체",
                b.getCreatedAt().toString(),
                isUnavailable
            );
        }).toList();

        String nextCursor = hasNext ? String.valueOf(bookmarks.get(bookmarks.size() - 1).getId()) : null;

        return new BookmarkListResponse(items, nextCursor, hasNext, bookmarkRepository.countByUserId(userId));
    }

    /**
     * AE2-61: 홈 화면 - 최근 찜한 콘텐츠 목록
     */
    @Transactional(readOnly = true)
    public List<DefaultContentResponse> getRecentBookmarkList(Long userId) {
        List<Content> contents = bookmarkRepository.findRecentBookmarkedContents(
            userId,
            PageRequest.of(0, 5)
        );
        return contentService.convertToDefaultContentResponses(contents);
    }

    /**
     * 찜 목록 연속 재생 (플레이리스트)
     */
    @Transactional(readOnly = true)
    public BookmarkPlaylistResponse getBookmarkPlaylist(Long userId) {

        List<Bookmark> bookmarks = bookmarkRepository.findPlaylistByUserIdAsc(userId, PageRequest.of(0, 50));

        if (bookmarks.isEmpty()) {
            return new BookmarkPlaylistResponse(Collections.emptyList(), 0);
        }

        List<Long> contentIds = bookmarks.stream().map(Bookmark::getContentId).toList();

        List<Video> allVideos = videoRepository.findAllByContentIdInOrderByEpisodeNoAsc(contentIds);
        List<Long> videoIds = allVideos.stream().map(Video::getId).toList();

        List<WatchHistory> histories = watchHistoryRepository.findByUserIdAndVideoIdIn(userId, videoIds);

        Map<Long, WatchHistory> historyMap = histories.stream()
                .collect(Collectors.toMap(h -> h.getVideo().getId(), h -> h));

        List<BookmarkPlaylistResponse.PlaylistItem> playlist = new ArrayList<>();
        int order = 1;

        for (Bookmark bookmark : bookmarks) {
            List<Video> seriesVideos = allVideos.stream()
                    .filter(v -> v.getContent().getId().equals(bookmark.getContentId()))
                    .toList();

            for (Video video : seriesVideos) {
                // [수정] ACTIVE가 아닌 모든 상태(DELETED, HIDDEN) 스킵
                if (video.getContent().getStatus() != ContentStatus.ACTIVE) {
                    continue;
                }

                WatchHistory history = historyMap.get(video.getId());

                String episodeTitle = null;
                if (video.getTitle() != null && !video.getTitle().isBlank()) {
                    episodeTitle = video.getEpisodeNo() + "화 - " + video.getTitle();
                } else {
                    episodeTitle = video.getEpisodeNo() + "화";
                }

                String thumbUrl = video.getThumbnailUrl();
                if (thumbUrl == null || thumbUrl.isBlank()) {
                    thumbUrl = video.getContent().getThumbnailUrl();
                }

                String videoUrl = "";
                int duration = 0;
                if (video.getVideoFile() != null) {
                    videoUrl = video.getVideoFile().getHlsUrl() != null ?
                               video.getVideoFile().getHlsUrl() : video.getVideoFile().getOriginalUrl();
                    duration = video.getVideoFile().getDurationSec();
                }

                int lastPosition = (history != null && history.getLastPositionSec() != null)
                                   ? history.getLastPositionSec() : 0;

                int progressPercent = 0;
                if (duration > 0) {
                    progressPercent = (int) Math.round((double) lastPosition / duration * 100);
                }

                boolean isSingleVideo = (video.getTitle() == null && video.getEpisodeNo() <= 1);

                playlist.add(new BookmarkPlaylistResponse.PlaylistItem(
                    order++,
                    video.getContent().getId(),
                    isSingleVideo ? null : video.getId(),
                    video.getContent().getTitle(),
                    isSingleVideo ? null : episodeTitle,
                    thumbUrl,
                    videoUrl,
                    duration,
                    lastPosition,
                    progressPercent
                ));
            }
        }

        return new BookmarkPlaylistResponse(playlist, playlist.size());
    }
}