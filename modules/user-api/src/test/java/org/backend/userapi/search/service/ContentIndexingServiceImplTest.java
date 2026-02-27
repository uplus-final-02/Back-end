package org.backend.userapi.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.backend.userapi.recommendation.service.TagVectorService;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.repository.ContentSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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

import common.entity.Tag;
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import content.entity.Content;
import content.entity.ContentTag;
import content.repository.ContentRepository;
import user.repository.UserPreferredTagRepository;

@ExtendWith(MockitoExtension.class)
class ContentIndexingServiceImplTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentSearchRepository contentSearchRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private TagVectorService tagVectorService;

    @Mock
    private UserPreferredTagRepository userPreferredTagRepository;

    @InjectMocks
    private ContentIndexingServiceImpl contentIndexingService;

    // 💡 [수정] ArgumentCaptor 제네릭 에러를 원천 차단하기 위해 필드로 선언
    @Captor
    private ArgumentCaptor<List<ContentDocument>> documentListCaptor;

    @Test
    @DisplayName("전체 인덱싱 시 findAllWithTags()를 호출하고 문서를 저장한다")
    void indexAllContents_savesAllMappedDocuments() {
        // Given
        Content content = sampleContent(10L, "테스트 제목");
        
        when(contentRepository.findAllWithTags()).thenReturn(List.of(content));
        when(tagVectorService.buildContentVector(any())).thenReturn(new float[100]);

        // When
        contentIndexingService.indexAllContents();

        // Then
        // 💡 [수정] 필드에 선언된 captor 사용
        verify(contentSearchRepository).saveAll(documentListCaptor.capture());
        List<ContentDocument> saved = documentListCaptor.getValue();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getContentId()).isEqualTo(10L);
        assertThat(saved.get(0).getTitle()).isEqualTo("테스트 제목");
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
    @DisplayName("검색어와 필터가 모두 공백이면 빈 페이지를 반환한다")
    void search_withAllBlank_returnsEmptyPage() {
        // Given
    	Pageable pageable = PageRequest.of(0, 20);

        // When
    	Page<ContentDocument> result = contentIndexingService.search("   ", null, null, null, null, pageable);
    	
        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    @DisplayName("유효한 검색어와 필터로 검색 시 NativeQuery를 실행한다")
    void search_withKeywordAndFilters_usesElasticsearchOperations() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        String keyword = "드라마";
        String tag = "의학"; 
        
        ContentDocument doc = ContentDocument.builder()
                .contentId(1L)
                .title("의학 드라마 제목")
                .build();

        SearchHit<ContentDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);
        when(hit.getHighlightField("title")).thenReturn(Collections.emptyList());
        when(hit.getHighlightField("description")).thenReturn(Collections.emptyList());

        SearchHits<ContentDocument> searchHits = mock(SearchHits.class);
        when(searchHits.getTotalHits()).thenReturn(1L);
        when(searchHits.stream()).thenReturn(List.of(hit).stream());

        when(elasticsearchOperations.search(any(NativeQuery.class), eq(ContentDocument.class)))
                .thenReturn(searchHits);

        // When
        Page<ContentDocument> result = contentIndexingService.search(keyword, null, null, tag, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        verify(elasticsearchOperations).search(any(NativeQuery.class), eq(ContentDocument.class));
    }

    @Test
    @DisplayName("인덱싱 상태 조회 시 초기 상태를 반환한다")
    void getIndexingStatus_returnsInitialStatus() {
        Map<String, Object> status = contentIndexingService.getIndexingStatus();

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

    private Content sampleContent(Long id, String title) {
        Tag tag = Tag.builder().name("테스트태그").build();

        Content targetContent = Content.builder()
                .type(ContentType.SERIES)
                .title(title)
                .description("설명")
                .thumbnailUrl("thumb.jpg")
                .status(ContentStatus.ACTIVE)
                .uploaderId(1L)
                .accessLevel(ContentAccessLevel.FREE)
                .build();
        
        ReflectionTestUtils.setField(targetContent, "id", id);

        ContentTag mockContentTag = mock(ContentTag.class); 
        when(mockContentTag.getTag()).thenReturn(tag);

        try {
            ReflectionTestUtils.setField(targetContent, "contentTags", new java.util.ArrayList<>(List.of(mockContentTag)));
        } catch (Exception e) {
            ReflectionTestUtils.setField(targetContent, "tags", new java.util.ArrayList<>(List.of(mockContentTag)));
        }

        return targetContent;
    }
}