package com.grocery.productread.repository;

import com.grocery.productread.document.CategoryDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface CategoryDocumentRepository extends ElasticsearchRepository<CategoryDocument, Long> {

    List<CategoryDocument> findByActiveTrue(Sort sort);
}
