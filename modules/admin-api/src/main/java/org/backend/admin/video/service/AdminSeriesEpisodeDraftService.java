package org.backend.admin.video.service;

import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import common.enums.ContentType;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.backend.admin.exception.ContentNotFoundException;
import org.backend.admin.video.dto.AdminEpisodeDraftResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSeriesEpisodeDraftService {

    private final ContentRepository contentRepository;
    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;

    @Transactional
    public AdminEpisodeDraftResponse createDraft(Long seriesId) {

        Content series = contentRepository.findById(seriesId)
                .orElseThrow(ContentNotFoundException::new);

        if (series.getType() != ContentType.SERIES) {
            throw new IllegalArgumentException("INVALID_SERIES: SERIES 타입 콘텐츠가 아닙니다. contentId=" + seriesId);
        }

        // content_id=seriesId
        int nextEpisodeNo = videoRepository.findTopByContent_IdOrderByEpisodeNoDesc(seriesId)
                .map(v -> v.getEpisodeNo() + 1)
                .orElse(1);

        Video video = Video.builder()
                .content(series)
                .episodeNo(nextEpisodeNo)
                .title(null)
                .description(null)
                .thumbnailUrl(null)
                .status(VideoStatus.DRAFT)
                .build();

        videoRepository.save(video);

        VideoFile videoFile = VideoFile.builder()
                .video(video)
                .originalUrl(null)              // objectKey는 confirm에서 저장
                .hlsUrl(null)
                .durationSec(0)
                .transcodeStatus(TranscodeStatus.PENDING_UPLOAD)
                .build();

        videoFileRepository.save(videoFile);

        return new AdminEpisodeDraftResponse(
                series.getId(),
                video.getId(),
                videoFile.getId(),
                nextEpisodeNo
        );
    }
}