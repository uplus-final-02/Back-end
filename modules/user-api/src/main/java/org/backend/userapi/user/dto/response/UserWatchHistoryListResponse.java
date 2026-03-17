package org.backend.userapi.user.dto.response;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWatchHistoryListResponse {
  private List<UserWatchHistoryGroupResponse> watchHistories;
}