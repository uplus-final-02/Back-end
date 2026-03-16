package org.backend.userapi.search.service;

import java.util.Map;

/**
 * 유저 업로드 콘텐츠 ES 인덱싱 서비스 인터페이스.
 * 인덱스: {@code user_contents_v1}
 */
public interface UserContentIndexingService {

    /**
     * 전체 ACTIVE 유저 콘텐츠 인덱싱 (비동기, 커서 기반 청크 처리).
     */
    void indexAllUserContents();

    /**
     * 단건 유저 콘텐츠 인덱싱.
     *
     * @param userContentId UserContent PK
     */
    void indexUserContent(Long userContentId);

    /**
     * 단건 유저 콘텐츠 인덱스 삭제.
     *
     * @param userContentId UserContent PK
     */
    void deleteUserContent(Long userContentId);

    /**
     * 현재 인덱싱 상태 조회.
     *
     * @return status(IDLE/RUNNING/SUCCESS/FAILED), lastRunTime, error(optional)
     */
    Map<String, Object> getIndexingStatus();
}
