package org.backend.transcoder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TranscoderWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TranscoderWorkerApplication.class, args);
    }
}