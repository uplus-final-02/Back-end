package org.backend.userapi.search.repository;

import org.backend.userapi.search.document.UserContentDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 유저 업로드 콘텐츠 ES Repository.
 * 인덱스: {@code user_contents_v1}
 */
public interface UserContentSearchRepository
        extends ElasticsearchRepository<UserContentDocument, Long> {
}
