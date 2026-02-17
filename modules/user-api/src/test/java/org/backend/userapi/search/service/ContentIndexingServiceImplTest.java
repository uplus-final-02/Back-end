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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    // 🚨 [추가] 서비스에서 NativeQuery를 쓰기 위해 추가된 의존성
    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    private ContentIndexingServiceImpl contentIndexingService;

    @BeforeEach
    void setUp() {
        // 🚨 [수정] 생성자에 elasticsearchOperations 추가
        contentIndexingService = new ContentIndexingServiceImpl(contentRepository, contentSearchRepository, elasticsearchOperations);
    }

    @Test
    void indexAllContents_savesAllMappedDocuments() {
        // Given
        Content content = sampleContent(10L, "테스트 제목");
        when(contentRepository.findAll()).thenReturn(List.of(content));

        // When
        contentIndexingService.indexAllContents();

        // Then
        ArgumentCaptor<List<ContentDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(contentSearchRepository).saveAll(captor.capture());
        List<ContentDocument> saved = captor.getValue();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getContentId()).isEqualTo(10L);
        assertThat(saved.get(0).getTitle()).isEqualTo("테스트 제목");
        // 태그 매핑 확인
        assertThat(saved.get(0).getTags()).contains("테스트태그"); 
    }

    @Test
    void indexContent_whenContentMissing_throws() {
        when(contentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contentIndexingService.indexContent(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("콘텐츠 없음");
    }

    @Test
    void search_withBlankKeyword_usesFindAll() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Page<ContentDocument> page = new PageImpl<>(List.of());
        when(contentSearchRepository.findAll(pageable)).thenReturn(page);

        // When
        Page<ContentDocument> result = contentIndexingService.search(" ", pageable);

        // Then
        assertThat(result).isEqualTo(page);
        verify(contentSearchRepository).findAll(pageable);
        // ElasticsearchOperations는 호출되지 않아야 함
        verifyNoInteractions(elasticsearchOperations);
    }

    @Test
    void search_withKeyword_usesElasticsearchOperations() {
        // 🚨 [대폭 수정] Repository 대신 ElasticsearchOperations를 모킹해야 함
        
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        String keyword = "드라마";
        
        // 결과에 담길 엔티티 데이터
        ContentDocument doc = ContentDocument.builder()
                .contentId(1L)
                .title("드라마 제목")
                .build();

        // SearchHit 모킹 (생성자 이슈 방지를 위해 mock 사용)
        SearchHit<ContentDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(doc);

        // SearchHits 모킹
        SearchHits<ContentDocument> searchHits = mock(SearchHits.class);
        when(searchHits.getTotalHits()).thenReturn(1L);
        when(searchHits.stream()).thenReturn(List.of(hit).stream());

        // NativeQuery 실행 시 모킹된 searchHits 반환 설정
        when(elasticsearchOperations.search(any(NativeQuery.class), eq(ContentDocument.class)))
                .thenReturn(searchHits);

        // When
        Page<ContentDocument> result = contentIndexingService.search(keyword, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("드라마 제목");
        
        // Repository가 아니라 elasticsearchOperations가 호출되었는지 검증
        verify(elasticsearchOperations).search(any(NativeQuery.class), eq(ContentDocument.class));
    }

    @Test
    void deleteContent_deletesById() {
        contentIndexingService.deleteContent(7L);
        verify(contentSearchRepository).deleteById(7L);
    }

    @Test
    void countIndexedContents_returnsRepositoryCount() {
        when(contentSearchRepository.count()).thenReturn(42L);
        assertThat(contentIndexingService.countIndexedContents()).isEqualTo(42L);
    }

    // 샘플 데이터 생성 헬퍼
    private Content sampleContent(Long id, String title) {
        // Tag 객체 모킹 또는 생성
        Tag tag = Tag.builder().name("테스트태그").type("GENRE").isActive(true).build();

        Content content = Content.builder()
                .type(ContentType.SERIES)
                .title(title)
                .description("설명")
                .thumbnailUrl("thumb.jpg")
                .status(ContentStatus.ACTIVE)
                .uploaderId(1L)
                .accessLevel(ContentAccessLevel.FREE)
                .tags(Set.of(tag)) // 🚨 Set<Tag>로 변경됨
                .build();
        ReflectionTestUtils.setField(content, "id", id);
        return content;
    }
}