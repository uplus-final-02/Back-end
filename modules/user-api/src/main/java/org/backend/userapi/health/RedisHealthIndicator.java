package org.backend.userapi.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component("redisDependency")
public class RedisHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redisTemplate;

    public RedisHealthIndicator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Health health() {
        try {
            String ping = redisTemplate.execute(RedisConnection::ping);
            if (ping == null) {
                return Health.down()
                        .withDetail("dependency", "redis")
                        .withDetail("reason", "PING returned null")
                        .build();
            }

            return Health.up()
                    .withDetail("dependency", "redis")
                    .withDetail("ping", ping)
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("dependency", "redis")
                    .build();
        }
    }
}
