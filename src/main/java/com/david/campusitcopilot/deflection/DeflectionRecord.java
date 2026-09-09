package com.david.campusitcopilot.deflection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Entity representing a student deflection metric / resolution signal ("did this fix it?").
 * Persists confirmation on whether the copilot successfully resolved the student's issue.
 */
@Entity
@Table(name = "deflections")
public class DeflectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private String conversationId;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "feedback", length = 1000)
    private String feedback;

    @Column(name = "topic")
    private String topic;

    @Column(name = "device")
    private String device;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DeflectionRecord() {
    }

    public DeflectionRecord(String conversationId, boolean resolved, String feedback, String topic, String device) {
        this.conversationId = conversationId;
        this.resolved = resolved;
        this.feedback = feedback;
        this.topic = topic;
        this.device = device;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeflectionRecord that = (DeflectionRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DeflectionRecord{" +
                "id=" + id +
                ", conversationId='" + conversationId + '\'' +
                ", resolved=" + resolved +
                ", topic='" + topic + '\'' +
                ", device='" + device + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
