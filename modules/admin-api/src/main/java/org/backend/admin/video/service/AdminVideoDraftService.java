package org.backend.admin.video.service;

import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
import common.enums.ContentAccessLevel;
import common.enums.ContentType;
import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.backend.admin.video.dto.AdminVideoDraftCreateRequest;
import org.backend.admin.video.dto.AdminVideoDraftCreateResponse;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminVideoDraftService {

    private final ContentRepository contentRepository;
    private final VideoRepository videoRepository;
    private final VideoFileRepository videoFileRepository;

    public AdminVideoDraftCreateResponse createDraft(AdminVideoDraftCreateRequest request) {
        if (request == null || request.uploaderId() == null) {
            throw new IllegalArgumentException("uploaderId is required");
        }

        // contents: title/thumbnail_url NOT NULL 이므로 임시값 필요
        Content content = Content.builder()
                .type(ContentType.SINGLE)
                .title("UNTITLED")
                .description(null)
                .thumbnailUrl("") // 또는 "about:blank"
                .uploaderId(request.uploaderId())
                .accessLevel(ContentAccessLevel.FREE)
                .build();
        contentRepository.save(content);

        // single 이므로 episodeNo=1
        Video video = Video.builder()
                .episodeNo(1)
                .title(null)
                .description(null)
                .thumbnailUrl(null)
                .status(VideoStatus.DRAFT)
                // ⚠️ Video 엔티티는 Content 연관관계가 필요하니 setter/생성자 구조에 맞춰 세팅
                .build();

        // 지금 엔티티 구조상 builder에 content가 안 들어가 있으니, 최소 수정이 필요합니다.
        // 가장 안전한 최소 수정: Video에 content를 세팅하는 메서드 추가 or 생성자에서 받기
        // 여기서는 "setContent"가 있다고 가정하지 않고, 아래처럼 '수정용 메서드'를 추천합니다.
        // video.setContent(content);

        // 만약 setContent가 없다면 Video 엔티티에 아래 메서드를 추가하세요:
        // public void setContent(Content content){ this.content = content; }
        video.setContent(content);

        videoRepository.save(video);

        VideoFile vf = VideoFile.builder()
                .video(video)
                .originalUrl(null)
                .hlsUrl(null)
                .durationSec(0)
                .transcodeStatus(TranscodeStatus.WAITING)
                .build();
        videoFileRepository.save(vf);

        return new AdminVideoDraftCreateResponse(content.getId(), video.getId(), vf.getId());
    }
}