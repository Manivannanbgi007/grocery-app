package com.grocery.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateAcceptingOrdersRequest(

        @Schema(description = "false = pause new orders (e.g. the store is too busy)", example = "false")
        @NotNull
        Boolean acceptingOrders) {
}
