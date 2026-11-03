package com.grocery.productread.mapper;

import com.grocery.productread.document.CategoryDocument;
import com.grocery.productread.document.ProductDocument;
import com.grocery.productread.document.StockDocument;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Component;

import static com.grocery.productread.util.AvroFields.getBoolean;
import static com.grocery.productread.util.AvroFields.getDecimal;
import static com.grocery.productread.util.AvroFields.getInstant;
import static com.grocery.productread.util.AvroFields.getInt;
import static com.grocery.productread.util.AvroFields.getJsonStringList;
import static com.grocery.productread.util.AvroFields.getLong;
import static com.grocery.productread.util.AvroFields.getString;

/**
 * Turns a Debezium change row (column names exactly as in MySQL) or an inventory event
 * into an Elasticsearch document.
 */
@Component
public class CatalogChangeMapper {

    /** @param categoryName looked up from the categories index (the products table only has category_id) */
    public ProductDocument toProductDocument(GenericRecord row, String categoryName) {
        ProductDocument product = new ProductDocument();
        product.setId(getLong(row, "id"));
        product.setVersion(getLong(row, "version"));
        product.setStoreId(getLong(row, "store_id"));
        product.setCategoryId(getLong(row, "category_id"));
        product.setCategoryName(categoryName);
        product.setName(getString(row, "name"));
        product.setDescription(getString(row, "description"));
        product.setBrand(getString(row, "brand"));
        product.setSku(getString(row, "sku"));
        product.setUnit(getString(row, "unit"));
        product.setUnitQuantity(getDecimal(row, "unit_quantity"));
        product.setPrice(getDecimal(row, "price"));
        product.setDiscountPercentage(getDecimal(row, "discount_percentage"));
        product.setSellingPrice(getDecimal(row, "selling_price"));
        product.setTaxPercentage(getDecimal(row, "tax_percentage"));
        product.setImageUrls(getJsonStringList(row, "image_urls"));
        product.setStatus(getString(row, "status"));
        product.setRatingAverage(getDecimal(row, "rating_average"));     // null for rows written before Phase 11
        Integer ratingCount = getInt(row, "rating_count");
        product.setRatingCount(ratingCount == null ? 0 : ratingCount);
        product.setCreatedAt(getInstant(row, "created_at"));
        product.setUpdatedAt(getInstant(row, "updated_at"));
        return product;
    }

    /** StockChanged event (field names from inventory-service's StockChanged.avsc) -> stock document. */
    public StockDocument toStockDocument(GenericRecord stockChanged) {
        StockDocument stock = new StockDocument();
        stock.setId(getLong(stockChanged, "productId"));
        stock.setVersion(getLong(stockChanged, "stockVersion"));
        stock.setStoreId(getLong(stockChanged, "storeId"));
        stock.setAvailableQuantity(getInt(stockChanged, "availableQuantity"));
        stock.setLowStock(getBoolean(stockChanged, "lowStock"));
        stock.setUpdatedAt(getInstant(stockChanged, "occurredAt"));
        return stock;
    }

    /** Copies the stock onto the product. No stock record (null) = nothing to sell yet. */
    public void copyStock(StockDocument stock, ProductDocument product) {
        product.setAvailableQuantity(stock == null ? 0 : stock.getAvailableQuantity());
        product.setInStock(stock != null && stock.getAvailableQuantity() > 0);
        product.setLowStock(stock != null && stock.isLowStock());
    }

    public CategoryDocument toCategoryDocument(GenericRecord row) {
        CategoryDocument category = new CategoryDocument();
        category.setId(getLong(row, "id"));
        category.setVersion(getLong(row, "version"));
        category.setName(getString(row, "name"));
        category.setSlug(getString(row, "slug"));
        category.setDescription(getString(row, "description"));
        category.setImageUrl(getString(row, "image_url"));
        category.setDisplayOrder(getInt(row, "display_order"));
        category.setActive(getBoolean(row, "active"));
        return category;
    }
}
