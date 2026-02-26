package org.backend.userapi.tag.dto;

import common.entity.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
public class TagResponse {
    private Long tagId;
    private String name;

    public static TagResponse from(Tag tag) {
        return TagResponse.builder()
                .tagId(tag.getId())
                .name(tag.getName())
                .build();
    }
}