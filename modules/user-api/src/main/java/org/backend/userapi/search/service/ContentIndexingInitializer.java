package org.backend.userapi.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentIndexingInitializer implements ApplicationRunner {

    private final ContentIndexingService contentIndexingService;

    @Value("${app.search.initial-index-on-startup:false}")
    private boolean initialIndexOnStartup;

    @Override
    public void run(ApplicationArguments args) {
        if (!initialIndexOnStartup) {
            return;
        }
        contentIndexingService.indexAllContents();
        log.info("Initial content indexing triggered (running in background).");
    }
}
