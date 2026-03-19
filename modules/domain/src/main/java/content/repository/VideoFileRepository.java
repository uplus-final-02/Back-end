package content.repository;

import content.entity.VideoFile;
import common.enums.TranscodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VideoFileRepository extends JpaRepository<VideoFile, Long> {
    Optional<VideoFile> findByVideoId(Long videoId);
    boolean existsByVideo_Content_IdAndTranscodeStatus(Long contentId, TranscodeStatus status);
    long countByVideo_Content_IdAndTranscodeStatus(Long contentId, TranscodeStatus status);
}