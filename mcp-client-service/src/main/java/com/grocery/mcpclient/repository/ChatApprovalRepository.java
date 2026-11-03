package com.grocery.mcpclient.repository;

import com.grocery.mcpclient.entity.ChatApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatApprovalRepository extends JpaRepository<ChatApproval, String> {

    /** The cards of a conversation, newest first (the history puts them back under their answers) */
    List<ChatApproval> findByConversationIdOrderByCreatedAtDesc(String conversationId);
}
