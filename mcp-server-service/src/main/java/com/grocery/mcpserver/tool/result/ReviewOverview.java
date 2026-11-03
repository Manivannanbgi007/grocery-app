package com.grocery.mcpserver.tool.result;

import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.client.dto.ReviewData;
import com.grocery.mcpserver.tool.Formats;

import java.util.List;

/**
 * What customers say about a product (getProductReviews): the overall rating and the newest reviews.
 * Reviewers' names are left out: the assistant does not need to repeat people's names.
 *
 * @param rating        "4.5 out of 5 (2 reviews)" or "No reviews yet"
 * @param latestReviews newest first, at most 3
 */
public record ReviewOverview(long productId, String productName, String rating, List<Review> latestReviews) {

    /**
     * @param stars   1 to 5
     * @param comment may be null (a rating without text)
     * @param date    e.g. "26 Oct 2026"
     */
    public record Review(int stars, String comment, String date) {
    }

    public static ReviewOverview from(ProductData product, List<ReviewData> reviews) {
        List<Review> latest = reviews.stream()
                .map(review -> new Review(review.rating(), review.comment(), Formats.date(review.createdAt())))
                .toList();
        return new ReviewOverview(product.id(), product.name(), ProductTexts.rating(product), latest);
    }
}
