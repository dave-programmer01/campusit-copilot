package com.david.campusitcopilot.search;

import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;


@RestController
public class SearchController {


    private static final int TOP_K = 3;


    private static final double SIMILARITY_THRESHOLD = 0.0;


    private static final int SNIPPET_LENGTH = 200;

    private final RetrievalService retrievalService;

    public SearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/search")
    public List<SearchHit> search(
            @RequestParam("q") String q,
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "device", required = false) String device,
            @RequestParam(value = "subtopic", required = false) String subtopic,
            @RequestParam(value = "account", required = false) String account) {

        FilterSpec filterSpec = new FilterSpec(topic, device, subtopic, account);
        List<Document> results = retrievalService.search(q, filterSpec, TOP_K, SIMILARITY_THRESHOLD);

        return results.stream()
                .map(this::toHit)
                .toList();
    }

    private SearchHit toHit(Document doc) {
        Object source = doc.getMetadata().get("source");
        Object device = doc.getMetadata().get("device");
        String text = doc.getText();
        String snippet = snippet(text);
        return new SearchHit(
                source == null ? null : source.toString(),
                device == null ? null : device.toString(),
                doc.getScore(),
                snippet);
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SNIPPET_LENGTH) + "…";
    }


    public record SearchHit(String source, String device, Double score, String snippet) {
        public SearchHit {
            Objects.requireNonNull(snippet, "snippet");
        }
    }
}
