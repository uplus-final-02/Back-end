package org.backend.userapi.video.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountService {
    private final StringRedisTemplate redisTemplate;

    // 누적 조회수 키 (Flush 배치가 읽을 대상)
    private static final String CONTENT_VIEW_KEY_PREFIX = "content:view:";
    private static final String VIDEO_VIEW_KEY_PREFIX = "video:view:";

    // 중복 방지 쿨타임 키
    private static final String VIEW_HISTORY_KEY_PREFIX = "view:history:";

    /**
     * 영상 조회수 증가 처리
     */
    public void incrementViewCount(Long contentId, Long videoId, Long userId, Integer durationSec) {
        String historyKey = VIEW_HISTORY_KEY_PREFIX + videoId + ":" + userId;

        // 가변 TTL 설정: 영상 길이 + 네트워크/로딩 지연을 고려한 5초 버퍼
        Duration dynamicTtl = Duration.ofSeconds(durationSec + 5);

        // 1. 중복 확인용 Key 생성 시도 (원자적 처리)
        Boolean isFirstViewInCoolTime = redisTemplate.opsForValue()
                                                     .setIfAbsent(historyKey, "1", dynamicTtl);

        // 2. 쿨타임 내 첫 조회인 경우 실제 조회수 증가
        if (Boolean.TRUE.equals(isFirstViewInCoolTime)) {
            String contentKey = CONTENT_VIEW_KEY_PREFIX + contentId;
            String videoKey = VIDEO_VIEW_KEY_PREFIX + videoId;

            redisTemplate.opsForValue().increment(contentKey);
            redisTemplate.opsForValue().increment(videoKey);

            log.debug("조회수 증가 로직 실행: contentId={}, videoId={}, userId={}", contentId, videoId, userId);
        } else {
            // 중복 시청인 경우 로그만 남기고 무시 (서버 부하 방지를 위해 로그 레벨 조절 필요)
            log.trace("조회수 쿨타임 적용 중 (중복 방어): videoId={}, userId={}", videoId, userId);
        }
    }

    /**
     * 시청 완료(90% 이상) 시 쿨타임 초기화
     */
    public void resetViewCoolTime(Long videoId, Long userId) {
        String historyKey = VIEW_HISTORY_KEY_PREFIX + videoId + ":" + userId;
        redisTemplate.delete(historyKey);
        log.debug("조회수 중복 방지 쿨타임 초기화(COMPLETED): videoId={}, userId={}", videoId, userId);
    }
}
