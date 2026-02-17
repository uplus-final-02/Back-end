package org.backend.userapi.tag.dto.request;
import java.util.List;

public record PreferredTagUpdateRequest(
    List<Long> tagIds
) {}