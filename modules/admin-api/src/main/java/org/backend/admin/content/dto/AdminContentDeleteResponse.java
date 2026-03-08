package org.backend.admin.content.dto;

import content.entity.Content;

public record AdminContentDeleteResponse(
        Long contentId,
        String status
) {
    public static AdminContentDeleteResponse from(Content content) {
        return new AdminContentDeleteResponse(
                content.getId(),
                content.getStatus().name()
        );
    }
}