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
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A product in the Elasticsearch index "products" - the READ model, a copy of product_db.products
 * kept up to date from Debezium change events. MySQL stays the source of truth.
 *
 * Versioning: "version" is the row's version from MySQL (JPA @Version). With EXTERNAL_GTE, Elasticsearch
 * only accepts a write whose version is >= the stored one, so an old or replayed change can never
 * overwrite newer data. Writing the same version again is allowed (e.g. to refresh the category name
 * or the stock, which have their own versions).
 *
 * "products" is an ALIAS of a versioned index ("products_v1"), created by SearchIndexes with the mapping and settings
 * from resources/elasticsearch (createIndex = false: Spring Data does not create it). The mapping is "strict": a field
 * that is not in products-mapping.json is refused, so a new field here needs a line in that file too.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(indexName = "products", createIndex = false, versionType = Document.VersionType.EXTERNAL_GTE)
@Mapping(mappingPath = "elasticsearch/products-mapping.json")
@Setting(settingPath = "elasticsearch/index-settings.json")
public class ProductDocument {

    @Id
    private Long id;

    @Version
    private Long version;

    @Field(type = FieldType.Long)
    private Long storeId;

    @Field(type = FieldType.Long)
    private Long categoryId;

    /** Copied from the category, so products can be found by category name. */
    @Field(type = FieldType.Text)
    private String categoryName;

    /** Full-text search on "name"; sorting on "name.keyword". */
    @MultiField(mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    /** Full-text search on "brand"; exact filter on "brand.keyword". */
    @MultiField(mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
    private String brand;

    @Field(type = FieldType.Keyword)
    private String sku;

    @Field(type = FieldType.Keyword)
    private String unit;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 1000)
    private BigDecimal unitQuantity;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal price;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal discountPercentage;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal sellingPrice;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal taxPercentage;

    @Field(type = FieldType.Keyword)
    private List<String> imageUrls;

    @Field(type = FieldType.Keyword)
    private String status;

    // ----- reviews (product-service keeps them on the products row, Debezium copies them here) -----

    /** Average stars, e.g. 4.3; null without reviews. */
    @Field(type = FieldType.Double)
    private BigDecimal ratingAverage;

    @Field(type = FieldType.Integer)
    private int ratingCount;

    // ----- stock, copied from StockDocument (inventory-service). No stock record yet = 0 / not in stock. -----

    @Field(type = FieldType.Integer)
    private int availableQuantity;

    @Field(type = FieldType.Boolean)
    private boolean inStock;

    @Field(type = FieldType.Boolean)
    private boolean lowStock;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;
}
