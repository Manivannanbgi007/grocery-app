package com.grocery.productread.config;

import com.grocery.productread.document.CategoryDocument;
import com.grocery.productread.document.ProductDocument;
import com.grocery.productread.document.StockDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Prepares the three search indexes while the application starts, before the Kafka listener runs (afterPropertiesSet
 * is called during start-up; the listener only starts once every bean is ready).
 *
 * The application reads and writes ALIASES ("products", "categories", "product_stock"). Each points to a versioned
 * index ("products_v1"). A change Elasticsearch cannot make in place (a field gets another type) is then done without
 * downtime: build "products_v2" next to it, copy the documents, switch the alias in one step
 * (scripts/reindex-products.ps1, docs/resilience.md "Search indexes").
 *
 * For each index:
 *   - the alias exists: new fields of the mapping file are added (adding fields is always allowed);
 *   - nothing exists (a new machine, the tests, after rebuild-product-index.ps1): "<name>_v1" is created with the
 *     mapping and settings files of resources/elasticsearch, and the alias points to it;
 *   - a plain index with the alias's name exists (made before aliases): it keeps working; a warning says how to move it
 *     behind an alias.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexes implements InitializingBean {

    static final List<Class<?>> DOCUMENTS = List.of(CategoryDocument.class, ProductDocument.class, StockDocument.class);

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void afterPropertiesSet() {
        DOCUMENTS.forEach(this::prepare);
    }

    /** Makes sure the alias of this document class exists and its mapping knows every field. */
    public void prepare(Class<?> documentClass) {
        IndexOperations alias = elasticsearchOperations.indexOps(documentClass);
        String name = alias.getIndexCoordinates().getIndexName();

        if (!alias.exists()) {
            createFirstVersion(alias, name);
            return;
        }
        alias.putMapping();   // new fields of the mapping file; on an alias, Elasticsearch updates the index behind it
        Set<String> indexes = indexesBehind(alias, name);
        if (indexes.isEmpty()) {
            log.warn("'{}' is a plain index made before aliases. It works, but a new version needs downtime: run "
                    + "scripts\\reindex-products.ps1 once to move it behind an alias", name);
        } else {
            log.info("Search index '{}' -> {}", name, indexes);
        }
    }

    private void createFirstVersion(IndexOperations alias, String name) {
        String index = name + "_v1";
        IndexOperations firstVersion = elasticsearchOperations.indexOps(IndexCoordinates.of(index));
        try {
            firstVersion.create(alias.createSettings(), alias.createMapping());
            firstVersion.alias(new AliasActions(new AliasAction.Add(AliasActionParameters.builder()
                    .withIndices(index).withAliases(name).withIsWriteIndex(true).build())));
            log.info("Created the search index '{}' behind the alias '{}'", index, name);
        } catch (RuntimeException ex) {
            // A second instance starting at the same moment may have created it first
            if (!alias.exists()) {
                throw ex;
            }
            log.info("The search index '{}' was just created by another instance", name);
        }
    }

    /** The real indexes the alias points to; empty when "name" is a plain index, not an alias. */
    private static Set<String> indexesBehind(IndexOperations alias, String name) {
        try {
            return alias.getAliases(name).keySet();
        } catch (ResourceNotFoundException ex) {   // Elasticsearch answers 404 "alias [products] missing"
            return Set.of();
        }
    }
}
