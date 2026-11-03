package com.grocery.productread.service;

import com.grocery.productread.ElasticsearchTestcontainersConfiguration;
import com.grocery.productread.config.SearchIndexes;
import com.grocery.productread.document.CategoryDocument;
import com.grocery.productread.document.ProductDocument;
import com.grocery.productread.document.StockDocument;
import com.grocery.productread.dto.common.PageResponse;
import com.grocery.productread.dto.request.ProductSearchFilter;
import com.grocery.productread.dto.response.ProductResponse;
import com.grocery.productread.enums.ProductSort;
import com.grocery.productread.enums.ProductStatus;
import com.grocery.productread.repository.CategoryDocumentRepository;
import com.grocery.productread.repository.ProductDocumentRepository;
import com.grocery.productread.support.CdcRows;
import com.grocery.productread.support.InventoryEvents;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies change rows to a real Elasticsearch (Testcontainers) and searches them.
 * Kafka listeners are switched off: this test calls the services directly.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(ElasticsearchTestcontainersConfiguration.class)
@ActiveProfiles("test")
class CatalogIndexAndSearchIntegrationTest {

    private static final Set<ProductStatus> ACTIVE_ONLY = EnumSet.of(ProductStatus.ACTIVE);

    @Autowired
    private CatalogIndexService catalogIndexService;
    @Autowired
    private ProductQueryService productQueryService;
    @Autowired
    private ProductDocumentRepository productRepository;
    @Autowired
    private CategoryDocumentRepository categoryRepository;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private SearchIndexes searchIndexes;

    @BeforeEach
    void setUp() {
        // Start every test with brand-new indexes. (Deleting documents is not enough: Elasticsearch
        // remembers the version of deleted documents for 60 seconds and would refuse version 0 again.)
        recreateIndex(ProductDocument.class);
        recreateIndex(CategoryDocument.class);
        recreateIndex(StockDocument.class);

        catalogIndexService.applyCategoryChange(CdcRows.category(1, "Vegetables", true, 0));
        catalogIndexService.applyCategoryChange(CdcRows.category(3, "Dairy & Eggs", true, 0));

        catalogIndexService.applyProductChange(CdcRows.product(1, 1, 1, "Tomato", "36.00", 0));
        catalogIndexService.applyProductChange(CdcRows.product(2, 1, 1, "Potato", "28.50", 0));
        catalogIndexService.applyProductChange(CdcRows.product(3, 2, 1, "Cherry Tomato", "45.00", 0));
        GenericRecord butter = CdcRows.product(4, 1, 3, "Butter", "56.00", 0);
        butter.put("brand", "Amul");
        catalogIndexService.applyProductChange(butter);
        GenericRecord mango = CdcRows.product(5, 1, 1, "Mango", "350.00", 0);
        mango.put("status", "INACTIVE");
        catalogIndexService.applyProductChange(mango);
        refresh();
    }

    // ===== Indexing: idempotent, ordered, rebuildable =====

    @Test
    void olderChange_neverOverwritesANewerOne() {
        GenericRecord version2 = CdcRows.product(1, 1, 1, "Tomato (Hybrid)", "40.00", 2);
        GenericRecord version1 = CdcRows.product(1, 1, 1, "Tomato (old name)", "30.00", 1);

        catalogIndexService.applyProductChange(version2);
        catalogIndexService.applyProductChange(version1);   // arrives late, e.g. after a replay
        catalogIndexService.applyProductChange(version2);   // a duplicate of the newest change is fine

        ProductDocument stored = productRepository.findById(1L).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Tomato (Hybrid)");
        assertThat(stored.getSellingPrice()).isEqualByComparingTo("40.00");
    }

    @Test
    void renamingACategory_updatesTheCategoryNameOnItsProducts_keepingTheirVersion() {
        assertThat(productRepository.findById(2L).orElseThrow().getCategoryName()).isEqualTo("Vegetables");

        catalogIndexService.applyCategoryChange(CdcRows.category(1, "Fresh Vegetables", true, 1));

        ProductDocument potato = productRepository.findById(2L).orElseThrow();
        assertThat(potato.getCategoryName()).isEqualTo("Fresh Vegetables");
        assertThat(potato.getVersion()).isZero();

        // ...and a later real change of the product (version 1) is still accepted
        catalogIndexService.applyProductChange(CdcRows.product(2, 1, 1, "Potato", "25.00", 1));
        assertThat(productRepository.findById(2L).orElseThrow().getSellingPrice()).isEqualByComparingTo("25.00");
    }

    @Test
    void productThatArrivesBeforeItsCategory_getsTheNameWhenTheCategoryArrives() {
        catalogIndexService.applyProductChange(CdcRows.product(20, 1, 9, "Bread", "45.00", 0));
        assertThat(productRepository.findById(20L).orElseThrow().getCategoryName()).isNull();

        catalogIndexService.applyCategoryChange(CdcRows.category(9, "Bakery", true, 0));
        refresh();

        assertThat(productRepository.findById(20L).orElseThrow().getCategoryName()).isEqualTo("Bakery");
    }

