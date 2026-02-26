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

import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.repository.ContentSearchRepository;
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

import common.entity.Tag;
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import content.entity.Content;
import content.entity.ContentTag;
import content.repository.ContentRepository;

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
    @DisplayName("검색어와 필터가 모두 공백이면 빈 페이지를 반환한다")
    void search_withAllBlank_returnsEmptyPage() {
        // Given
    	Pageable pageable = PageRequest.of(0, 20);

        // When - 모든 필터가 null 또는 공백인 경우
    	Page<ContentDocument> result = contentIndexingService.search("   ", null, null, null, pageable);
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
        String tag = "의학"; // 추가된 필터
        
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

        // [수정] 모든 파라미터를 받는 search 메서드 모킹
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(ContentDocument.class)))
                .thenReturn(searchHits);

        // When
        // [수정] 파라미터 순서: keyword, category, genre, tag, pageable
        Page<ContentDocument> result = contentIndexingService.search(keyword, null, null, tag, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        // [핵심] NativeQuery 내부의 BoolQuery에 필터가 잘 조립되었는지는 통합 테스트 영역이지만, 
        // 여기서는 서비스 메서드가 예외 없이 실행되는지 확인합니다.
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

        // 2. 변수명을 'targetContent'로 하여 패키지명(content)과의 충돌을 원천 차단
        Content targetContent = Content.builder()
                .type(ContentType.SERIES)
                .title(title)
                .description("설명")
                .thumbnailUrl("thumb.jpg")
                .status(ContentStatus.ACTIVE)
                .uploaderId(1L)
                .accessLevel(ContentAccessLevel.FREE)
                .build();
        
        // 3. ID 주입 (필드명이 'id'인지 확인 필요)
        ReflectionTestUtils.setField(targetContent, "id", id);

        // 4. ContentTag Mock 생성
        // 💡 상단에 import content.entity.ContentTag; 가 정확히 되어 있는지 확인하세요.
        ContentTag mockContentTag = mock(ContentTag.class); 
        when(mockContentTag.getTag()).thenReturn(tag);

        // 5. [매우 중요] 엔티티의 실제 필드명을 확인하세요!
        // 💡 Content 엔티티 안에 'private List<ContentTag> contentTags;' 필드가 실제로 있나요?
        // 💡 만약 필드명이 'tags'라면 아래 "contentTags"를 "tags"로 바꿔야 빌드(테스트)가 통과됩니다.
        try {
            ReflectionTestUtils.setField(targetContent, "contentTags", new java.util.ArrayList<>(List.of(mockContentTag)));
        } catch (Exception e) {
            // 필드명이 다를 경우를 대비한 방어 로직 (실제 프로젝트 필드명으로 수정 권장)
            ReflectionTestUtils.setField(targetContent, "tags", new java.util.ArrayList<>(List.of(mockContentTag)));
        }

        return targetContent;
    }
}