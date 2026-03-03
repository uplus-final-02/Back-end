package org.backend.admin.video.service;

import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import core.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.backend.admin.video.dto.AdminVideoUploadConfirmRequest;
import org.backend.admin.video.dto.AdminVideoUploadConfirmResponse;
import org.springframework.stereotype.Service;
import core.storage.ObjectStorageService;
import core.storage.ObjectNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.backend.admin.exception.ContentNotFoundException;
import org.backend.admin.exception.UploadNotCompletedException;

@Service
@RequiredArgsConstructor
public class AdminVideoUploadService {

    private final ContentRepository contentRepository;
    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;

    private final ObjectStorageService objectStorageService;

    @Transactional
    public AdminVideoUploadConfirmResponse confirmUpload(AdminVideoUploadConfirmRequest req) {
        validate(req);

        var stat = safeStat(req.objectKey());
        if (stat.size() <= 0) {
            throw new UploadNotCompletedException(); // 0바이트도 업로드 미완료
        }

        Content content = contentRepository.findById(req.contentId())
                .orElseThrow(ContentNotFoundException::new);

        int nextEpisodeNo = videoRepository.findTopByContent_IdOrderByEpisodeNoDesc(req.contentId())
                .map(v -> v.getEpisodeNo() + 1)
                .orElse(1);

        Video video = Video.builder()
                .content(content)
                .episodeNo(nextEpisodeNo)
                .title(req.title())
                .description(req.description())
                .status(VideoStatus.DRAFT)
                .thumbnailUrl(null)
                .build();

        videoRepository.save(video);

        VideoFile vf = VideoFile.builder()
                .video(video)
                .originalUrl(req.objectKey())
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
    }

    private ObjectStorageService.ObjectStat safeStat(String objectKey) {
        try {
            return objectStorageService.statObject(objectKey);
        } catch (ObjectNotFoundException e) {
            throw new UploadNotCompletedException();
        } catch (StorageException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("NoSuchKey") || msg.contains("Object does not exist"))) {
                throw new UploadNotCompletedException();
            }
            throw e;
        }
    }
}