package org.backend.userapi.tag.dto;

import common.entity.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.backend.userapi.content.dto.DefaultContentResponse;

@Getter
@Builder
public class TagResponse {
    private Long tagId;
    private String name;
    private Long priority;
    private Boolean isActive;

    public static DefaultContentResponse.TagResponse from(Tag tag) {
        return DefaultContentResponse.TagResponse.builder()
                .tagId(tag.getId())
                .name(tag.getName())
                .priority(tag.getPriority())
                .isActive(tag.getIsActive())
                .build();
    }
}