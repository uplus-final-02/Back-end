// modules/user-api/src/main/java/org/backend/userapi/content/publish/dto/UserPublishStatusResponse.java
package org.backend.userapi.content.publish.dto;

import common.enums.ContentStatus;
import common.enums.TranscodeStatus;

public record UserPublishStatusResponse(
        Long userContentId,
        boolean publishRequested,
        ContentStatus contentStatus,
        TranscodeStatus transcodeStatus
) {}