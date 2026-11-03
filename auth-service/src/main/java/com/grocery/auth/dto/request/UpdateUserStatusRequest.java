package com.grocery.auth.dto.request;

import com.grocery.auth.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(

        @Schema(example = "DISABLED")
        @NotNull
        UserStatus status) {
}
