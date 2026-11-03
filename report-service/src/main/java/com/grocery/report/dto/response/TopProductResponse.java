package com.grocery.report.dto.response;

import java.math.BigDecimal;

public record TopProductResponse(Long productId, String productName, long quantity, BigDecimal sales) {
}
