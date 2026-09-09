package com.david.campusitcopilot.deflection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Endpoint for recording student deflection signals ("did this fix it?").
 * Saves feedback records directly into PostgreSQL table for deflection analytics and reporting.
 */
@RestController
public class DeflectionController {

    private static final Logger log = LoggerFactory.getLogger(DeflectionController.class);

    private final DeflectionRepository deflectionRepository;

    public DeflectionController(DeflectionRepository deflectionRepository) {
        this.deflectionRepository = deflectionRepository;
    }

    @PostMapping({"/deflection", "/feedback"})
    public ResponseEntity<DeflectionResponse> recordDeflection(@RequestBody DeflectionRequest request) {
        String convId = StringUtils.hasText(request.conversationId())
                ? request.conversationId()
                : UUID.randomUUID().toString();

        boolean resolved = request.isResolvedStatus();
        String feedback = request.feedbackText();

        DeflectionRecord record = new DeflectionRecord(
                convId,
                resolved,
                feedback,
                request.topic(),
                request.device()
        );

        DeflectionRecord saved = deflectionRepository.save(record);
        log.info("Recorded deflection metric: id={}, conversationId={}, resolved={}, topic={}, device={}",
                saved.getId(), saved.getConversationId(), saved.isResolved(), saved.getTopic(), saved.getDevice());

        DeflectionResponse response = new DeflectionResponse(
                saved.getId(),
                saved.getConversationId(),
                saved.isResolved(),
                saved.getTopic(),
                saved.getDevice(),
                saved.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping({"/deflection/stats", "/feedback/stats"})
    public ResponseEntity<DeflectionStats> getStats() {
        long total = deflectionRepository.count();
        long resolved = deflectionRepository.countByResolvedTrue();
        long unresolved = deflectionRepository.countByResolvedFalse();
        double rate = total > 0 ? ((double) resolved / total) * 100.0 : 0.0;

        return ResponseEntity.ok(new DeflectionStats(total, resolved, unresolved, rate));
    }

    public record DeflectionRequest(
            String conversationId,
            Boolean resolved,
            Boolean fixed,
            String feedback,
            String comments,
            String topic,
            String device
    ) {
        public boolean isResolvedStatus() {
            if (resolved != null) {
                return resolved;
            }
            if (fixed != null) {
                return fixed;
            }
            return false;
        }

        public String feedbackText() {
            if (StringUtils.hasText(feedback)) {
                return feedback;
            }
            return comments;
        }
    }

    public record DeflectionResponse(
            Long id,
            String conversationId,
            boolean resolved,
            String topic,
            String device,
            Instant createdAt
    ) {}

    public record DeflectionStats(
            long total,
            long resolved,
            long unresolved,
            double deflectionRate
    ) {}
}
