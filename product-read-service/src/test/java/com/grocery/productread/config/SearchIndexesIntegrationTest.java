package com.grocery.productread.config;

import com.grocery.productread.ElasticsearchTestcontainersConfiguration;
import com.grocery.productread.document.ProductDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasData;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The search indexes on a real Elasticsearch (Testcontainers): aliases created at start-up, strict mappings, and an
 * index made before aliases. Kafka listeners are switched off.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(ElasticsearchTestcontainersConfiguration.class)
@ActiveProfiles("test")
class SearchIndexesIntegrationTest {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private SearchIndexes searchIndexes;

    @Test
    void startUpCreatesAVersionedIndexBehindEachAlias() {
        for (String name : new String[] {"products", "categories", "product_stock"}) {
            Map<String, Set<AliasData>> indexes = elasticsearchOperations.indexOps(IndexCoordinates.of(name)).getAliases(name);

            assertThat(indexes).containsOnlyKeys(name + "_v1");
            assertThat(indexes.get(name + "_v1")).singleElement().satisfies(alias -> assertThat(alias.isWriteIndex()).isTrue());
        }
        // The settings file: one shard, no replica (one local node: the cluster stays green)
        assertThat(elasticsearchOperations.indexOps(IndexCoordinates.of("products_v1")).getSettings().flatten())
                .containsEntry("index.number_of_shards", "1").containsEntry("index.number_of_replicas", "0");
    }

    @Test
    void startingAgainKeepsTheSameIndex() {
        searchIndexes.prepare(ProductDocument.class);

        assertThat(products().getAliases("products")).containsOnlyKeys("products_v1");
    }

    @Test
    void theMappingRefusesAFieldItDoesNotKnow() {
        // A field missing from products-mapping.json (e.g. added to ProductDocument without a line in the file)
        IndexQuery unknownField = new IndexQueryBuilder().withId("999").withSource("{\"id\": 999, \"colour\": \"red\"}").build();

        assertThatThrownBy(() -> elasticsearchOperations.index(unknownField, IndexCoordinates.of("products")))
                .hasStackTraceContaining("strict_dynamic_mapping_exception");
    }

    @Test
    void aPlainIndexFromBeforeAliasesKeepsWorking() {
        IndexOperations products = products();
        elasticsearchOperations.indexOps(IndexCoordinates.of("products_v1")).delete();
        products.createWithMapping();   // what Spring Data made before aliases: a plain index "products"
        try {
            searchIndexes.prepare(ProductDocument.class);   // only a warning: the plain index is used as before

            assertThat(products.exists()).isTrue();
            assertThatThrownBy(() -> products.getAliases("products")).hasMessageContaining("alias [products] missing");
        } finally {
            // Back to the alias for the other tests
            elasticsearchOperations.indexOps(IndexCoordinates.of("products")).delete();
            searchIndexes.prepare(ProductDocument.class);
        }
    }

    private IndexOperations products() {
        return elasticsearchOperations.indexOps(ProductDocument.class);
    }
}
