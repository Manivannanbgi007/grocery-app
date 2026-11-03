package com.grocery.productread.service.impl;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.grocery.productread.document.ProductDocument;
import com.grocery.productread.dto.common.PageResponse;
import com.grocery.productread.dto.request.ProductSearchFilter;
import com.grocery.productread.dto.response.ProductResponse;
import com.grocery.productread.enums.ProductSort;
import com.grocery.productread.enums.ProductStatus;
import com.grocery.productread.exception.ResourceNotFoundException;
import com.grocery.productread.mapper.ProductDocumentMapper;
import com.grocery.productread.repository.ProductDocumentRepository;
import com.grocery.productread.service.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class ProductQueryServiceImpl implements ProductQueryService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductDocumentRepository productRepository;
    private final ProductDocumentMapper productMapper;

    @Override
    public PageResponse<ProductResponse> searchProducts(ProductSearchFilter filter, ProductSort sort, int page, int size) {
        boolean hasText = StringUtils.hasText(filter.text());
        Pageable pageable = PageRequest.of(page, size, sort.toSort(hasText));

        CriteriaQuery query = new CriteriaQuery(buildCriteria(filter), pageable);
        SearchHits<ProductDocument> hits = elasticsearchOperations.search(query, ProductDocument.class);

        List<ProductResponse> products = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(productMapper::toResponse)
                .toList();
        return PageResponse.from(new PageImpl<>(products, pageable, hits.getTotalHits()));
    }

    /**
     * One Elasticsearch query on the existing text fields (no extra mapping, no reindexing). A product is suggested
     * when at least one of these matches, and the more match, the higher it ranks:
     * <ul>
     *   <li>the name contains the typed words, the last one only begun: "toma", "cherry tom" (counts most);</li>
     *   <li>the typed words are in the name, brand or category, in any order, the last one only begun:
     *       "butter amu" -> Amul Butter;</li>
     *   <li>the name or brand with a small typo: "tomatto" -> Tomato (the first letter must be right). It weighs
     *       little, so a typo match never comes before a real one: for "cherry tom", Tomato comes before "Curry".</li>
     * </ul>
     * Ties are sorted by name, so the same text always gives the same list.
     */
    @Override
    public List<ProductResponse> suggestProducts(String text, Long categoryId, int size) {
        String typed = text.trim();
        Query match = Query.of(q -> q.bool(b -> {
            b.filter(f -> f.term(t -> t.field("status").value(ProductStatus.ACTIVE.name())));
            if (categoryId != null) {
                b.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
            }
            return b
                    .should(s -> s.matchPhrasePrefix(m -> m.field("name").query(typed).boost(5f)))
                    .should(s -> s.multiMatch(m -> m.query(typed).type(TextQueryType.BoolPrefix)
                            .fields("name^3", "brand^2", "categoryName^2")))
                    .should(s -> s.multiMatch(m -> m.query(typed).fields("name^3", "brand^2")
                            .fuzziness("AUTO").prefixLength(1).boost(0.3f)))
                    .minimumShouldMatch("1");
        }));
        NativeQuery query = NativeQuery.builder()
                .withQuery(match)
                .withSort(s -> s.score(score -> score.order(SortOrder.Desc)))
                .withSort(s -> s.field(field -> field.field("name.keyword").order(SortOrder.Asc)))
                .withPageable(PageRequest.of(0, size))
                .build();

        return elasticsearchOperations.search(query, ProductDocument.class).getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    public ProductResponse getActiveProduct(Long productId) {
        return findProduct(productId, product -> ProductStatus.ACTIVE.name().equals(product.getStatus()));
    }

    @Override
    public ProductResponse getStoreProduct(Long storeId, Long productId) {
        return findProduct(productId, product -> Objects.equals(product.getStoreId(), storeId));
    }

    @Override
    public ProductResponse getProduct(Long productId) {
        return findProduct(productId, product -> true);
    }

    @Override
    public List<ProductResponse> getProducts(List<Long> productIds) {
        List<ProductResponse> products = new ArrayList<>();
        productRepository.findAllById(productIds).forEach(product -> products.add(productMapper.toResponse(product)));
        return products;
    }

    /**
     * Every filter that is set narrows the result (AND). The search text is matched against several
     * fields (OR): a match in the name counts most, then brand and category, then the description.
     */
    private Criteria buildCriteria(ProductSearchFilter filter) {
        List<String> statuses = filter.statuses().stream().map(Enum::name).toList();
        Criteria criteria = new Criteria("status").in(statuses);

        if (filter.storeId() != null) {
            criteria = criteria.and(new Criteria("storeId").is(filter.storeId()));
        }
        if (filter.categoryId() != null) {
            criteria = criteria.and(new Criteria("categoryId").is(filter.categoryId()));
        }
        if (StringUtils.hasText(filter.brand())) {
            criteria = criteria.and(new Criteria("brand.keyword").is(filter.brand().trim()));
        }
        if (filter.minPrice() != null) {
            criteria = criteria.and(new Criteria("sellingPrice").greaterThanEqual(filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            criteria = criteria.and(new Criteria("sellingPrice").lessThanEqual(filter.maxPrice()));
        }
        if (filter.inStockOnly()) {
            criteria = criteria.and(new Criteria("inStock").is(true));
        }
        if (filter.lowStockOnly()) {
            criteria = criteria.and(new Criteria("lowStock").is(true));
        }
        if (StringUtils.hasText(filter.text())) {
            String text = filter.text().trim();
            criteria = criteria.subCriteria(new Criteria("name").matches(text).boost(3)
                    .or(new Criteria("brand").matches(text).boost(2))
                    .or(new Criteria("categoryName").matches(text).boost(2))
                    .or(new Criteria("description").matches(text)));
        }
        return criteria;
    }

    private ProductResponse findProduct(Long productId, Predicate<ProductDocument> visible) {
        return productRepository.findById(productId)
                .filter(visible)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }
}
