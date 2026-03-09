package org.backend.admin.tag.dto;

import common.entity.Tag;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TagResponse {
    private Long tagId;
    private String name;
    private Long priority;

    public static TagResponse from(Tag tag) {
        return TagResponse.builder()
                .tagId(tag.getId())
                .name(tag.getName())
                .priority(tag.getPriority())
                .build();
    }
}