package com.grocery.productread.controller;

import com.grocery.productread.constant.HeaderNames;
import com.grocery.productread.dto.common.PageResponse;
import com.grocery.productread.dto.request.ProductSearchFilter;
import com.grocery.productread.enums.ProductSort;
import com.grocery.productread.enums.ProductStatus;
import com.grocery.productread.service.ProductQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PublicProductController.class, VendorProductQueryController.class})
class PublicProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductQueryService productQueryService;

    @Test
    void publicSearch_alwaysAsksForActiveProductsOnly() throws Exception {
        when(productQueryService.searchProducts(any(), any(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/v1/products").param("q", "tomato").param("sort", "PRICE_LOW_TO_HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<ProductSearchFilter> filter = ArgumentCaptor.forClass(ProductSearchFilter.class);
        verify(productQueryService).searchProducts(filter.capture(), eq(ProductSort.PRICE_LOW_TO_HIGH), eq(0), eq(20));
        assertThat(filter.getValue().statuses()).containsExactly(ProductStatus.ACTIVE);
        assertThat(filter.getValue().text()).isEqualTo("tomato");
        assertThat(filter.getValue().inStockOnly()).isFalse();
    }

    @Test
    void publicSearch_canHideOutOfStockProducts() throws Exception {
        when(productQueryService.searchProducts(any(), any(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/v1/products").param("inStockOnly", "true"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductSearchFilter> filter = ArgumentCaptor.forClass(ProductSearchFilter.class);
        verify(productQueryService).searchProducts(filter.capture(), any(), eq(0), eq(20));
        assertThat(filter.getValue().inStockOnly()).isTrue();
        assertThat(filter.getValue().lowStockOnly()).isFalse();
    }

    @Test
    void pageSizeAbove100_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        verifyNoInteractions(productQueryService);
    }

    @Test
    void unknownSortOption_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "CHEAPEST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'CHEAPEST' for parameter 'sort'"));
    }

    @Test
    void suggestions_passTheTypedTextTheCategoryAndTheSize() throws Exception {
        when(productQueryService.suggestProducts("toma", 1L, 4)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products/suggestions").param("q", "toma").param("categoryId", "1").param("size", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
        verify(productQueryService).suggestProducts("toma", 1L, 4);
    }

    @Test
    void suggestions_showSixByDefault_inAllCategories() throws Exception {
        when(productQueryService.suggestProducts("amu", null, 6)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/products/suggestions").param("q", "amu"))
                .andExpect(status().isOk());
        verify(productQueryService).suggestProducts("amu", null, 6);
    }

    @Test
    void suggestions_needSomeText_andAtMostTen() throws Exception {
        mockMvc.perform(get("/api/v1/products/suggestions").param("q", "  "))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/products/suggestions").param("q", "toma").param("size", "11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        verifyNoInteractions(productQueryService);
    }

    @Test
    void vendorList_needsAnApprovedStore() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/products").header(HeaderNames.USER_ID, "10"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(productQueryService);
    }
}
