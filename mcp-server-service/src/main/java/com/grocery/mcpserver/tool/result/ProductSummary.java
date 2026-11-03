package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.tool.Formats;

/**
 * One product in a search result. Prices are texts ("₹36"), copied as they are into the answer.
 *
 * @param productId           for getProductDetails / getProductReviews
 * @param price               what the customer pays, e.g. "₹36"
 * @param priceBeforeDiscount only when there is a discount, e.g. "₹40"
 * @param discount            only when there is a discount, e.g. "10% off"
 * @param stock               "In stock", "Only 3 left" or "Out of stock"
 * @param store               the store that sells it (a cart holds products of one store only)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductSummary(
        long productId,
        String name,
        String brand,
        String packSize,
        String price,
        String priceBeforeDiscount,
        String discount,
        String stock,
        String store,
        String category,
        String rating) {

    public static ProductSummary from(ProductData product, String storeName) {
        return new ProductSummary(
                product.id(),
                product.name(),
                product.brand(),
                Formats.packSize(product.unit(), product.unitQuantity()),
                Formats.rupees(product.sellingPrice()),
                ProductTexts.priceBeforeDiscount(product),
                ProductTexts.discount(product),
                ProductTexts.stock(product),
                storeName,
                product.categoryName(),
                ProductTexts.rating(product));
    }
}
