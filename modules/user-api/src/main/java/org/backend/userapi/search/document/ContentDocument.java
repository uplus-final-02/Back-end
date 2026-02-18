package org.backend.userapi.search.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import org.springframework.data.annotation.Transient; // 💡 Import 추가 필수!

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "contents_v1", createIndex = true)
@Setting(settingPath = "elasticsearch/content-settings.json")
public class ContentDocument {

    @Id
    private Long contentId;

    @Field(type = FieldType.Text, analyzer = "nori_analyzer", searchAnalyzer = "nori_analyzer")
    private String title;

    @Field(type = FieldType.Text, analyzer = "nori_analyzer", searchAnalyzer = "nori_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Keyword)
    private String type;
    
    @Field(type = FieldType.Keyword)
    private String status;
    
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
    
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime updatedAt;

    // 🚨 [수정] ES 저장소 제외 (응답 DTO용)
    @Transient 
    private String highlightTitle;

    @Transient
    private String highlightDescription;
}