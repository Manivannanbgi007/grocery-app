package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.StockProductData;
import com.grocery.mcpserver.tool.Formats;

/**
 * One product in the list of getVendorLowStock.
 *
 * @param stock  "Sold out" or "Low: 3 left"
 * @param hidden why customers do not see the product at all ("Hidden by the vendor", "Blocked by an admin")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StockLine(String product, String packSize, String stock, String hidden) {

    public static StockLine from(StockProductData product) {
        String stock = product.inStock() && product.availableQuantity() > 0
                ? "Low: " + product.availableQuantity() + " left"
                : "Sold out";
        String hidden = switch (product.status() == null ? "" : product.status()) {
            case "INACTIVE" -> "Hidden by the vendor";
            case "BLOCKED" -> "Blocked by an admin";
            default -> null;
        };
        return new StockLine(product.name(), Formats.packSize(product.unit(), product.unitQuantity()), stock, hidden);
    }
}
