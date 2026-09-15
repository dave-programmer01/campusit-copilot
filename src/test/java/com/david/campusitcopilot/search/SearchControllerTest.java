package com.david.campusitcopilot.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SearchControllerTest {

    private RetrievalService retrievalService;
    private SearchController searchController;

    @BeforeEach
    void setUp() {
        retrievalService = mock(RetrievalService.class);
        searchController = new SearchController(retrievalService);
    }

    @Test
    void testSearchWithAllParams() {
        Document doc = new Document("Guide content here", Map.of("source", "guide.md", "device", "macbook"));
        when(retrievalService.search(eq("query"), any(FilterSpec.class), anyInt(), anyDouble()))
                .thenReturn(List.of(doc));

        List<SearchController.SearchHit> hits = searchController.search("query", "wifi", "macbook", null, null);

        assertEquals(1, hits.size());
        assertEquals("guide.md", hits.get(0).source());
        assertEquals("macbook", hits.get(0).device());
        assertEquals("Guide content here", hits.get(0).snippet());

        ArgumentCaptor<FilterSpec> captor = ArgumentCaptor.forClass(FilterSpec.class);
        verify(retrievalService).search(eq("query"), captor.capture(), eq(3), eq(0.0));
        FilterSpec spec = captor.getValue();
        assertEquals("wifi", spec.topic());
        assertEquals("macbook", spec.device());
        assertNull(spec.subtopic());
        assertNull(spec.account());
    }

    @Test
    void testSearchLoginSubtopic() {
        Document doc = new Document("Password reset guide content", Map.of("source", "reset.md"));
        when(retrievalService.search(eq("reset password"), any(FilterSpec.class), anyInt(), anyDouble()))
                .thenReturn(List.of(doc));

        List<SearchController.SearchHit> hits = searchController.search("reset password", "login", null, "reset", "lehman");

        assertEquals(1, hits.size());
        assertEquals("reset.md", hits.get(0).source());
        assertNull(hits.get(0).device());

        ArgumentCaptor<FilterSpec> captor = ArgumentCaptor.forClass(FilterSpec.class);
        verify(retrievalService).search(eq("reset password"), captor.capture(), eq(3), eq(0.0));
        FilterSpec spec = captor.getValue();
        assertEquals("login", spec.topic());
        assertNull(spec.device());
        assertEquals("reset", spec.subtopic());
        assertEquals("lehman", spec.account());
    }

    @Test
    void testSearchMfaWithAccount() {
        Document doc = new Document("CUNY Login MFA steps", Map.of("source", "CUNY-Login-MFA-Setup.md"));
        when(retrievalService.search(eq("MFA error"), any(FilterSpec.class), anyInt(), anyDouble()))
                .thenReturn(List.of(doc));

        List<SearchController.SearchHit> hits = searchController.search("MFA error", "mfa", null, null, "cuny");

        assertEquals(1, hits.size());
        assertEquals("CUNY-Login-MFA-Setup.md", hits.get(0).source());

        ArgumentCaptor<FilterSpec> captor = ArgumentCaptor.forClass(FilterSpec.class);
        verify(retrievalService).search(eq("MFA error"), captor.capture(), eq(3), eq(0.0));
        FilterSpec spec = captor.getValue();
        assertEquals("mfa", spec.topic());
        assertNull(spec.device());
        assertNull(spec.subtopic());
        assertEquals("cuny", spec.account());
    }
}
