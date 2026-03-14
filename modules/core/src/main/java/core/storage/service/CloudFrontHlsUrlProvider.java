package core.storage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.s3.provider", havingValue = "aws")
public class CloudFrontHlsUrlProvider implements HlsUrlProvider {

    @Value("${app.storage.cloudfront.domain-name:}")
    private String domainName;

    @Override
    public String getHlsUrl(Long videoFileId) {
        // S3(MinIO) 내의 객체 키 경로: hls/{videoFileId}/master.m3u8
        return "https://" + domainName + "/hls/" + videoFileId + "/master.m3u8";
    }
}