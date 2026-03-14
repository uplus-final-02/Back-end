package org.backend.admin.content.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminThumbnailUploadResponse {

    private String uploadedThumbnailUrl;
}