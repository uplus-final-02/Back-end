package org.backend.userapi.health;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("elasticsearchDependency")
public class ElasticsearchHealthIndicator implements HealthIndicator {

    private final ElasticsearchClient elasticsearchClient;

    public ElasticsearchHealthIndicator(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Health health() {
        try {
            BooleanResponse response = elasticsearchClient.ping();
            if (response.value()) {
                return Health.up()
                        .withDetail("dependency", "elasticsearch")
                        .build();
            }

            return Health.down()
                    .withDetail("dependency", "elasticsearch")
                    .withDetail("reason", "ping returned false")
                    .build();
        } catch (Exception e) {
            return Health.down(e)
                    .withDetail("dependency", "elasticsearch")
                    .build();
        }
    }
}
