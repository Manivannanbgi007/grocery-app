package com.grocery.mcpclient.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A page of the website that fits the answer, e.g. ("Your wishlist", "/wishlist") or ("Amul Butter", "/products/12").
 * Made by AnswerLinks from the tool results, never by the model. The website checks the path again before it
 * shows the link.
 *
 * @param label the link's text
 * @param path  a path of the website (starts with "/"), never a full address
 */
@Schema(description = "A page of the website that fits the answer")
public record ChatLink(
        @Schema(description = "The link's text", example = "Your wishlist") String label,
        @Schema(description = "A path of the website", example = "/wishlist") String path) {
}
