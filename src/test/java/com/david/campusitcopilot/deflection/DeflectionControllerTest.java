package com.david.campusitcopilot.deflection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeflectionControllerTest {

    private DeflectionRepository deflectionRepository;
    private DeflectionController controller;

    @BeforeEach
    void setUp() {
        deflectionRepository = mock(DeflectionRepository.class);
        controller = new DeflectionController(deflectionRepository);
    }

    @Test
    void testRecordDeflectionWithResolvedTrue() {
        DeflectionRecord saved = new DeflectionRecord("conv-123", true, "Thanks, it worked!", "wifi", "macbook");
        saved.setId(10L);
        saved.setCreatedAt(Instant.now());

        when(deflectionRepository.save(any(DeflectionRecord.class))).thenReturn(saved);

        DeflectionController.DeflectionRequest request = new DeflectionController.DeflectionRequest(
                "conv-123",
                true,
                null,
                "Thanks, it worked!",
                null,
                "wifi",
                "macbook"
        );

        ResponseEntity<DeflectionController.DeflectionResponse> response = controller.recordDeflection(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(10L, response.getBody().id());
        assertEquals("conv-123", response.getBody().conversationId());
        assertTrue(response.getBody().resolved());
        assertEquals("wifi", response.getBody().topic());
        assertEquals("macbook", response.getBody().device());

        ArgumentCaptor<DeflectionRecord> captor = ArgumentCaptor.forClass(DeflectionRecord.class);
        verify(deflectionRepository).save(captor.capture());
        DeflectionRecord captured = captor.getValue();
        assertEquals("conv-123", captured.getConversationId());
        assertTrue(captured.isResolved());
        assertEquals("Thanks, it worked!", captured.getFeedback());
        assertEquals("wifi", captured.getTopic());
        assertEquals("macbook", captured.getDevice());
    }

    @Test
    void testRecordDeflectionWithFixedAlias() {
        DeflectionRecord saved = new DeflectionRecord("conv-fixed", true, "All good", "login", null);
        saved.setId(20L);

        when(deflectionRepository.save(any(DeflectionRecord.class))).thenReturn(saved);

        DeflectionController.DeflectionRequest request = new DeflectionController.DeflectionRequest(
                "conv-fixed",
                null,
                true, // using "fixed" instead of "resolved"
                null,
                "All good", // using "comments" instead of "feedback"
                "login",
                null
        );

        ResponseEntity<DeflectionController.DeflectionResponse> response = controller.recordDeflection(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().resolved());

        ArgumentCaptor<DeflectionRecord> captor = ArgumentCaptor.forClass(DeflectionRecord.class);
        verify(deflectionRepository).save(captor.capture());
        DeflectionRecord captured = captor.getValue();
        assertTrue(captured.isResolved());
        assertEquals("All good", captured.getFeedback());
    }

    @Test
    void testRecordDeflectionGeneratesConversationIdWhenMissing() {
        DeflectionRecord saved = new DeflectionRecord("auto-id", false, "Still broken", "wifi", "iphone");
        saved.setId(30L);

        when(deflectionRepository.save(any(DeflectionRecord.class))).thenReturn(saved);

        DeflectionController.DeflectionRequest request = new DeflectionController.DeflectionRequest(
                null,
                false,
                null,
                "Still broken",
                null,
                "wifi",
                "iphone"
        );

        ResponseEntity<DeflectionController.DeflectionResponse> response = controller.recordDeflection(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<DeflectionRecord> captor = ArgumentCaptor.forClass(DeflectionRecord.class);
        verify(deflectionRepository).save(captor.capture());
        assertNotNull(captor.getValue().getConversationId());
        assertFalse(captor.getValue().getConversationId().isBlank());
        assertFalse(captor.getValue().isResolved());
    }

    @Test
    void testGetStats() {
        when(deflectionRepository.count()).thenReturn(100L);
        when(deflectionRepository.countByResolvedTrue()).thenReturn(85L);
        when(deflectionRepository.countByResolvedFalse()).thenReturn(15L);

        ResponseEntity<DeflectionController.DeflectionStats> statsResponse = controller.getStats();

        assertEquals(HttpStatus.OK, statsResponse.getStatusCode());
        assertNotNull(statsResponse.getBody());
        assertEquals(100L, statsResponse.getBody().total());
        assertEquals(85L, statsResponse.getBody().resolved());
        assertEquals(15L, statsResponse.getBody().unresolved());
        assertEquals(85.0, statsResponse.getBody().deflectionRate(), 0.001);
    }

    @Test
    void testGetStatsZeroTotalReturnsZeroRate() {
        when(deflectionRepository.count()).thenReturn(0L);
        when(deflectionRepository.countByResolvedTrue()).thenReturn(0L);
        when(deflectionRepository.countByResolvedFalse()).thenReturn(0L);

        ResponseEntity<DeflectionController.DeflectionStats> statsResponse = controller.getStats();

        assertEquals(HttpStatus.OK, statsResponse.getStatusCode());
        assertNotNull(statsResponse.getBody());
        assertEquals(0L, statsResponse.getBody().total());
        assertEquals(0.0, statsResponse.getBody().deflectionRate(), 0.001);
    }
}
