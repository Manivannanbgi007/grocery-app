package com.grocery.delivery.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ZoneResponse(Long id, String name, String city, List<String> pincodes, BigDecimal partnerPayout, boolean active) {
}
