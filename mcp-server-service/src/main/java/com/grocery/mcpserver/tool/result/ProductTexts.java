package com.grocery.mcpserver.tool.result;

import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.tool.Formats;

/**
 * The short texts about a product that several results share (stock, rating, discount).
 * Written as plain sentences, so the model can repeat them without interpreting numbers.
 */
final class ProductTexts {

    private ProductTexts() {
    }

    /** "In stock", "Only 3 left" or "Out of stock" */
    static String stock(ProductData product) {
        if (!product.inStock()) {
            return "Out of stock";
        }
        return product.lowStock() ? "Only " + product.availableQuantity() + " left" : "In stock";
    }

    /** "4.5 out of 5 (2 reviews)" or "No reviews yet" */
    static String rating(ProductData product) {
        if (product.ratingAverage() == null || product.ratingCount() == 0) {
            return "No reviews yet";
        }
        String reviews = product.ratingCount() == 1 ? "1 review" : product.ratingCount() + " reviews";
        return product.ratingAverage().stripTrailingZeros().toPlainString() + " out of 5 (" + reviews + ")";
    }

    static boolean discounted(ProductData product) {
        return product.price() != null && product.sellingPrice() != null && product.sellingPrice().compareTo(product.price()) < 0;
    }

    /** The price before the discount ("₹40"), or null without a discount */
    static String priceBeforeDiscount(ProductData product) {
        return discounted(product) ? Formats.rupees(product.price()) : null;
    }

    /** "10% off", or null without a discount */
    static String discount(ProductData product) {
        return discounted(product) ? Formats.percent(product.discountPercentage()) + " off" : null;
    }
}
