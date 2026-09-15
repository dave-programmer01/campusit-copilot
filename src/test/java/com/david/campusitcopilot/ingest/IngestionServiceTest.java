package com.david.campusitcopilot.ingest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionServiceTest {

    @Test
    void testIsMfaDoc() {
        assertTrue(IngestionService.isMfaDoc("CUNY-Login-MFA-Setup.md"));
        assertTrue(IngestionService.isMfaDoc("Microsoft365-MFA-Setup.md"));
        assertFalse(IngestionService.isMfaDoc("Lehman-Login-Activation.md"));
        assertFalse(IngestionService.isMfaDoc("MacBook-Wi-Fi-Configuration-Guide.md"));
    }

    @Test
    void testAccountFromMfaFileName() {
        assertEquals("cuny", IngestionService.accountFromMfaFileName("CUNY-Login-MFA-Setup.md"));
        assertEquals("microsoft365", IngestionService.accountFromMfaFileName("Microsoft365-MFA-Setup.md"));
        assertEquals("microsoft365", IngestionService.accountFromMfaFileName("Office-365-MFA.md"));
    }

    @Test
    void testIngestDeletesTopicsAndAddsChunks() {
        VectorStore vectorStore = mock(VectorStore.class);
        IngestionService service = new IngestionService(vectorStore);

        int chunks = service.ingest();
        assertTrue(chunks > 0);

        verify(vectorStore).delete("topic == 'wifi'");
        verify(vectorStore).delete("topic == 'login'");
        verify(vectorStore).delete("topic == 'mfa'");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, atLeastOnce()).add(captor.capture());

        List<List<Document>> allBatches = captor.getAllValues();
        List<Document> allDocs = allBatches.stream().flatMap(List::stream).toList();

        // Check clean split:
        // 5 wifi docs (device, no account)
        // 2 login docs (subtopic + account=lehman, no device)
        // 2 mfa docs (account=cuny or account=microsoft365, no device, no subtopic)

        long wifiDocs = allDocs.stream()
                .filter(d -> "wifi".equals(d.getMetadata().get("topic")))
                .peek(d -> {
                    assertNotNull(d.getMetadata().get("device"));
                    assertNull(d.getMetadata().get("account"));
                    assertNull(d.getMetadata().get("subtopic"));
                })
                .map(d -> d.getMetadata().get("source"))
                .distinct()
                .count();
        assertEquals(5, wifiDocs);

        long loginDocs = allDocs.stream()
                .filter(d -> "login".equals(d.getMetadata().get("topic")))
                .peek(d -> {
                    assertNotNull(d.getMetadata().get("subtopic"));
                    assertEquals("lehman", d.getMetadata().get("account"));
                    assertNull(d.getMetadata().get("device"));
                })
                .map(d -> d.getMetadata().get("source"))
                .distinct()
                .count();
        assertEquals(2, loginDocs);

        long mfaDocs = allDocs.stream()
                .filter(d -> "mfa".equals(d.getMetadata().get("topic")))
                .peek(d -> {
                    assertTrue("cuny".equals(d.getMetadata().get("account")) || "microsoft365".equals(d.getMetadata().get("account")));
                    assertNull(d.getMetadata().get("device"));
                    assertNull(d.getMetadata().get("subtopic"));
                })
                .map(d -> d.getMetadata().get("source"))
                .distinct()
                .count();
        assertEquals(2, mfaDocs);
    }
}
