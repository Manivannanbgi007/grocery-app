package com.grocery.platform.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Which dead-letter messages to send back: one (partition + offset, from the list), or every message that was not
 * replayed yet (all = true). "all" is a Boolean, not a boolean: Jackson 3 refuses a missing value for a primitive.
 */
public record DeadLetterReplayRequest(
        @PositiveOrZero Integer partition,
        @PositiveOrZero Long offset,
        Boolean all) {

    /** true = every message not replayed yet; a missing "all" means one message. */
    public boolean replayAll() {
        return Boolean.TRUE.equals(all);
    }

    @AssertTrue(message = "Send either partition and offset, or all = true")
    public boolean isOneOrAll() {
        return replayAll() ? partition == null && offset == null : partition != null && offset != null;
    }
}
