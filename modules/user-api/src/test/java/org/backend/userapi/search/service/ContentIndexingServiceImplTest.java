package org.backend.userapi.search.service;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentIndexingServiceImplTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentSearchRepository contentSearchRepository;

    private ContentIndexingServiceImpl contentIndexingService;

    @BeforeEach
    void setUp() {
        contentIndexingService = new ContentIndexingServiceImpl(contentRepository, contentSearchRepository);
    }

    @Test
    void indexAllContents_savesAllMappedDocuments() {
        Content content = sampleContent(10L, "테스트 제목");
        when(contentRepository.findAll()).thenReturn(List.of(content));

        contentIndexingService.indexAllContents();

        ArgumentCaptor<List<ContentDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(contentSearchRepository).saveAll(captor.capture());
        List<ContentDocument> saved = captor.getValue();

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getContentId()).isEqualTo(10L);
        assertThat(saved.get(0).getTitle()).isEqualTo("테스트 제목");
    }

    @Test
    void indexContent_whenContentMissing_throws() {
        when(contentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contentIndexingService.indexContent(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("콘텐츠를 찾을 수 없습니다.");
    }

    @Test
    void search_withBlankKeyword_usesFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ContentDocument> page = new PageImpl<>(List.of());
        when(contentSearchRepository.findAll(pageable)).thenReturn(page);

        Page<ContentDocument> result = contentIndexingService.search(" ", pageable);

        assertThat(result).isEqualTo(page);
        verify(contentSearchRepository).findAll(pageable);
    }

    @Test
    void search_withKeyword_usesKeywordSearch() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ContentDocument> page = new PageImpl<>(List.of());
        when(contentSearchRepository.searchByKeyword("드라마", pageable)).thenReturn(page);

        Page<ContentDocument> result = contentIndexingService.search("드라마", pageable);

        assertThat(result).isEqualTo(page);
        verify(contentSearchRepository).searchByKeyword("드라마", pageable);
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

    private Content sampleContent(Long id, String title) {
        Content content = Content.builder()
                .type(ContentType.SERIES)
                .title(title)
                .description("설명")
                .thumbnailUrl("thumb.jpg")
                .status(ContentStatus.ACTIVE)
                .uploaderId(1L)
                .accessLevel(ContentAccessLevel.FREE)
                .build();
        ReflectionTestUtils.setField(content, "id", id);
        return content;
    }
}
