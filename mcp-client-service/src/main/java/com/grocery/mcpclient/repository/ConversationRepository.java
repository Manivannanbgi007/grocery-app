package com.grocery.mcpclient.repository;

import com.grocery.mcpclient.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    /** Ids of conversations without a message since the given time (the nightly clean-up deletes them in batches). */
    @Query("SELECT c.id FROM Conversation c WHERE c.lastMessageAt < :before ORDER BY c.lastMessageAt")
    List<String> findIdsInactiveSince(Instant before, Pageable batch);
}
