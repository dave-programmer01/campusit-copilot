package com.david.campusitcopilot.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetrievalServiceTest {

    private VectorStore vectorStore;
    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        retrievalService = new RetrievalService(vectorStore);
    }

    @Test
    void testFilterSpecNormalizesWhitespaceAndNulls() {
        FilterSpec spec = new FilterSpec("  wifi  ", "  macbook  ", "  ", "  ");
        assertEquals("wifi", spec.topic());
        assertEquals("macbook", spec.device());
        assertNull(spec.subtopic());
        assertNull(spec.account());

        FilterSpec wifiSpec = FilterSpec.wifi("iphone");
        assertEquals("wifi", wifiSpec.topic());
        assertEquals("iphone", wifiSpec.device());
        assertNull(wifiSpec.subtopic());
        assertNull(wifiSpec.account());

        FilterSpec loginSpec = FilterSpec.login("activation");
        assertEquals("login", loginSpec.topic());
        assertNull(loginSpec.device());
        assertEquals("activation", loginSpec.subtopic());
        assertEquals("lehman", loginSpec.account());

        FilterSpec mfaSpec = FilterSpec.mfa("cuny");
        assertEquals("mfa", mfaSpec.topic());
        assertNull(mfaSpec.device());
        assertNull(mfaSpec.subtopic());
        assertEquals("cuny", mfaSpec.account());
    }

    @Test
    void testSearchWithWifiFilterSpec() {
        Document doc = new Document("MacBook setup steps", Map.of("topic", "wifi", "device", "macbook"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Document> results = retrievalService.search("how to connect", FilterSpec.wifi("macbook"), 1, 0.0);

        assertEquals(1, results.size());
        assertEquals("MacBook setup steps", results.get(0).getText());

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertEquals("how to connect", request.getQuery());
        assertEquals(1, request.getTopK());
        assertEquals(0.0, request.getSimilarityThreshold());
        assertNotNull(request.getFilterExpression());
    }

    @Test
    void testSearchWithLoginFilterSpec() {
        Document doc = new Document("Password reset steps", Map.of("topic", "login", "subtopic", "reset"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Document> results = retrievalService.search("forgot password", FilterSpec.login("reset"), 2, 0.5);

        assertEquals(1, results.size());
        assertEquals("Password reset steps", results.get(0).getText());

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertEquals("forgot password", request.getQuery());
        assertEquals(2, request.getTopK());
        assertEquals(0.5, request.getSimilarityThreshold());
        assertNotNull(request.getFilterExpression());
    }

    @Test
    void testSearchWithMfaFilterSpec() {
        Document doc = new Document("CUNY Login MFA steps", Map.of("topic", "mfa", "account", "cuny"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Document> results = retrievalService.search("MFA error", FilterSpec.mfa("cuny"), 1, 0.0);

        assertEquals(1, results.size());
        assertEquals("CUNY Login MFA steps", results.get(0).getText());

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertEquals("MFA error", request.getQuery());
        assertEquals(1, request.getTopK());
        assertEquals(0.0, request.getSimilarityThreshold());
        assertNotNull(request.getFilterExpression());
    }

    @Test
    void testSearchWithoutFilter() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<Document> results = retrievalService.search("general query", new FilterSpec(null, null, null), 3, 0.0);

        assertTrue(results.isEmpty());
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertNull(request.getFilterExpression());
    }

    @Test
    void testSearchLegacyDeviceMethod() {
        Document doc = new Document("Win 11 steps", Map.of("device", "windows-11"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Document> results = retrievalService.search("windows wifi", "windows-11", 1, 0.0);

        assertEquals(1, results.size());
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest request = captor.getValue();
        assertNotNull(request.getFilterExpression());
    }
}
