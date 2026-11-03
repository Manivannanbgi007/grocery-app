package com.grocery.productread.service;

import com.grocery.productread.dto.common.PageResponse;
import com.grocery.productread.dto.request.ProductSearchFilter;
import com.grocery.productread.dto.response.ProductResponse;
import com.grocery.productread.enums.ProductSort;

import java.util.List;

/**
 * Product searches and lookups - all answered from Elasticsearch.
 */
public interface ProductQueryService {

    PageResponse<ProductResponse> searchProducts(ProductSearchFilter filter, ProductSort sort, int page, int size);

    /**
     * Products for the search box's suggestions while the customer types (ACTIVE only, best first): also partial
     * words ("toma" -> Tomato), words in any order and small typos. categoryId may be null (all categories).
     */
    List<ProductResponse> suggestProducts(String text, Long categoryId, int size);

    /** A product customers may see (ACTIVE only). */
    ProductResponse getActiveProduct(Long productId);

    /** A product of the given store, in any status (vendor panel). */
    ProductResponse getStoreProduct(Long storeId, Long productId);

    /** Any product (admin panel and other services). */
    ProductResponse getProduct(Long productId);

    /** Several products in one call, in any status (other services). Unknown ids are simply missing from the result. */
    List<ProductResponse> getProducts(List<Long> productIds);
}
