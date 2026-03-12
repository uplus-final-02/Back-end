package org.backend.userapi.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * ShedLock 분산 락 설정.
 *
 * <p>[목적]
 * user-api 2개 인스턴스가 동일한 @Scheduled 메서드를 동시에 실행하지 않도록
 * MySQL shedlock 테이블을 중재자로 사용해 딱 1개 인스턴스만 실행되게 보장.
 *
 * <p>[동작 방식]
 * <pre>
 *   user-api #1 ──┐
 *                 ├──→ MySQL(shedlock 테이블) ← 원자적 INSERT/UPDATE 경쟁
 *   user-api #2 ──┘
 *         ↓               ↓
 *    락 획득 성공        락 획득 실패
 *    → 작업 실행        → 해당 사이클 스킵 (예외 없음)
 * </pre>
 *
 * <p>[defaultLockAtMostFor]
 * 전체 기본값 PT10M. 개별 @SchedulerLock에서 재정의 가능.
 * 앱이 중간에 크래시해도 10분 후엔 락이 자동 해제되어 데드락 방지.
 *
 * <p>[usingDbTime()]
 * 락 만료 계산 기준을 애플리케이션 서버 시계가 아닌 DB 시계로 통일.
 * EC2 2대의 시계가 NTP로 동기화되어 있어도 수 ms 차이 발생 가능 → DB 기준으로 안전하게.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()   // EC2 인스턴스 간 시계 차이 무관
                        .build()
        );
    }
}
