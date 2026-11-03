package com.grocery.delivery.dto.request;

import com.grocery.delivery.enums.DeliveryIssue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Why the delivery cannot be completed.
 *
 * @param note optional details, e.g. "Called twice, no answer"
 */
public record IssueRequest(@NotNull DeliveryIssue issue, @Size(max = 255) String note) {
}
