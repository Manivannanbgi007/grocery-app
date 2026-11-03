package com.grocery.productread.enums;

import org.springframework.data.domain.Sort;

/**
 * Sort options offered to clients (simpler and safer than letting them sort by any field).
 */
public enum ProductSort {

    /** Best matches first. Only meaningful with a search text; without one, sorted by name. */
    RELEVANCE,
    PRICE_LOW_TO_HIGH,
    PRICE_HIGH_TO_LOW,
    NAME,
    NEWEST,
    /** Best rated first; products without reviews come last. */
    RATING;

    public Sort toSort(boolean hasSearchText) {
        Sort sort = switch (this) {
            case RELEVANCE -> hasSearchText ? Sort.unsorted() : Sort.by("name.keyword");
            case PRICE_LOW_TO_HIGH -> Sort.by(Sort.Direction.ASC, "sellingPrice");
            case PRICE_HIGH_TO_LOW -> Sort.by(Sort.Direction.DESC, "sellingPrice");
            case NAME -> Sort.by("name.keyword");
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case RATING -> Sort.by(Sort.Order.desc("ratingAverage").nullsLast(), Sort.Order.desc("ratingCount"));
        };
        // Same-price products keep a stable order between pages
        return sort.isSorted() ? sort.and(Sort.by("id")) : sort;
    }
}
