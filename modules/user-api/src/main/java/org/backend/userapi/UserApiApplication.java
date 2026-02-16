package org.backend.userapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"org.backend", "user", "common", "content"})
@EntityScan(basePackages = {"user.entity", "common.entity", "content.entity"})
@EnableJpaRepositories(basePackages = {"user.repository", "common.repository", "content.repository"})
@EnableJpaAuditing
public class UserApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApiApplication.class, args);
    }
}