package org.backend.userapi.search.service;

import common.entity.Tag;
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import content.entity.Content;
import content.repository.ContentRepository;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.repository.ContentSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentIndexingServiceImplTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentSearchRepository contentSearchRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private ContentIndexingServiceImpl contentIndexingService;

    @Test
    @DisplayName("전체 인덱싱 시 findAllWithTags()를 호출하고 문서를 저장한다")
    void indexAllContents_savesAllMappedDocuments() {
        // Given
        Content content = sampleContent(10L, "테스트 제목");
        
        // Repository가 반환할 Mock 데이터 설정
        when(contentRepository.findAllWithTags()).thenReturn(List.of(content));

        // When
        contentIndexingService.indexAllContents();

        // Then
        ArgumentCaptor<List<ContentDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(contentSearchRepository).saveAll(captor.capture());
        List<ContentDocument> saved = captor.getValue();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getContentId()).isEqualTo(10L);
        assertThat(saved.get(0).getTitle()).isEqualTo("테스트 제목");
        // sampleContent에서 주입한 태그가 잘 들어갔는지 확인
        assertThat(saved.get(0).getTags()).contains("테스트태그"); 
    }

    @Test
    @DisplayName("단건 인덱싱 시 콘텐츠가 없으면 예외를 던진다")
    void indexContent_whenContentMissing_throws() {
        when(contentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contentIndexingService.indexContent(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("콘텐츠 없음");
    }

    @Test
    @DisplayName("검색어가 공백이면 빈 페이지를 반환하고 ES를 호출하지 않는다")
    void search_withBlankKeyword_returnsEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);

        // When
        Page<ContentDocument> result = contentIndexingService.search("   ", pageable);

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(elasticsearchOperations); // ES 호출 안 함 확인
        verifyNoInteractions(contentSearchRepository); // Repository 호출 안 함 확인
    }

    @Test
    @DisplayName("유효한 검색어 검색 시 NativeQuery를 실행하고 하이라이팅을 적용한다")
    void search_withKeyword_usesElasticsearchOperations() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        String keyword = "드라마";
        
        ContentDocument doc = ContentDocument.builder()
                .contentId(1L)
                .title("드라마 제목")
                .description("재미있는 드라마")
                .build();

        // SearchHit 모킹 (하이라이트 포함)
        SearchHit<ContentDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        
        // 하이라이트 필드 반환 모킹
        when(hit.getHighlightField("title")).thenReturn(List.of("<em>드라마</em> 제목"));
        when(hit.getHighlightField("description")).thenReturn(Collections.emptyList());

        // SearchHits 모킹
        SearchHits<ContentDocument> searchHits = mock(SearchHits.class);
        when(searchHits.getTotalHits()).thenReturn(1L);
        when(searchHits.stream()).thenReturn(List.of(hit).stream());

        // ES 동작 모킹
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(ContentDocument.class)))
                .thenReturn(searchHits);

        // When
        Page<ContentDocument> result = contentIndexingService.search(keyword, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        ContentDocument resultDoc = result.getContent().get(0);
        
        assertThat(resultDoc.getTitle()).isEqualTo("드라마 제목");
        // 하이라이트 적용 확인 (DTO 매핑 로직 검증)
        assertThat(resultDoc.getHighlightTitle()).isEqualTo("<em>드라마</em> 제목");
        
        verify(elasticsearchOperations).search(any(NativeQuery.class), eq(ContentDocument.class));
    }

    @Test
    @DisplayName("인덱싱 상태 조회 시 초기 상태를 반환한다")
    void getIndexingStatus_returnsInitialStatus() {
        // When
        Map<String, Object> status = contentIndexingService.getIndexingStatus();

        // Then
        assertThat(status).containsEntry("status", "IDLE");
        assertThat(status).doesNotContainKey("error");
    }

    @Test
    @DisplayName("콘텐츠 삭제 시 deleteById를 호출한다")
    void deleteContent_deletesById() {
        contentIndexingService.deleteContent(7L);
        verify(contentSearchRepository).deleteById(7L);
    }

    @Test
    @DisplayName("인덱싱된 콘텐츠 개수 조회 시 count()를 호출한다")
    void countIndexedContents_returnsRepositoryCount() {
        when(contentSearchRepository.count()).thenReturn(42L);
        assertThat(contentIndexingService.countIndexedContents()).isEqualTo(42L);
    }

    // 🚨 [핵심 수정] 중간 엔티티(ContentTag) 반영을 위한 헬퍼 메서드
    private Content sampleContent(Long id, String title) {
        // 1. 태그 생성
        Tag tag = Tag.builder().name("테스트태그").build();

        // 2. Content 생성 (빌더에서 tags 파라미터 제거됨)
        Content content = Content.builder()
                .type(ContentType.SERIES)
                .title(title)
                .description("설명")
                .thumbnailUrl("thumb.jpg")
                .status(ContentStatus.ACTIVE)
                .uploaderId(1L)
                .accessLevel(ContentAccessLevel.FREE)
                .build();
        
        // 3. ID 주입 (Reflection 사용)
        ReflectionTestUtils.setField(content, "id", id);

        // 4. [중요] 중간 엔티티(ContentTag) Mock 생성 및 주입
        // ContentTag의 생성자가 protected거나 복잡할 수 있으므로 Mock 객체 사용
        content.entity.ContentTag mockContentTag = mock(content.entity.ContentTag.class);
        when(mockContentTag.getTag()).thenReturn(tag);

        // 5. Content 내부의 contentTags 리스트에 Mock 객체 리스트 주입
        // 이렇게 하면 content.getTags() 호출 시 이 Mock 객체를 통해 태그를 가져옵니다.
        ReflectionTestUtils.setField(content, "contentTags", List.of(mockContentTag));

        return content;
    }
}