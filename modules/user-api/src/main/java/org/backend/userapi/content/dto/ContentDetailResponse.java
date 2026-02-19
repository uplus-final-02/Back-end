package org.backend.userapi.content.dto;

import common.entity.Tag;
import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContentDetailResponse {

    private Long contentId;
    private ContentType type;

    private String title;
    private String description;
    private String thumbnailUrl;

    private ContentStatus status;
    private Long totalViewCount;
    private Long bookmarkCount;

    private Long uploaderId;
    private ContentAccessLevel accessLevel;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<TagResponse> tags;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TagResponse {
        private Long tagId;
        private String name;
        private String type;
        private Boolean isActive;

        public static TagResponse from(Tag tag) {
            return TagResponse.builder()
                    .tagId(tag.getId())
                    .name(tag.getName())
                    .type(tag.getType())
                    .isActive(tag.getIsActive())
                    .build();
        }
    }
}