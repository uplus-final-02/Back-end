package core.storage.service;

public interface HlsUrlProvider {
    /**
     * 비디오 파일 ID를 받아 재생 가능한 HLS 마스터 URL을 반환합니다.
     * 로컬 환경: /api/hls/{id}/master.m3u8?signature=...
     * 운영 환경: https://cdn.example.com/hls/{id}/master.m3u8?signature=...
     */
    String getHlsUrl(Long videoFileId);
}