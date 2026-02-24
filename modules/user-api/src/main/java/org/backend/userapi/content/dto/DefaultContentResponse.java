package org.backend.userapi.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import common.entity.Tag;
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import content.entity.Content;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class DefaultContentResponse {

    private Long contentId;
    private ContentType type;

    private String title;
    @JsonRawValue
    private String description;     // Json 형식
    private String thumbnailUrl;

    private ContentStatus status;
    private Long totalViewCount;
    private Long bookmarkCount;

    private String uploaderName;    // uploaderId -> nickname
    private ContentAccessLevel accessLevel;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<ContentDetailResponse.TagResponse> tags;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TagResponse {
        private Long tagId;
        private String name;
        private String type;
        private Boolean isActive;

        public static ContentDetailResponse.TagResponse from(Tag tag) {
            return ContentDetailResponse.TagResponse.builder()
                    .tagId(tag.getId())
                    .name(tag.getName())
                    .type(tag.getType())
                    .isActive(tag.getIsActive())
                    .build();
        }
    }

    // 기본 from 메소드 (내부에서 태그 필터링 없음 - 호출하는 쪽에서 필터링된 태그를 넘겨주거나, 전체 태그 사용)
    // 하지만 기존 코드 호환성을 위해, 여기서는 전체 태그를 변환하도록 둡니다.
    // 만약 서비스에서 필터링된 태그를 쓰고 싶다면 아래 오버로딩된 메소드를 사용하세요.
    public static DefaultContentResponse from(Content content, String uploaderName) {
        return from(content, uploaderName, content.getTags());
    }

    // 오버로딩: 필터링된 태그 리스트를 직접 받아서 처리
    public static DefaultContentResponse from(Content content, String uploaderName, List<Tag> tags) {
        return DefaultContentResponse.builder()
                .contentId(content.getId())
                .type(content.getType())
                .title(content.getTitle())
                .description(content.getDescription())
                .thumbnailUrl(content.getThumbnailUrl())
                .status(content.getStatus())
                .totalViewCount(content.getTotalViewCount())
                .bookmarkCount(content.getBookmarkCount())
                .uploaderName(uploaderName)
                .accessLevel(content.getAccessLevel())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt())
                .tags(tags.stream()
                        .map(TagResponse::from)
                        .collect(Collectors.toList())
                )
                .build();
    }
}
