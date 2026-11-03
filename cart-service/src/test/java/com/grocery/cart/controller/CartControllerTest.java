package com.grocery.cart.controller;

import com.grocery.cart.constant.HeaderNames;
import com.grocery.cart.exception.CartStoreConflictException;
import com.grocery.cart.service.CartService;
import com.grocery.cart.service.CheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;
    @MockitoBean
    private CheckoutService checkoutService;

    @Test
    void addItem_returns400WithFieldErrors_forInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": -1, "quantity": 25 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("productId", "quantity")));

        verifyNoInteractions(cartService);
    }

    @Test
    void addItem_productOfAnotherStore_returns409() throws Exception {
        when(cartService.addItem(eq(4L), any(), eq(false)))
                .thenThrow(new CartStoreConflictException("Your cart has items from another store."));

        mockMvc.perform(post("/api/v1/cart/items").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": 11, "quantity": 1 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void addItem_passesReplaceCart() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items?replaceCart=true").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": 11, "quantity": 1 }
                                """))
                .andExpect(status().isOk());

        verify(cartService).addItem(eq(4L), any(), eq(true));
    }

    @Test
    void applyCoupon_rejectsCodesWithSymbols() throws Exception {
        mockMvc.perform(put("/api/v1/cart/coupon").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "SAVE-10%" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }

    @Test
    void cart_needsTheGatewayUserHeader() throws Exception {
        mockMvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
    }
}
