package org.backend.userapi.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.backend.userapi.user.dto.response.BookmarkPlaylistResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import common.enums.ContentStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.WatchHistory;
import content.repository.VideoRepository;
import content.repository.WatchHistoryRepository;
import interaction.entity.Bookmark;
import interaction.repository.BookmarkRepository;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @InjectMocks
    private BookmarkService bookmarkService;

    @Mock
    private BookmarkRepository bookmarkRepository;
    @Mock
    private VideoRepository videoRepository;
    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Test
    @DisplayName("찜 목록 연속 재생 로직 검증: 시리즈 전개, 단건 처리, 진행률 계산이 완벽한가?")
    void testGetBookmarkPlaylist() {
        // =========================================================
        // 1. [Given] 가짜 데이터(Mock) 세팅
        // =========================================================
        Long userId = 1L;

        // 찜 2개: 1번은 시리즈(슬의생), 2번은 단건(요리 브이로그)
        Bookmark bookmark1 = mock(Bookmark.class);
        when(bookmark1.getContentId()).thenReturn(100L);
        
        Bookmark bookmark2 = mock(Bookmark.class);
        when(bookmark2.getContentId()).thenReturn(200L);

        // 콘텐츠 세팅 (Deep Stub 활용)
        Content seriesContent = mock(Content.class);
        when(seriesContent.getId()).thenReturn(100L);
        when(seriesContent.getStatus()).thenReturn(ContentStatus.ACTIVE);
        when(seriesContent.getTitle()).thenReturn("슬기로운 의사생활");
        when(seriesContent.getThumbnailUrl()).thenReturn("series_thumb.jpg");

        Content singleContent = mock(Content.class);
        when(singleContent.getId()).thenReturn(200L);
        when(singleContent.getStatus()).thenReturn(ContentStatus.ACTIVE);
        when(singleContent.getTitle()).thenReturn("요리 브이로그");
        when(singleContent.getThumbnailUrl()).thenReturn("single_thumb.jpg");

        // 비디오 3개 세팅 (시리즈 2개, 단건 1개)
        Video video1 = mock(Video.class, RETURNS_DEEP_STUBS);
        when(video1.getId()).thenReturn(10L);
        when(video1.getContent()).thenReturn(seriesContent);
        when(video1.getEpisodeNo()).thenReturn(1);
        when(video1.getTitle()).thenReturn("시작");
        when(video1.getVideoFile().getDurationSec()).thenReturn(1000); // 1000초짜리 영상
        when(video1.getVideoFile().getHlsUrl()).thenReturn("v1.m3u8");

        Video video2 = mock(Video.class, RETURNS_DEEP_STUBS);
        when(video2.getId()).thenReturn(11L);
        when(video2.getContent()).thenReturn(seriesContent);
        when(video2.getEpisodeNo()).thenReturn(2);
        when(video2.getTitle()).thenReturn("위기");
        when(video2.getVideoFile().getDurationSec()).thenReturn(1000); // 1000초짜리 영상
        when(video2.getVideoFile().getHlsUrl()).thenReturn("v2.m3u8");

        Video video3 = mock(Video.class, RETURNS_DEEP_STUBS);
        when(video3.getId()).thenReturn(20L);
        when(video3.getContent()).thenReturn(singleContent);
        when(video3.getEpisodeNo()).thenReturn(1);
        when(video3.getTitle()).thenReturn(null); // 단건은 에피소드 제목 없음
        when(video3.getVideoFile().getDurationSec()).thenReturn(600); // 600초짜리 영상
        when(video3.getVideoFile().getHlsUrl()).thenReturn("v3.m3u8");

        // 시청 이력 세팅 (video1은 절반 봄, video3는 다 봄, video2는 안 봄)
        WatchHistory history1 = mock(WatchHistory.class, RETURNS_DEEP_STUBS);
        when(history1.getVideo().getId()).thenReturn(10L);
        when(history1.getLastPositionSec()).thenReturn(500); // 1000초 중 500초 시청 (50%)

        WatchHistory history3 = mock(WatchHistory.class, RETURNS_DEEP_STUBS);
        when(history3.getVideo().getId()).thenReturn(20L);
        when(history3.getLastPositionSec()).thenReturn(600); // 600초 중 600초 시청 (100%)

        // Repository 동작 정의 (MOCKING)
        when(bookmarkRepository.findPlaylistByUserIdAsc(eq(userId), any(Pageable.class)))
                .thenReturn(List.of(bookmark1, bookmark2)); // 찜 2개 반환
        when(videoRepository.findAllByContentIdInOrderByEpisodeNoAsc(List.of(100L, 200L)))
                .thenReturn(List.of(video1, video2, video3)); // 비디오 3개 반환
        when(watchHistoryRepository.findByUserIdAndVideoIdIn(eq(userId), eq(List.of(10L, 11L, 20L))))
                .thenReturn(List.of(history1, history3)); // 이력 2개 반환

        // =========================================================
        // 2. [When] 테스트 대상 로직 실행!
        // =========================================================
        BookmarkPlaylistResponse response = bookmarkService.getBookmarkPlaylist(userId);
        List<BookmarkPlaylistResponse.PlaylistItem> playlist = response.getPlaylist();

        // =========================================================
        // 3. [Then] 결과 검증 (Assert)
        // =========================================================
        System.out.println("✅ 총 찜 개수(2개)가 시리즈 전개를 통해 비디오(3개)로 변환되었는가?");
        assertEquals(3, response.getTotalCount());
        assertEquals(3, playlist.size());

        System.out.println("✅ 첫 번째 영상 (슬의생 1화) 로직 검증");
        BookmarkPlaylistResponse.PlaylistItem item1 = playlist.get(0);
        assertEquals("1화 - 시작", item1.getEpisodeTitle());
        assertEquals(500, item1.getLastPosition());
        assertEquals(50, item1.getProgressPercent()); // 500 / 1000 * 100 = 50%
        assertNotNull(item1.getEpisodeId()); // 시리즈물이므로 episodeId 존재해야 함

        System.out.println("✅ 두 번째 영상 (슬의생 2화) 로직 검증 (시청 이력 없음)");
        BookmarkPlaylistResponse.PlaylistItem item2 = playlist.get(1);
        assertEquals("2화 - 위기", item2.getEpisodeTitle());
        assertEquals(0, item2.getLastPosition());
        assertEquals(0, item2.getProgressPercent());

        System.out.println("✅ 세 번째 영상 (단건: 요리 브이로그) 단건 널(Null) 처리 검증");
        BookmarkPlaylistResponse.PlaylistItem item3 = playlist.get(2);
        assertNull(item3.getEpisodeTitle()); // 단건이므로 에피소드 제목 null
        assertNull(item3.getEpisodeId());    // 단건이므로 에피소드 ID null
        assertEquals(100, item3.getProgressPercent()); // 600 / 600 * 100 = 100%
        
        System.out.println("🎉 모든 테스트가 완벽하게 통과했습니다!");
    }
}