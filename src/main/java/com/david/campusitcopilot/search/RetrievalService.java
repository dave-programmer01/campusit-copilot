package com.david.campusitcopilot.search;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class RetrievalService {

    private final VectorStore vectorStore;

    public RetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> search(String query, FilterSpec filterSpec, int topK, double similarityThreshold) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold);

        Filter.Expression filterExpression = buildFilterExpression(filterSpec);
        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
        }

        List<Document> results = vectorStore.similaritySearch(builder.build());
        return results == null ? List.of() : results;
    }

    public List<Document> search(String query, String device, int topK, double similarityThreshold) {
        return search(query, new FilterSpec(null, device, null), topK, similarityThreshold);
    }

    private Filter.Expression buildFilterExpression(FilterSpec filterSpec) {
        if (filterSpec == null) {
            return null;
        }

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();

        if (StringUtils.hasText(filterSpec.topic())) {
            ops.add(b.eq("topic", filterSpec.topic().trim()));
        }
        if (StringUtils.hasText(filterSpec.device())) {
            ops.add(b.eq("device", filterSpec.device().trim()));
        }
        if (StringUtils.hasText(filterSpec.subtopic())) {
            ops.add(b.eq("subtopic", filterSpec.subtopic().trim()));
        }
        if (StringUtils.hasText(filterSpec.account())) {
            ops.add(b.eq("account", filterSpec.account().trim()));
        }

        if (ops.isEmpty()) {
            return null;
        }

        FilterExpressionBuilder.Op combined = ops.get(0);
        for (int i = 1; i < ops.size(); i++) {
            combined = b.and(combined, ops.get(i));
        }
        return combined.build();
    }
}
