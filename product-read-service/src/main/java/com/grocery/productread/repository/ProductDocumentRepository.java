package com.grocery.productread.repository;

import com.grocery.productread.document.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Simple lookups in the "products" index. Searching with filters is done with
 * ElasticsearchOperations in ProductQueryServiceImpl.
 */
public interface ProductDocumentRepository extends ElasticsearchRepository<ProductDocument, Long> {

    Page<ProductDocument> findByCategoryId(Long categoryId, Pageable pageable);
}
