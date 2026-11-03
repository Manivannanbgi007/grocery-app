package com.grocery.productread.util;

import org.apache.avro.generic.GenericRecord;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Reads typed values from a Debezium change row (an Avro GenericRecord).
 *
 * How Debezium sends MySQL columns (see product-cdc-connector.json):
 *   DECIMAL      -> String "45.00"   (decimal.handling.mode = string)
 *   DATETIME     -> Long epoch millis (time.precision.mode = connect)
 *   JSON         -> String '["a.jpg"]'
 *   BOOLEAN      -> 1/0 or true/false (MySQL stores BOOLEAN as TINYINT(1))
 *   __deleted    -> "true" when the row was deleted (ExtractNewRecordState, rewrite mode)
 * A missing field returns null, so new columns in MySQL never break this service.
 */
public final class AvroFields {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private AvroFields() {
    }

    public static String getString(GenericRecord row, String field) {
        Object value = raw(row, field);
        return value == null ? null : value.toString();   // Avro text can be a Utf8 object, not a String
    }

    public static Long getLong(GenericRecord row, String field) {
        Object value = raw(row, field);
        return value == null ? null : ((Number) value).longValue();
    }

    public static Integer getInt(GenericRecord row, String field) {
        Object value = raw(row, field);
        return value == null ? null : ((Number) value).intValue();
    }

    public static BigDecimal getDecimal(GenericRecord row, String field) {
        String value = getString(row, field);
        return value == null ? null : new BigDecimal(value);
    }

    public static Instant getInstant(GenericRecord row, String field) {
        Long epochMillis = getLong(row, field);
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }

    public static boolean getBoolean(GenericRecord row, String field) {
        Object value = raw(row, field);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    public static List<String> getJsonStringList(GenericRecord row, String field) {
        String json = getString(row, field);
        return json == null || json.isBlank() ? List.of() : JSON.readValue(json, STRING_LIST);
    }

    /** true when Debezium reports that the row was deleted from MySQL. */
    public static boolean isDeleted(GenericRecord row) {
        return "true".equals(getString(row, "__deleted"));
    }

    private static Object raw(GenericRecord row, String field) {
        return row.hasField(field) ? row.get(field) : null;
    }
}
