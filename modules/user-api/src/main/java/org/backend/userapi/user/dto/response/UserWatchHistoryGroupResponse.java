package org.backend.userapi.user.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWatchHistoryGroupResponse {
    private Long parentContentId;
    private String parentTitle;
    private String parentThumbnailUrl;
    private List<UserWatchHistoryResponse> histories;
}