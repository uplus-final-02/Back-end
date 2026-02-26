package org.backend.userapi.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class WatchHistoryListResponse {
  private List<WatchHistoryDto> watchHistory;
  private String nextCursor; // 다음 페이지 커서
  private boolean hasNext;   // 다음 페이지 존재 여부
}