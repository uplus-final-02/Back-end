package org.backend.userapi.search.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 유저 업로드 콘텐츠 ES 문서.
 *
 * <p>인덱스: {@code user_contents_v1}
 *
 * <p>[태그 벡터 전략]
 * UserContent 자체에 contentTags가 없으므로 parentContent(관리자 원본)의 태그를 상속한다.
 * tagVector = tagVectorService.buildContentVector(parentContent.contentTags)
 *
 * <p>[인덱싱 조건]
 * contentStatus = ACTIVE 인 UserContent만 인덱싱한다.
 * (트랜스코딩 완료 + UserVideoFileStatusService.markDone() 이후 ACTIVE 전환 시점)
 *
 * <p>[mapping/settings 재사용]
 * 기존 contents_v1과 동일한 분석기 설정을 사용한다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "user_contents_v1", createIndex = true)
@Setting(settingPath = "elasticsearch/content-settings.json")
@Mapping(mappingPath = "elasticsearch/user-content-mapping.json")
public class UserContentDocument {

    @Id
    private Long userContentId;

    /** 관리자 원본 콘텐츠 ID (태그 상속 출처) */
    @Field(type = FieldType.Long)
    private Long parentContentId;

    @Field(type = FieldType.Text, analyzer = "nori_analyzer", searchAnalyzer = "nori_analyzer")
    private String title;

    /** parentContent의 태그명 목록 상속 */
    @Field(type = FieldType.Keyword)
    private List<String> tags;

    /**
     * 업로더 유저 ID.
     * 크리에이터 페이지에서 특정 유저의 콘텐츠만 검색/필터링할 때 사용.
     */
    @Field(type = FieldType.Long)
    private Long uploaderId;

    /** ACTIVE 필터용 — ACTIVE 인 것만 인덱싱되므로 항상 ACTIVE */
    @Field(type = FieldType.Keyword)
    private String contentStatus;

    @Field(type = FieldType.Keyword)
    private String accessLevel;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Long)
    private Long totalViewCount;

    @Field(type = FieldType.Long)
    private Long bookmarkCount;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createdAt;

    /** parentContent 태그 기반 100차원 벡터 (TagVectorService.buildContentVector) */
    @Field(type = FieldType.Dense_Vector)
    private float[] tagVector;
}