    @Test
    void deletedProducts_leaveTheIndex() {
        GenericRecord softDeleted = CdcRows.product(2, 1, 1, "Potato", "28.50", 1);
        softDeleted.put("status", "DELETED");
        GenericRecord hardDeleted = CdcRows.product(3, 2, 1, "Cherry Tomato", "45.00", 1);
        hardDeleted.put("__deleted", "true");

        catalogIndexService.applyProductChange(softDeleted);
        catalogIndexService.applyProductChange(hardDeleted);

        assertThat(productRepository.findById(2L)).isEmpty();
        assertThat(productRepository.findById(3L)).isEmpty();
    }

    // ===== Stock (StockChanged from inventory-service) =====

    @Test
    void productWithoutAStockRecord_isNotInStock() {
        ProductDocument potato = productRepository.findById(2L).orElseThrow();

        assertThat(potato.getAvailableQuantity()).isZero();
        assertThat(potato.isInStock()).isFalse();
    }

    @Test
    void stockChange_isCopiedOntoTheProduct_keepingTheProductVersion() {
        catalogIndexService.applyStockChange(InventoryEvents.stockChanged(1, 1, 40, 3));

        ProductDocument tomato = productRepository.findById(1L).orElseThrow();
        assertThat(tomato.getAvailableQuantity()).isEqualTo(40);
        assertThat(tomato.isInStock()).isTrue();
        assertThat(tomato.isLowStock()).isFalse();
        assertThat(tomato.getVersion()).isZero();

        // ...and the next real change of the product keeps the stock
        catalogIndexService.applyProductChange(CdcRows.product(1, 1, 1, "Tomato", "30.00", 1));
        assertThat(productRepository.findById(1L).orElseThrow().getAvailableQuantity()).isEqualTo(40);
    }

    @Test
    void olderStockChange_neverOverwritesANewerOne() {
        catalogIndexService.applyStockChange(InventoryEvents.stockChanged(1, 1, 0, 5));
        catalogIndexService.applyStockChange(InventoryEvents.stockChanged(1, 1, 40, 4));   // arrives late

        ProductDocument tomato = productRepository.findById(1L).orElseThrow();
        assertThat(tomato.isInStock()).isFalse();
        assertThat(tomato.isLowStock()).isTrue();
    }

    @Test
    void stockThatArrivesBeforeItsProduct_isUsedWhenTheProductArrives() {
        catalogIndexService.applyStockChange(InventoryEvents.stockChanged(30, 1, 12, 0));
        assertThat(productRepository.findById(30L)).isEmpty();

        catalogIndexService.applyProductChange(CdcRows.product(30, 1, 1, "Carrot", "40.00", 0));

        assertThat(productRepository.findById(30L).orElseThrow().getAvailableQuantity()).isEqualTo(12);
    }

    @Test
    void inStockOnly_hidesProductsWithNothingAvailable() {
        catalogIndexService.applyStockChange(InventoryEvents.stockChanged(1, 1, 40, 0));   // Tomato: in stock
        catalogIndexService.applyStockChange(InventoryEvents.stockChanged(3, 2, 0, 0));    // Cherry Tomato: sold out
        refresh();

        PageResponse<ProductResponse> result = productQueryService.searchProducts(
                new ProductSearchFilter("tomato", null, null, null, null, null, ACTIVE_ONLY, true, false),
                ProductSort.RELEVANCE, 0, 20);

        assertThat(names(result)).containsExactly("Tomato");
        assertThat(result.content().getFirst().availableQuantity()).isEqualTo(40);
    }

    @Test
    void lowStockOnly_findsTheProductsThatNeedRestocking() {
        catalogIndexService.applyStockChange(InventoryEvents.stockChanged(1, 1, 40, 0));   // Tomato: plenty
        catalogIndexService.applyStockChange(InventoryEvents.stockChanged(2, 1, 3, 0));    // Potato: low
        catalogIndexService.applyStockChange(InventoryEvents.stockChanged(4, 1, 0, 0));    // Butter: empty = low too
        refresh();

        PageResponse<ProductResponse> result = productQueryService.searchProducts(
                new ProductSearchFilter(null, null, 1L, null, null, null, ACTIVE_ONLY, false, true),
                ProductSort.NAME, 0, 20);

        assertThat(names(result)).containsExactly("Butter", "Potato");
    }

    // ===== Searching =====

    @Test
    void searchText_matchesWholeWordsInTheName() {
        assertThat(names(search("tomato", null, null, ProductSort.RELEVANCE)))
                .containsExactlyInAnyOrder("Tomato", "Cherry Tomato");
    }

    @Test
    void searchText_alsoMatchesBrandAndCategoryName() {
        assertThat(names(search("amul", null, null, ProductSort.RELEVANCE))).containsExactly("Butter");
        assertThat(names(search("dairy", null, null, ProductSort.RELEVANCE))).containsExactly("Butter");
    }

