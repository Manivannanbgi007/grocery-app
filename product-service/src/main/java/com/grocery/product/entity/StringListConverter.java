package com.grocery.product.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a List&lt;String&gt; in a single JSON column, e.g. ["/media/products/1/a.jpg", "/media/products/1/b.jpg"].
 * Keeping the image list on the product row means Debezium sends it together with the product.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        return JSON.writeValueAsString(values == null ? List.of() : values);
    }

    @Override
    public List<String> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(JSON.readValue(json, STRING_LIST));
    }
}
