package org.backend.userapi.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "indexingExecutor") // 빈 이름을 지정해두면 나중에 @Async("indexingExecutor")로 골라 쓸 수 있음
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 1. 기본적으로 유지할 스레드 수 (평소 대기하는 직원 수)
        executor.setCorePoolSize(5);
        
        // 2. 최대 스레드 수 (바쁠 때 임시로 늘리는 최대 직원 수)
        executor.setMaxPoolSize(10);
        
        // 3. 대기열 크기 (모든 직원이 바쁠 때 줄 세우는 대기석)
        // 대기석이 꽉 차면 그제서야 MaxPoolSize까지 스레드를 늘림
        executor.setQueueCapacity(100);
        
        // 4. 스레드 이름 접두사 (로그 볼 때 "Indexer-1" 처럼 보여서 디버깅에 필수)
        executor.setThreadNamePrefix("Indexer-");
        
        executor.initialize();
        return executor;
	}
}
