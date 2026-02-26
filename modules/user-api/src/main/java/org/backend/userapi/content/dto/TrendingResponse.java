package org.backend.userapi.content.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TrendingResponse {
    private Integer rank;
    private Double trendingScore;
    private DefaultContentResponse content;
}
