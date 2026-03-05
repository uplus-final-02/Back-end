package org.backend.transcoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EnableJpaAuditing
@SpringBootApplication(scanBasePackages = {
        "org.backend.transcoder",
        "core",
        "common", "content"
})
@EntityScan(basePackages = {
        "content.entity"
})
@EnableJpaRepositories(basePackages = {
        "content.repository"
})
public class TranscoderWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TranscoderWorkerApplication.class, args);
    }
}