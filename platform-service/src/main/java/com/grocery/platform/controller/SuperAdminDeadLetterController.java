package com.grocery.platform.controller;

import com.grocery.platform.context.UserContextHolder;
import com.grocery.platform.dto.common.ApiResponse;
import com.grocery.platform.dto.request.DeadLetterReplayRequest;
import com.grocery.platform.dto.response.DeadLetterReplayResponse;
import com.grocery.platform.dto.response.DeadLetterResponse;
import com.grocery.platform.dto.response.DeadLetterTopicResponse;
import com.grocery.platform.service.DeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dead letters: events that failed after all retries. Look at them, and send them back once the cause is fixed.
 * Allowed role (checked by the API gateway): SUPER_ADMIN. How to use them: docs/resilience.md, "Dead letters".
 */
@Tag(name = "Super Admin - Dead letters", description = "Events that failed after all retries: inspect and replay (SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/super-admin/dead-letters")
@RequiredArgsConstructor
public class SuperAdminDeadLetterController {

    private final DeadLetterService deadLetterService;

    @Operation(summary = "Every dead-letter topic and how many messages it keeps (30 days)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<DeadLetterTopicResponse>>> listTopics() {
        return ResponseEntity.ok(ApiResponse.success("Dead-letter topics fetched successfully", deadLetterService.listTopics()));
    }

    @Operation(summary = "The newest messages of one dead-letter topic: the event, why it failed, whether it was replayed")
    @GetMapping("/{topic}")
    public ResponseEntity<ApiResponse<List<DeadLetterResponse>>> read(@PathVariable String topic,
                                                                     @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return ResponseEntity.ok(ApiResponse.success("Dead letters fetched successfully", deadLetterService.read(topic, limit)));
    }

    @Operation(summary = "Send one message (partition + offset) or every message not replayed yet (all) back to its topic")
    @PostMapping("/{topic}/replay")
    public ResponseEntity<ApiResponse<DeadLetterReplayResponse>> replay(@PathVariable String topic,
                                                                        @Valid @RequestBody DeadLetterReplayRequest request) {
        DeadLetterReplayResponse response = deadLetterService.replay(topic, request, UserContextHolder.getRequiredUserId());
        return ResponseEntity.ok(ApiResponse.success(response.replayed() + " message(s) sent back to their topic", response));
    }
}
