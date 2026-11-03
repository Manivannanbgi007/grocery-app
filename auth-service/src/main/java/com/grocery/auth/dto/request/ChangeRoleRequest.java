package com.grocery.auth.dto.request;

import com.grocery.auth.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(

        @Schema(description = "Any role except SUPER_ADMIN", example = "ADMIN")
        @NotNull
        Role role) {
}
