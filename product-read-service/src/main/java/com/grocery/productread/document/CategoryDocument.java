package com.grocery.productread.document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

/**
 * A category in the Elasticsearch index "categories" - a copy of product_db.categories.
 * Versioned like ProductDocument (old changes never overwrite newer ones). "categories" is an alias with a strict
 * mapping, like "products" (see ProductDocument).
 */
@Getter
@Setter
@NoArgsConstructor
@Document(indexName = "categories", createIndex = false, versionType = Document.VersionType.EXTERNAL_GTE)
@Mapping(mappingPath = "elasticsearch/categories-mapping.json")
@Setting(settingPath = "elasticsearch/index-settings.json")
public class CategoryDocument {

    @Id
    private Long id;

    @Version
    private Long version;

    @Field(type = FieldType.Keyword)
    private String name;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword, index = false)
    private String imageUrl;

    @Field(type = FieldType.Integer)
    private Integer displayOrder;

    @Field(type = FieldType.Boolean)
    private boolean active;
}
