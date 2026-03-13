package core.storage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.s3.provider", havingValue = "cloudfront")
public class CloudFrontHlsUrlProvider implements HlsUrlProvider {

    private final CloudFrontService cloudFrontService;

    @Override
    public String getHlsUrl(Long videoFileId) {
        // S3(MinIO) 내의 객체 키 경로: hls/{videoFileId}/master.m3u8
        String objectKey = "hls/" + videoFileId + "/master.m3u8";
        return cloudFrontService.generateSignedUrl(objectKey);
    }
}