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
            guides = resourceResolver.getResources("classpath*:/*.md");
            if (guides == null || guides.length == 0) {
                guides = resourceResolver.getResources("classpath:/*.md");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to locate markdown guides on the classpath", e);
        }

        // Purge previously ingested vectors first so re-running this (e.g. after editing a
        // guide) replaces rather than duplicates them. One delete per topic — the wifi delete
        // won't touch login rows, so login and mfa need their own deletes too.
        deleteTopic("wifi");
        deleteTopic("login");
        deleteTopic("mfa");

        int totalChunks = 0;
        for (Resource guide : guides) {
            String fileName = guide.getFilename();
            if (fileName == null || fileName.equalsIgnoreCase("readme.md") || fileName.equalsIgnoreCase("help.md")) {
                continue;
            }
            TextReader reader = new TextReader(guide);
            reader.getCustomMetadata().put("source", fileName);

            // Fork on the metadata bundle:
            // - mfa docs get topic=mfa + account (cuny or microsoft365) (no device, no subtopic)
            // - login docs get topic=login + subtopic + account=lehman (no device)
            // - wifi docs get topic=wifi + device (no subtopic, no account)
            String description;
            if (isMfaDoc(fileName)) {
                String account = accountFromMfaFileName(fileName);
                reader.getCustomMetadata().put("topic", "mfa");
                reader.getCustomMetadata().put("account", account);
                description = "account=" + account;
            } else if (isLoginDoc(fileName)) {
                String subtopic = subtopicFromFileName(fileName);
                reader.getCustomMetadata().put("topic", "login");
                reader.getCustomMetadata().put("subtopic", subtopic);
                reader.getCustomMetadata().put("account", "lehman");
                description = "subtopic=" + subtopic + ", account=lehman";
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

    static boolean isMfaDoc(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).contains("mfa");
    }

    static String accountFromMfaFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.contains("cuny")) {
            return "cuny";
        }
        if (lower.contains("microsoft") || lower.contains("365")) {
            return "microsoft365";
        }
        return "cuny";
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
