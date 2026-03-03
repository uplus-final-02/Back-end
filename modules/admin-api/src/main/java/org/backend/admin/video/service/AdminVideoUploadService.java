package org.backend.admin.video.service;

import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminVideoUploadConfirmRequest;
import org.backend.admin.video.dto.AdminVideoUploadConfirmResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminVideoUploadService {

    private final ContentRepository contentRepository;
    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;

    @Transactional
    public AdminVideoUploadConfirmResponse confirmUpload(AdminVideoUploadConfirmRequest req) {
        validate(req);

        Content content = contentRepository.findById(req.contentId())
                .orElseThrow(() -> new RuntimeException("CONTENT_NOT_FOUND"));

        // contentId별 다음 episodeNo 계산 (SINGLE도 SERIES도 동일 로직으로 확장 가능)
        int nextEpisodeNo = videoRepository.findTopByContent_IdOrderByEpisodeNoDesc(req.contentId())
                .map(v -> v.getEpisodeNo() + 1)
                .orElse(1);

        Video video = Video.builder()
                .content(content)
                .episodeNo(nextEpisodeNo)
                .title(req.title())
                .description(req.description())
                .status(VideoStatus.DRAFT) // 정책에 따라 READY/PROCESSING 같은 enum이 있으면 그걸로
                .thumbnailUrl(null)
                .build();

        videoRepository.save(video);

        // 팀 정책(영상 private) 기준: DB에는 URL이 아니라 objectKey를 저장하는 게 맞음
        // 컬럼명이 original_url 이지만 "key 저장"으로 운용해도 됩니다.
        VideoFile vf = VideoFile.builder()
                .video(video)
                .originalUrl(req.objectKey()) // ✅ 사실상 originalKey
                .hlsUrl(null)
                .durationSec(0)
                .transcodeStatus(TranscodeStatus.WAITING)
                .build();

        videoFileRepository.save(vf);

        return new AdminVideoUploadConfirmResponse(
                content.getId(),
                video.getId(),
                vf.getId(),
                vf.getOriginalUrl(),
                vf.getTranscodeStatus().name()
        );
    }

    private void validate(AdminVideoUploadConfirmRequest req) {
        if (req == null || req.contentId() == null) {
            throw new IllegalArgumentException("contentId는 필수입니다.");
        }
        if (!StringUtils.hasText(req.objectKey())) {
            throw new IllegalArgumentException("objectKey는 필수입니다.");
        }
        // title/description은 정책에 따라 필수로 바꿔도 됨
    }
}