package org.backend.userapi.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SUGGEST_KEY_PREFIX = "search:suggest:";
    private static final Duration SUGGEST_TTL = Duration.ofMinutes(2);

    public List<String> getFromCache(String keyword) {
        String cacheKey = buildKey(keyword);
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) return null;
            log.debug("[Cache HIT] 자동완성 캐시 히트: keyword={}", keyword);
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 자동완성 캐시 조회 실패 → ES 직접 조회: keyword={}", keyword);
            return null;
        } catch (Exception e) {
            log.warn("[Cache] 자동완성 역직렬화 실패 → ES 직접 조회: keyword={}", keyword);
            return null;
        }
    }

    public void putToCache(String keyword, List<String> suggestions) {
        String cacheKey = buildKey(keyword);
        try {
            String json = objectMapper.writeValueAsString(suggestions);
            redisTemplate.opsForValue().set(cacheKey, json, SUGGEST_TTL);
            log.debug("[Cache] 자동완성 결과 저장: keyword={}, count={}", keyword, suggestions.size());
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 자동완성 캐시 저장 실패 - 무시: keyword={}", keyword);
        } catch (Exception e) {
            log.warn("[Cache] 자동완성 직렬화 실패 - 무시: keyword={}", keyword);
        }
    }

    private String buildKey(String keyword) {
        String encoded = URLEncoder.encode(keyword.toLowerCase(Locale.ROOT), StandardCharsets.UTF_8);
        return SUGGEST_KEY_PREFIX + encoded;
    }
}