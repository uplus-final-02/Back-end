package org.backend.userapi.video.service;

import content.repository.ContentRepository;
import content.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountService {
    private final StringRedisTemplate redisTemplate;
    private final VideoRepository videoRepository;
    private final ContentRepository contentRepository;

    // 누적 조회수 키 (Flush 배치가 읽을 대상)
    private static final String CONTENT_VIEW_KEY_PREFIX = "content:view:";
    private static final String VIDEO_VIEW_KEY_PREFIX   = "video:view:";

    // 중복 방지 쿨타임 키
    private static final String VIEW_HISTORY_KEY_PREFIX = "view:history:";

    /**
     * 영상 조회수 증가 처리.
     *
     * [정상] Redis dedup 체크 → Redis 누적 → Flush 스케줄러가 DB 반영
     * [Redis 완전 다운] setIfAbsent 실패 → DB 직접 반영 (dedup 없이)
     * [mid-operation 장애] dedup 성공 후 increment 실패 → 1회 누락 허용
     *   → DB fallback 시 content는 이미 Redis에 +1된 상태일 수 있어 이중 반영 위험
     *   → 이중 반영(+2)보다 누락(±0)이 덜 해롭기 때문에 이 케이스는 손실 허용
     */
    public void incrementViewCount(Long contentId, Long videoId, Long userId, Integer durationSec) {
        String historyKey = VIEW_HISTORY_KEY_PREFIX + videoId + ":" + userId;
        Duration dynamicTtl = Duration.ofSeconds(durationSec + 5);

        // ── 1단계: dedup 체크 — Redis 가용성 판단 지점 ──────────────────
        boolean redisAvailable = true;
        boolean isFirstView    = false;

        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(historyKey, "1", dynamicTtl);
            isFirstView = Boolean.TRUE.equals(result);
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] dedup 확인 불가 → DB Fallback 진입: contentId={}, videoId={}", contentId, videoId);
            redisAvailable = false;
            isFirstView    = true; // Redis 상태 모를 때는 첫 조회로 간주
        }

        // 쿨타임 내 중복 요청이면 종료
        if (!isFirstView) {
            log.trace("조회수 쿨타임 적용 중 (중복 방어): videoId={}, userId={}", videoId, userId);
            return;
        }

        // ── 2단계: Redis 정상 → Redis에 누적 ───────────────────────────
        if (redisAvailable) {
            try {
                redisTemplate.opsForValue().increment(CONTENT_VIEW_KEY_PREFIX + contentId);
                redisTemplate.opsForValue().increment(VIDEO_VIEW_KEY_PREFIX + videoId);
                log.debug("조회수 증가 (Redis): contentId={}, videoId={}, userId={}", contentId, videoId, userId);
            } catch (RedisConnectionFailureException e) {
                // setIfAbsent 성공 후 increment 실패 (mid-operation 장애)
                // → content가 이미 Redis에 +1 됐을 수 있어 DB fallback 시 이중 반영 위험
                // → 이중 반영보다 1회 누락이 덜 해로우므로 이 건은 손실 허용
                log.warn("[Redis DOWN] increment 실패 (dedup 이후 mid-operation 장애)" +
                         " - 조회수 1회 누락 허용: contentId={}, videoId={}", contentId, videoId);
            }
            return;
        }

        // ── 3단계: Redis 완전 다운 확정 → DB 직접 반영 ─────────────────
        // setIfAbsent 자체가 실패한 경우만 진입 → Redis 미반영 확실
        try {
            videoRepository.incrementViewCount(videoId, 1L);
            contentRepository.incrementViewCount(contentId, 1L);
            log.debug("조회수 증가 (DB Fallback): contentId={}, videoId={}", contentId, videoId);
        } catch (Exception dbEx) {
            // DB도 실패하면 이번 조회수는 손실 — 영상 재생 자체는 중단시키지 않음
            log.error("[Fallback 실패] DB 직접 조회수 반영도 실패: contentId={}, videoId={}", contentId, videoId, dbEx);
        }
    }

    /**
     * 시청 완료(90% 이상) 시 쿨타임 초기화.
     * - Redis 다운 시 TTL 자동 만료로 처리되므로 무시
     */
    public void resetViewCoolTime(Long videoId, Long userId) {
        String historyKey = VIEW_HISTORY_KEY_PREFIX + videoId + ":" + userId;
        try {
            redisTemplate.delete(historyKey);
            log.debug("조회수 중복 방지 쿨타임 초기화(COMPLETED): videoId={}, userId={}", videoId, userId);
        } catch (RedisConnectionFailureException e) {
            // 삭제 실패해도 TTL이 만료되면 자동 초기화 → 무시
            log.warn("[Redis DOWN] 쿨타임 초기화 실패 (TTL 자동 만료 예정): videoId={}, userId={}", videoId, userId);
        }
    }
}
