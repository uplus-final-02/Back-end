package org.backend.userapi.search.service;

import java.util.List;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SuggestMode;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.TermSuggestOption;
import common.util.ChosungUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionServiceImpl implements SuggestionService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final SuggestionCacheService suggestionCacheService;

    @Override
    public List<String> getSuggestions(String keyword) {
        if (!StringUtils.hasText(keyword)) return List.of();

        List<String> cached = suggestionCacheService.getFromCache(keyword);
        if (cached != null) return cached;

        boolean isChosungQuery = !keyword.matches(".*[가-힣].*");

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.should(s -> s.match(m -> m.field("title.ngram").query(keyword)));
                    b.should(s -> s.match(m -> m.field("tags.search").query(keyword)));
                    if (isChosungQuery) {
                        String chosungKeyword = ChosungUtil.extract(keyword).replaceAll("\\s+", "");
                        b.should(s -> s.match(m -> m.field("titleChosung.ngram").query(chosungKeyword)));
                    }
                    b.minimumShouldMatch("1");
                    return b;
                }))
                .withPageable(Pageable.ofSize(10))
                .build();

        try {
            List<String> results = elasticsearchOperations.search(query, ContentDocument.class)
                    .stream()
                    .map(h -> h.getContent().getTitle())
                    .distinct()
                    .toList();

            suggestionCacheService.putToCache(keyword, results);
            return results;

        } catch (DataAccessException e) {
            log.warn("[ES DOWN] 자동완성 ES 연결 실패 → 빈 결과 반환: keyword={}", keyword);
            return List.of();
        }
    }

    @Override
    public String getDidYouMean(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;

        try {
            SearchResponse<ContentDocument> response = elasticsearchClient.search(s -> s
                    .index("contents")
                    .size(0) // 문서는 안 가져옴
                    .suggest(sg -> sg
                            .suggesters("title-suggest", ts -> ts
                                    .text(keyword)
                                    .term(t -> t
                                            .field("title")
                                            .suggestMode(SuggestMode.Missing)
                                            .size(1)
                                    )
                            )
                    ),
                    ContentDocument.class
            );

            return response.suggest()
                    .getOrDefault("title-suggest", List.of())
                    .stream()
                    .flatMap(entry -> entry.term().options().stream())
                    .map(TermSuggestOption::text)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            log.warn("[ES DOWN] Did-you-mean ES 연결 실패: keyword={}", keyword);
            return null;
        }
    }
}