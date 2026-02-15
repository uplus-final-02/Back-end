package org.backend.userapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"org.backend", "user", "common", "content"})
@EntityScan(basePackages = {"user.entity", "common.entity", "content.entity"})
@EnableJpaRepositories(basePackages = {"user.repository", "common.repository", "content.repository"})
@EnableElasticsearchRepositories(basePackages = "org.backend.userapi.search.repository")
@EnableJpaAuditing
@EnableScheduling
public class UserApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApiApplication.class, args);
    }
}
