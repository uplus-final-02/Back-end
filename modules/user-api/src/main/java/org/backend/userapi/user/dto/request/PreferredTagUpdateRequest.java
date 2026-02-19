package org.backend.userapi.user.dto.request;
import java.util.List;

public record PreferredTagUpdateRequest(
    List<Long> tagIds
) {}