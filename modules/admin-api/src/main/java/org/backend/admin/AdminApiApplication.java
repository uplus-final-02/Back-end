package org.backend.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = {
        "org.backend.admin",          // admin-api
        "core",                       // core 모듈 (core.security.*)
        "common", "content", "interaction", "user"  // domain 모듈
})
@EntityScan(basePackages = {"common", "content", "interaction", "user"})
@EnableJpaRepositories(basePackages = {"common", "content", "interaction", "user"})
public class AdminApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApiApplication.class, args);
    }
}