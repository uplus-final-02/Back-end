package org.backend.userapi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Java 8 날짜/시간(LocalDateTime) 처리를 위한 모듈 등록
        objectMapper.registerModule(new JavaTimeModule());
        // 타임스탬프 대신 ISO-8601 형식(예: 2026-02-18T...)으로 출력 설정
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
}