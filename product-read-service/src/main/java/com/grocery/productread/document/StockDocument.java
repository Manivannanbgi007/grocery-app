package com.grocery.productread.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;

/**
 * The latest stock of a product, from inventory-service's StockChanged events (index "product_stock").
 *
 * Why a separate index? Stock and product changes arrive independently. If the stock arrives
 * before the product (new product, or an index rebuild), it waits here, and the product picks it up
 * when it is indexed. The stock numbers are also copied onto the ProductDocument, so searches can
 * filter on them.
 *
 * Versioning: "version" is inventory-service's stockVersion (EXTERNAL_GTE, like ProductDocument),
 * so an old or replayed StockChanged never overwrites newer stock. "product_stock" is an alias with a strict
 * mapping, like "products" (see ProductDocument).
 */
@Getter
@Setter
@NoArgsConstructor
@Document(indexName = "product_stock", createIndex = false, versionType = Document.VersionType.EXTERNAL_GTE)
@Mapping(mappingPath = "elasticsearch/product_stock-mapping.json")
@Setting(settingPath = "elasticsearch/index-settings.json")
public class StockDocument {

    /** The product id. */
    @Id
    private Long id;

    @Version
    private Long version;

    @Field(type = FieldType.Long)
    private Long storeId;

    @Field(type = FieldType.Integer)
    private int availableQuantity;

    @Field(type = FieldType.Boolean)
    private boolean lowStock;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;
}
