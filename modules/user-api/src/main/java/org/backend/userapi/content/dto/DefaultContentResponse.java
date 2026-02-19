package org.backend.userapi.content.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import content.entity.Content;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class DefaultContentResponse {

    private Long contentId;
    private String title;
    private String thumbnailUrl;
    private String uploaderName;
    private Long totalViewCount;

    @JsonProperty("bookmark_count")
    private Long bookmarkCount;

    @JsonRawValue
    private String description;

    private List<String> tags;

    public static DefaultContentResponse from(Content content, String uploaderName) {
        return DefaultContentResponse.builder()
                .contentId(content.getId())
                .title(content.getTitle())
                .thumbnailUrl(content.getThumbnailUrl())
                .uploaderName(uploaderName)
                .totalViewCount(content.getTotalViewCount())
                .bookmarkCount(content.getBookmarkCount())
                .description(content.getDescription())
                .tags(content.getContentTags().stream()
                        .map(ct -> ct.getTag().getName())
                        .collect(Collectors.toList()))
                .build();
    }
}
