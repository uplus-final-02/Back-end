package org.backend.userapi.search.repository;

import org.backend.userapi.search.document.ContentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ContentSearchRepository extends ElasticsearchRepository<ContentDocument, Long> {

}
