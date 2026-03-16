package org.backend.userapi.search.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_log", indexes = {
    @Index(name = "idx_search_log_keyword", columnList = "keyword"),
    @Index(name = "idx_search_log_searched_at", columnList = "searched_at"),
    @Index(name = "idx_search_log_result_count", columnList = "result_count")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String keyword;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "user_id")
    private Long userId; // null = 비로그인

    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    public static SearchLog of(String keyword, int resultCount, Long userId) {
        SearchLog log = new SearchLog();
        log.keyword = keyword;
        log.resultCount = resultCount;
        log.userId = userId;
        log.searchedAt = LocalDateTime.now();
        return log;
    }
}