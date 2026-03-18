package org.backend.admin.content.publish.dto;

public record AdminPublishStatusResponse(
        Long contentId,
        String contentStatus,
        boolean publishRequested,
        boolean anyTranscodeDone
) {}