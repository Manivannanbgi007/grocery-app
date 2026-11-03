package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.tool.Formats;

/**
 * Everything about one product (getProductDetails): the summary fields plus the description.
 *
 * @param priceNote "Price for 500 g, including GST", so the model never adds tax on top
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetails(
        long productId,
        String name,
        String brand,
        String description,
        String packSize,
        String price,
        String priceBeforeDiscount,
        String discount,
        String priceNote,
        String stock,
        String store,
        String category,
        String rating) {

    public static ProductDetails from(ProductData product, String storeName) {
        String packSize = Formats.packSize(product.unit(), product.unitQuantity());
        return new ProductDetails(
                product.id(),
                product.name(),
                product.brand(),
                product.description(),
                packSize,
                Formats.rupees(product.sellingPrice()),
                ProductTexts.priceBeforeDiscount(product),
                ProductTexts.discount(product),
                "Price for " + packSize + ", including GST",
                ProductTexts.stock(product),
                storeName,
                product.categoryName(),
                ProductTexts.rating(product));
    }
}
