package org.backend.userapi.content.dto;

import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EpisodesResponse {

    private Long contentId;
    private int count;
    private List<EpisodeResponse> episodes;

    public static EpisodesResponse of(Long contentId, List<EpisodeResponse> episodes) {
        return EpisodesResponse.builder()
                .contentId(contentId)
                .count(episodes.size())
                .episodes(episodes)
                .build();
    }
}