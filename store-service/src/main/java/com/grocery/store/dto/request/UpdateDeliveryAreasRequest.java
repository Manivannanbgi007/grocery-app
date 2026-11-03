package com.grocery.store.dto.request;

import com.grocery.store.constant.ValidationPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Replaces the list of PIN codes the store delivers to.
 */
public record UpdateDeliveryAreasRequest(

        @Schema(example = "[\"560038\", \"560008\", \"560071\"]")
        @NotEmpty @Size(max = 50)
        List<@Pattern(regexp = ValidationPatterns.PINCODE, message = ValidationPatterns.PINCODE_MESSAGE) String> pincodes) {
}
