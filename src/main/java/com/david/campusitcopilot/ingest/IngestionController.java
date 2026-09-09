package com.david.campusitcopilot.ingest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest() {
        int chunks = ingestionService.ingest();
        return Map.of("status", "ok", "chunksStored", chunks);
    }
}
