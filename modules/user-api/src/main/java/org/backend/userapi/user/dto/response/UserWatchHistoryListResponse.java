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
public class UserWatchHistoryListResponse {
  private List<UserWatchHistoryResponse> watchHistory;
  private String nextCursor;
  private boolean hasNext;
}