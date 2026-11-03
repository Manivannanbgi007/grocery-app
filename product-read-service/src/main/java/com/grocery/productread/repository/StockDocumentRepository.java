package com.grocery.productread.repository;

import com.grocery.productread.document.StockDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface StockDocumentRepository extends ElasticsearchRepository<StockDocument, Long> {
}