    @Test
    void customersNeverSeeInactiveProducts() {
        assertThat(names(search("mango", null, null, ProductSort.RELEVANCE))).isEmpty();
    }

    @Test
    void filtersByStoreCategoryAndPrice_andSortsByPrice() {
        PageResponse<ProductResponse> result = productQueryService.searchProducts(
                new ProductSearchFilter(null, 1L, 1L, null, new BigDecimal("20"), new BigDecimal("40"), ACTIVE_ONLY, false, false),
                ProductSort.PRICE_LOW_TO_HIGH, 0, 20);

        assertThat(names(result)).containsExactly("Potato", "Tomato");
        assertThat(result.content().getFirst().sellingPrice()).isEqualByComparingTo("28.50");
    }

    @Test
    void vendorSeesAllStatusesOfTheirOwnStoreOnly() {
        PageResponse<ProductResponse> result = productQueryService.searchProducts(
                new ProductSearchFilter(null, null, 1L, null, null, null,
                        EnumSet.of(ProductStatus.ACTIVE, ProductStatus.INACTIVE, ProductStatus.BLOCKED), false, false),
                ProductSort.NAME, 0, 20);

        assertThat(names(result)).containsExactly("Butter", "Mango", "Potato", "Tomato");
    }

    @Test
    void pagesThroughResults() {
        PageResponse<ProductResponse> firstPage = productQueryService.searchProducts(
                new ProductSearchFilter(null, null, null, null, null, null, ACTIVE_ONLY, false, false), ProductSort.NAME, 0, 2);

        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(names(firstPage)).containsExactly("Butter", "Cherry Tomato");
    }

    // ===== Suggestions while typing =====

    @Test
    void suggestions_findPartlyTypedWords_shortestNameFirst() {
        assertThat(suggest("toma", null, 6)).containsExactly("Tomato", "Cherry Tomato");
        assertThat(suggest("toma", null, 1)).containsExactly("Tomato");
    }

    @Test
    void suggestions_putTheNameWithAllTypedWordsFirst() {
        assertThat(suggest("cherry tom", null, 6)).first().isEqualTo("Cherry Tomato");
    }

    @Test
    void suggestions_alsoMatchTheBeginningOfBrandAndCategory_inAnyOrder() {
        assertThat(suggest("amu", null, 6)).containsExactly("Butter");
        assertThat(suggest("butter amu", null, 6)).containsExactly("Butter");
        assertThat(suggest("amu but", null, 6)).containsExactly("Butter"); // "amu" is a small typo of "amul"
        assertThat(suggest("dai", null, 6)).containsExactly("Butter");
    }

    @Test
    void suggestions_forgiveASmallTypo() {
        assertThat(suggest("tomatto", null, 6)).first().isEqualTo("Tomato");
        assertThat(suggest("potatoe", null, 6)).containsExactly("Potato");
    }

    @Test
    void suggestions_putATypoMatchAfterTheRealMatches() {
        // "tom" really begins Tomato and Cherry Tomato; "potatoe" is only a typo of Potato
        assertThat(suggest("potatoe tom", null, 6)).hasSize(3).last().isEqualTo("Potato");
    }

    @Test
    void suggestions_stayInTheChosenCategory_andNeverShowInactiveProducts() {
        assertThat(suggest("pot", 1L, 6)).containsExactly("Potato");
        assertThat(suggest("pot", 3L, 6)).isEmpty();
        assertThat(suggest("man", null, 6)).isEmpty();
    }

    @Test
    void getProducts_returnsTheKnownProductsInAnyStatus_andSkipsUnknownIds() {
        assertThat(productQueryService.getProducts(java.util.List.of(1L, 5L, 999L)))
                .extracting(ProductResponse::name)
                .containsExactlyInAnyOrder("Tomato", "Mango");
    }

    private PageResponse<ProductResponse> search(String text, Long storeId, Long categoryId, ProductSort sort) {
        return productQueryService.searchProducts(
                new ProductSearchFilter(text, categoryId, storeId, null, null, null, ACTIVE_ONLY, false, false), sort, 0, 20);
    }

    private java.util.List<String> names(PageResponse<ProductResponse> page) {
        return page.content().stream().map(ProductResponse::name).toList();
    }

    private java.util.List<String> suggest(String text, Long categoryId, int size) {
        return productQueryService.suggestProducts(text, categoryId, size).stream().map(ProductResponse::name).toList();
    }

    /** Elasticsearch makes new documents searchable about once a second; tests force it now. */
    private void refresh() {
        elasticsearchOperations.indexOps(ProductDocument.class).refresh();
    }

    /** The name is an alias (SearchIndexes): delete the real index behind it, then let SearchIndexes create it again. */
    private void recreateIndex(Class<?> documentClass) {
        IndexOperations alias = elasticsearchOperations.indexOps(documentClass);
        String name = alias.getIndexCoordinates().getIndexName();
        alias.getAliases(name).keySet().forEach(index -> elasticsearchOperations.indexOps(IndexCoordinates.of(index)).delete());
        searchIndexes.prepare(documentClass);
    }
}
