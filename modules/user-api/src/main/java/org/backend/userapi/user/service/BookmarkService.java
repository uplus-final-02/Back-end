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

        bookmarkRepository.save(Bookmark.builder()
                .userId(userId)
                .contentId(contentId)
                .build());

        content.updateBookmarkCount(content.getBookmarkCount() + 1);
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
        
        contentRepository.findById(contentId).ifPresent(content ->{
            if(content.getBookmarkCount() > 0) {
                content.updateBookmarkCount(content.getBookmarkCount() - 1);
            }
        });
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
            return new BookmarkItemResponse(
                b.getId(),
                b.getContentId(),
                content != null ? content.getTitle() : "알 수 없는 콘텐츠",
                content != null ? content.getThumbnailUrl() : null,
                content != null ? content.getType().name() : "UNKNOWN",
                "전체",
                b.getCreatedAt().toString(),
                content == null || content.getStatus() == ContentStatus.DELETED
            );
        }).toList();

        String nextCursor = hasNext ? String.valueOf(bookmarks.get(bookmarks.size() - 1).getId()) : null;

        return new BookmarkListResponse(items, nextCursor, hasNext, bookmarkRepository.countByUserId(userId));
    }

    /**
     * AE2-61: 홈 화면 - 최근 찜한 콘텐츠 목록 api
     */
    @Transactional(readOnly = true)
    public List<DefaultContentResponse> getRecentBookmarkList(Long userId) {
        List<Bookmark> bookmarks = bookmarkRepository.findRecentBookmarks(
            userId,
            PageRequest.of(0, 5)
        );

        if (bookmarks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> contentIds = bookmarks.stream()
                                         .map(Bookmark::getContentId)
                                         .collect(Collectors.toList());

        // Fetch contents
        List<Content> contents = contentRepository.findAllById(contentIds);
        Map<Long, Content> contentMap = contents.stream()
                                                .collect(Collectors.toMap(Content::getId, c -> c));

        // 북마크 순서대로 Content 정렬
        List<Content> sortedContents = bookmarks.stream()
                .map(b -> contentMap.get(b.getContentId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // ContentService의 변환 로직 사용 (닉네임 조회 포함)
        return contentService.convertToDefaultContentResponses(sortedContents);
    }
    
    /**
     * [신규] 찜 목록 연속 재생 (유튜브 재생목록 스타일)
     * - 시리즈는 전체 에피소드로 전개(Flatten), 진행률 동적 계산 적용
     */
    @Transactional(readOnly = true)
    public BookmarkPlaylistResponse getBookmarkPlaylist(Long userId) {
        
        // 1. 유저의 찜 목록을 '오래된 순(ASC)'으로 가져옴
        List<Bookmark> bookmarks = bookmarkRepository.findPlaylistByUserIdDesc(userId, PageRequest.of(0, 50));
        
        if (bookmarks.isEmpty()) {
            return new BookmarkPlaylistResponse(Collections.emptyList(), 0);
        }

        List<Long> contentIds = bookmarks.stream().map(Bookmark::getContentId).toList();

        // 2. 찜한 콘텐츠들에 속한 '모든 비디오'를 조회 (Bulk 조회)
        List<Video> allVideos = videoRepository.findAllByContentIdInOrderByEpisodeNoAsc(contentIds);
        List<Long> videoIds = allVideos.stream().map(Video::getId).toList();

        // 3. 해당 비디오들에 대한 유저의 '시청 기록' 조회 (Bulk 조회)
        List<WatchHistory> histories = watchHistoryRepository.findByUserIdAndVideoIdIn(userId, videoIds);
        
        // 4. Map으로 변환하여 매핑 속도를 O(1)로 최적화
        Map<Long, WatchHistory> historyMap = histories.stream()
                .collect(Collectors.toMap(h -> h.getVideo().getId(), h -> h));

        // 5. 데이터 조립 (Flattening - 시리즈를 낱개 비디오로 펼치기)
        List<BookmarkPlaylistResponse.PlaylistItem> playlist = new ArrayList<>();
        int order = 1;

        for (Bookmark bookmark : bookmarks) {
            List<Video> seriesVideos = allVideos.stream()
                    .filter(v -> v.getContent().getId().equals(bookmark.getContentId()))
                    .toList();

            for (Video video : seriesVideos) {
                if (video.getContent().getStatus() == ContentStatus.DELETED) {
                    continue; 
                }

                WatchHistory history = historyMap.get(video.getId());
                
                // 에피소드 제목 조립 ("1화 - 부제")
                String episodeTitle = null;
                if (video.getTitle() != null && !video.getTitle().isBlank()) {
                    episodeTitle = video.getEpisodeNo() + "화 - " + video.getTitle();
                } else {
                    episodeTitle = video.getEpisodeNo() + "화";
                }

                // 썸네일 로직 (회차 썸네일 우선, 없으면 콘텐츠 대표 썸네일)
                String thumbUrl = video.getThumbnailUrl();
                if (thumbUrl == null || thumbUrl.isBlank()) {
                    thumbUrl = video.getContent().getThumbnailUrl();
                }

                // VideoFile 엔티티에서 영상 정보 추출
                String videoUrl = "";
                int duration = 0;
                if (video.getVideoFile() != null) {
                    videoUrl = video.getVideoFile().getHlsUrl() != null ? 
                               video.getVideoFile().getHlsUrl() : video.getVideoFile().getOriginalUrl();
                    duration = video.getVideoFile().getDurationSec();
                }

                // 시청 위치 및 진행률(%) 직접 계산
                int lastPosition = (history != null && history.getLastPositionSec() != null) 
                                   ? history.getLastPositionSec() : 0;
                
                int progressPercent = 0;
                if (duration > 0) {
                    progressPercent = (int) Math.round((double) lastPosition / duration * 100);
                }

                // 단건 판별 로직
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