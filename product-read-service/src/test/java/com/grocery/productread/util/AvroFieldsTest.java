package com.grocery.productread.util;

import com.grocery.productread.support.CdcRows;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvroFieldsTest {

    @Test
    void readsDebeziumTypes() {
        GenericRecord row = CdcRows.product(1, 1, 1, "Tomato", "36.00", 3);
        row.put("image_urls", "[\"http://img/1.jpg\", \"http://img/2.jpg\"]");

        assertThat(AvroFields.getLong(row, "id")).isEqualTo(1L);
        assertThat(AvroFields.getDecimal(row, "selling_price")).isEqualByComparingTo("36.00");
        assertThat(AvroFields.getJsonStringList(row, "image_urls")).containsExactly("http://img/1.jpg", "http://img/2.jpg");
        assertThat(AvroFields.getInstant(row, "created_at")).isNotNull();
        assertThat(AvroFields.getString(row, "brand")).isNull();
        assertThat(AvroFields.isDeleted(row)).isFalse();
    }

    @Test
    void readsBooleansSentAsNumbersOrBooleans() {
        assertThat(AvroFields.getBoolean(CdcRows.category(1, "Fruits", true, 0), "active")).isTrue();
        assertThat(AvroFields.getBoolean(CdcRows.category(1, "Fruits", false, 0), "active")).isFalse();

        GenericRecord withRealBoolean = new GenericData.Record(
                SchemaBuilder.record("Row").fields().requiredBoolean("active").endRecord());
        withRealBoolean.put("active", true);
        assertThat(AvroFields.getBoolean(withRealBoolean, "active")).isTrue();
    }

    @Test
    void aFieldThatDoesNotExist_isNull_soNewColumnsNeverBreakTheService() {
        GenericRecord row = CdcRows.category(1, "Fruits", true, 0);

        assertThat(AvroFields.getString(row, "no_such_column")).isNull();
        assertThat(AvroFields.getLong(row, "no_such_column")).isNull();
    }

    @Test
    void recognisesDeletedRows() {
        GenericRecord row = CdcRows.product(1, 1, 1, "Tomato", "36.00", 3);
        row.put("__deleted", "true");

        assertThat(AvroFields.isDeleted(row)).isTrue();
    }
}
