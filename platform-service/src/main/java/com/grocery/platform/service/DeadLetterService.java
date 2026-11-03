package com.grocery.platform.service;

import com.grocery.platform.dto.request.DeadLetterReplayRequest;
import com.grocery.platform.dto.response.DeadLetterReplayResponse;
import com.grocery.platform.dto.response.DeadLetterResponse;
import com.grocery.platform.dto.response.DeadLetterTopicResponse;

import java.util.List;

/**
 * The dead-letter topics ("<topic>.DLT"): events a consumer could not handle after all its retries.
 * Look at them, fix the cause, then send them back to their topic.
 */
public interface DeadLetterService {

    List<DeadLetterTopicResponse> listTopics();

    /** The newest {@code limit} messages of one dead-letter topic, newest first. */
    List<DeadLetterResponse> read(String topic, int limit);

    /** Sends one message, or every message not replayed yet, back to its topic; writes each replay to the audit log. */
    DeadLetterReplayResponse replay(String topic, DeadLetterReplayRequest request, Long superAdminUserId);
}
