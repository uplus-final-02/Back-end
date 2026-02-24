package org.backend.userapi.user.dto.request;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "선호 태그 변경 요청 데이터")
public record PreferredTagUpdateRequest(
   
	@Schema(description = "사용자가 선택한 선호 태그의 ID 목록", example = "[1, 2, 3")
	List<Long> tagIds
) {}