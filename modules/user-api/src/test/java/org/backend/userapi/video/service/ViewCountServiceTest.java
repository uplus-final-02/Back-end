package org.backend.userapi.video.service;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ExtendWith(SpringExtension.class) // JUnit 5와 Spring Context 통합
@Import({DataRedisAutoConfiguration.class, ViewCountService.class}) // Redis 기본 설정과 테스트할 Service만 로드
@Testcontainers
public class ViewCountServiceTest {

    @Autowired
    private ViewCountService viewCountService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // Testcontainers: 독립적인 Redis 환경 구성
    @Container
    private static final GenericContainer<?> REDIS_CONTAINER =
        new GenericContainer<>(DockerImageName.parse("redis:7.2.3-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379).toString());
    }

    private final Long contentId = 100L;
    private final Long videoId = 200L;
    private final Long userId = 300L;
    private final Integer durationSec = 60; // 60초 영상

    @BeforeEach
    void setUp() {
        // 테스트 시작 전 Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("최초 시청 시 조회수가 1 증가하고 가변 TTL이 설정된다.")
    void incrementViewCount_firstTime() {
        // when
        viewCountService.incrementViewCount(contentId, videoId, userId, durationSec);

        // then
        String historyKey = "view:history:" + videoId + ":" + userId;
        String contentKey = "content:view:" + contentId;
        String videoKey = "video:view:" + videoId;

        // 1. 조회수 1 증가 확인
        assertThat(redisTemplate.opsForValue().get(contentKey)).isEqualTo("1");
        assertThat(redisTemplate.opsForValue().get(videoKey)).isEqualTo("1");

        // 2. 가변 TTL 설정 확인 (durationSec + 5초 오차 감안)
        Long expireTime = redisTemplate.getExpire(historyKey);
        assertThat(expireTime).isGreaterThan(durationSec);
        assertThat(expireTime).isLessThanOrEqualTo(durationSec + 5);
    }

    @Test
    @DisplayName("가변 TTL 쿨타임 내에 중복 호출 시 조회수는 증가하지 않는다.")
    void incrementViewCount_duplicatedWithinCoolTime() {
        // given
        viewCountService.incrementViewCount(contentId, videoId, userId, durationSec);

        // when (중복 호출)
        viewCountService.incrementViewCount(contentId, videoId, userId, durationSec);
        viewCountService.incrementViewCount(contentId, videoId, userId, durationSec);

        // then (처음 1번만 적용되어야 함)
        String contentKey = "content:view:" + contentId;
        String videoKey = "video:view:" + videoId;

        assertThat(redisTemplate.opsForValue().get(contentKey)).isEqualTo("1");
        assertThat(redisTemplate.opsForValue().get(videoKey)).isEqualTo("1");
    }

    @Test
    @DisplayName("동시에 여러 번 API가 호출되어도 조회수는 1번만 증가한다. (동시성 제어 검증)")
    void incrementViewCount_concurrencyControl() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when: 100개의 스레드가 동시에 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    viewCountService.incrementViewCount(contentId, videoId, userId, durationSec);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then: SETNX 덕분에 단 1번만 성공해야 함
        String contentKey = "content:view:" + contentId;
        String videoKey = "video:view:" + videoId;

        assertThat(redisTemplate.opsForValue().get(contentKey)).isEqualTo("1");
        assertThat(redisTemplate.opsForValue().get(videoKey)).isEqualTo("1");
    }

    @Test
    @DisplayName("COMPLETED 달성 시 TTL 쿨타임이 강제 해제된다.")
    void resetViewCoolTime() {
        // given: 최초 시청으로 쿨타임 세팅
        viewCountService.incrementViewCount(contentId, videoId, userId, durationSec);
        String historyKey = "view:history:" + videoId + ":" + userId;
        assertThat(redisTemplate.hasKey(historyKey)).isTrue();

        // when: 쿨타임 강제 해제 (TODO 2에서 호출할 메서드)
        viewCountService.resetViewCoolTime(videoId, userId);

        // then: Key가 삭제되었는지 확인
        assertThat(redisTemplate.hasKey(historyKey)).isFalse();
    }
}
