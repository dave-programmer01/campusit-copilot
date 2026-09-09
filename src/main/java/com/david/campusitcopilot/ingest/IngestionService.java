package com.david.campusitcopilot.ingest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class IngestionService {


    private static final TokenTextSplitter SPLITTER = TokenTextSplitter.builder()
            .withChunkSize(1000)            // tokens per chunk
            .withMinChunkSizeChars(600)     // don't emit tiny fragments
            .withMinChunkLengthToEmbed(10)
            .withMaxNumChunks(10)
            .withKeepSeparator(true)
            .build();

    private final VectorStore vectorStore;
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }


    public int ingest() {
        Resource[] guides;
        try {
            guides = resourceResolver.getResources("classpath:/*.md");
        } catch (Exception e) {
            throw new IllegalStateException("Unable to locate markdown guides on the classpath", e);
        }

        // Purge previously ingested vectors first so re-running this (e.g. after editing a
        // guide) replaces rather than duplicates them. One delete per topic — the wifi delete
        // won't touch login rows, so login needs its own delete too.
        deleteTopic("wifi");
        deleteTopic("login");

        int totalChunks = 0;
        for (Resource guide : guides) {
            String fileName = guide.getFilename();
            if (fileName == null) {
                continue;
            }
            TextReader reader = new TextReader(guide);
            reader.getCustomMetadata().put("source", fileName);

            // Fork on the metadata bundle: login docs get topic=login + subtopic (no device),
            // everything else stays wifi with topic=wifi + device (no subtopic). Only the keys
            // that apply to each topic are set, so login chunks never carry a stray device field.
            String description;
            if (isLoginDoc(fileName)) {
                String subtopic = subtopicFromFileName(fileName);
                reader.getCustomMetadata().put("topic", "login");
                reader.getCustomMetadata().put("subtopic", subtopic);
                description = "subtopic=" + subtopic;
            } else {
                String device = deviceFromFileName(fileName);
                reader.getCustomMetadata().put("topic", "wifi");
                reader.getCustomMetadata().put("device", device);
                description = "device=" + device;
            }

            List<Document> documents = reader.get();
            List<Document> chunks = SPLITTER.apply(documents);

            vectorStore.add(chunks);
            totalChunks += chunks.size();
            log.info("Ingested guide '{}' ({}) into {} chunk(s)", fileName, description, chunks.size());
        }

        log.info("Ingestion complete: {} chunk(s) stored across {} guide(s)", totalChunks, guides.length);
        return totalChunks;
    }

    private void deleteTopic(String topic) {
        try {
            vectorStore.delete("topic == '" + topic + "'");
            log.info("Cleared existing topic={} vectors before re-ingesting", topic);
        } catch (Exception e) {
            log.warn("Could not clear existing topic={} vectors (continuing): {}", topic, e.getMessage());
        }
    }

    static boolean isLoginDoc(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).contains("login");
    }

    static String subtopicFromFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.contains("activation")) {
            return "activation";
        }
        if (lower.contains("reset") || lower.contains("password")) {
            return "reset";
        }
        // Fallback: normalise the whole file name into a slug so nothing is silently mistyped.
        int dot = lower.lastIndexOf('.');
        String name = dot > 0 ? lower.substring(0, dot) : lower;
        return name.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    static String deviceFromFileName(String fileName) {
        String name = fileName;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        String lower = name.toLowerCase(Locale.ROOT);

        if (lower.contains("windows 11") || lower.contains("windows-11")) {
            return "windows-11";
        }
        if (lower.contains("windows 10") || lower.contains("windows-10")) {
            return "windows-10";
        }
        if (lower.contains("macbook")) {
            return "macbook";
        }
        if (lower.contains("iphone")) {
            return "iphone";
        }
        if (lower.contains("android")) {
            return "android";
        }
        // Fallback: normalise the whole file name into a slug.
        return lower.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
