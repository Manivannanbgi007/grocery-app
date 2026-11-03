package com.grocery.report.dto.response;

import java.math.BigDecimal;

public record TopStoreResponse(Long storeId, String storeName, long orders, BigDecimal sales) {
}
