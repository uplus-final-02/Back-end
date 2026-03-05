package content.dto;

public interface TrendingStatDto {
    Long getContentId();
    Long getTotalDeltaView();
    Long getTotalDeltaBookmark();
    Long getTotalDeltaCompleted();
}
