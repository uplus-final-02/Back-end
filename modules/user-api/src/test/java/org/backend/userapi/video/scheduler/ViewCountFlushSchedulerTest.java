package org.backend.userapi.video.scheduler;

import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import common.enums.TranscodeStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoRepository;
import org.backend.userapi.search.repository.ContentSearchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

// H2 인메모리 DB를 사용하고, Flyway/DDL-AUTO를 꺼서 자체적으로 테스트 환경 격리
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:scheduler_test;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop", // 이 테스트에서는 DB 스키마가 필요하므로 생성함
})
@Testcontainers
class ViewCountFlushSchedulerTest {

    @MockitoBean
    private ContentSearchRepository contentSearchRepository;
    @MockitoBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ViewCountFlushScheduler flushScheduler;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private ContentRepository contentRepository;

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

    private Content savedContent;
    private Video savedVideo;

    @BeforeEach
    void setUp() {
        // 1. Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        // 2. Content 엔티티 생성 및 필수값 리플렉션 주입
        Content content = Content.builder().build();
        ReflectionTestUtils.setField(content, "title", "Test Content Title");
        ReflectionTestUtils.setField(content, "uploaderId", 1L);
        ReflectionTestUtils.setField(content, "totalViewCount", 100L);
        ReflectionTestUtils.setField(content, "accessLevel", ContentAccessLevel.FREE);
        ReflectionTestUtils.setField(content, "status", ContentStatus.ACTIVE);
        ReflectionTestUtils.setField(content, "type", ContentType.SERIES);
        ReflectionTestUtils.setField(content, "thumbnailUrl", "http://thumbnail.23");

        savedContent = contentRepository.save(content);

        // 3. Video 엔티티 생성 및 필수값 리플렉션 주입
        Video video = Video.builder().build();
        ReflectionTestUtils.setField(video, "content", savedContent);
        ReflectionTestUtils.setField(video, "viewCount", 50L);
        ReflectionTestUtils.setField(video, "episodeNo", 1);

        VideoFile videoFile = VideoFile.builder().build();
        ReflectionTestUtils.setField(videoFile, "video", video); // 양방향 연관관계 세팅
        ReflectionTestUtils.setField(videoFile, "hlsUrl", "http://test/hls/master.m3u8");
        ReflectionTestUtils.setField(videoFile, "durationSec", 120);
        ReflectionTestUtils.setField(videoFile, "transcodeStatus", TranscodeStatus.DONE);

        ReflectionTestUtils.setField(video, "videoFile", videoFile);

        savedVideo = videoRepository.save(video);
    }

    @AfterEach
    void tearDown() {
        videoRepository.deleteAll();
        contentRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("Redis에 누적된 조회수가 스케줄러 실행 시 DB에 반영되고 Redis 값은 0으로 리셋된다.")
    void flushViewCountsToDB_Success() {
        // given: 핑(Ping)이 여러 번 들어와서 Redis에 조회수가 누적되었다고 가정
        Long contentId = savedContent.getId();
        Long videoId = savedVideo.getId();

        String contentKey = "content:view:" + contentId;
        String videoKey = "video:view:" + videoId;

        // Redis에 수동으로 증가분(Delta) 세팅
        redisTemplate.opsForValue().set(contentKey, "30"); // Content 조회수 30 증가
        redisTemplate.opsForValue().set(videoKey, "15");   // Video 조회수 15 증가

        // when: 스케줄러 수동 강제 실행 (실제로는 시간에 맞춰 돌지만, 테스트를 위해 직접 호출)
        flushScheduler.flushViewCountsToDB();

        // then 1: Redis의 값은 모두 "0"으로 리셋되어야 함 (getAndSet 검증)
        assertThat(redisTemplate.opsForValue().get(contentKey)).isEqualTo("0");
        assertThat(redisTemplate.opsForValue().get(videoKey)).isEqualTo("0");

        // then 2: DB의 값은 기존 값에 Delta가 더해져서 업데이트되어야 함
        Content updatedContent = contentRepository.findById(contentId).orElseThrow();
        Video updatedVideo = videoRepository.findById(videoId).orElseThrow();

        // 기존 100 + 증가 30 = 130
        assertThat(updatedContent.getTotalViewCount()).isEqualTo(130L);
        // 기존 50 + 증가 15 = 65
        assertThat(updatedVideo.getViewCount()).isEqualTo(65L);
    }
}