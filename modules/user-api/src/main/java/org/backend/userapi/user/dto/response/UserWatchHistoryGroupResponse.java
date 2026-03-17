package org.backend.userapi.user.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserWatchHistoryGroupResponse {
    private Long parentContentId;
    private List<UserWatchHistoryResponse> watchHistories;
}