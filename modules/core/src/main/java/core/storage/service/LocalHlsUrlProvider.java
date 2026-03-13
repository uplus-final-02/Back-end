package core.storage.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.s3.provider", havingValue = "minio", matchIfMissing = true)
public class LocalHlsUrlProvider implements HlsUrlProvider {

    private final LocalUrlSignatureService signatureService;

    @Override
    public String getHlsUrl(Long videoFileId) {
        // 1. 로컬 프록시 컨트롤러 경로
        String path = "/api/hls/" + videoFileId + "/master.m3u8";
        
        // 2. 만료 시간 설정 (1시간)
        long expiry = Instant.now().getEpochSecond() + 3600;

        // 3. 서명 생성
        String signature = signatureService.sign(path, expiry);

        // 4. 최종 URL 반환 (프론트엔드가 이 URL로 요청하면 UserHlsProxyController가 받음)
        return path + "?expires=" + expiry + "&signature=" + signature;
    }
}