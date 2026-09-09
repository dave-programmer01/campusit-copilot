package com.david.campusitcopilot.ingest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@ConditionalOnProperty(name = "app.ingest.on-startup", havingValue = "true")
public class IngestionRunner implements CommandLineRunner {

    private final IngestionService ingestionService;

    public IngestionRunner(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(String... args) {
        log.info("app.ingest.on-startup=true detected, running one-off ingestion...");
        ingestionService.ingest();
    }
}
