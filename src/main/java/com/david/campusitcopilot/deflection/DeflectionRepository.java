package com.david.campusitcopilot.deflection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link DeflectionRecord}.
 */
@Repository
public interface DeflectionRepository extends JpaRepository<DeflectionRecord, Long> {

    List<DeflectionRecord> findByConversationId(String conversationId);

    long countByResolved(boolean resolved);

    long countByResolvedTrue();

    long countByResolvedFalse();

    long countByTopic(String topic);
}
