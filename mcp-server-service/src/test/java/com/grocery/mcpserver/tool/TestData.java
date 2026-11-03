package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.dto.CartData;
import com.grocery.mcpserver.client.dto.CategoryData;
import com.grocery.mcpserver.client.dto.CouponData;
import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.client.dto.StoreData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Small, realistic gateway answers for the tests (the values of the local seed data). */
public final class TestData {

    private TestData() {
    }

    public static ProductData amulButter() {
        return new ProductData(7L, 1L, 3L, "Dairy & Eggs", "Amul Butter", "Pasteurised salted butter", "Amul", "G",
                new BigDecimal("100.000"), new BigDecimal("56.00"), BigDecimal.ZERO, new BigDecimal("56.00"),
                3, true, true, new BigDecimal("4.5"), 2);
    }

    public static ProductData tomato() {
        return new ProductData(1L, 1L, 1L, "Vegetables", "Tomato", "Farm fresh red tomatoes", null, "KG",
                new BigDecimal("1.000"), new BigDecimal("40.00"), new BigDecimal("10.00"), new BigDecimal("36.00"),
                120, true, false, null, 0);
    }

    public static ProductData pomegranate() {
        return new ProductData(13L, 2L, 2L, "Fruits", "Pomegranate", "Bhagwa pomegranates", null, "KG",
                new BigDecimal("1.000"), new BigDecimal("200.00"), new BigDecimal("20.00"), new BigDecimal("160.00"),
                0, false, true, null, 0);
    }

    /** The same name as amulButter(), a bigger pack */
    public static ProductData amulButter500() {
        return new ProductData(8L, 1L, 3L, "Dairy & Eggs", "Amul Butter", "Pasteurised salted butter", "Amul", "G",
                new BigDecimal("500.000"), new BigDecimal("275.00"), BigDecimal.ZERO, new BigDecimal("275.00"),
                10, true, false, null, 0);
    }

    public static ProductData amulTaazaMilk() {
        return new ProductData(6L, 1L, 3L, "Dairy & Eggs", "Amul Taaza Toned Milk", "Toned milk, 3% fat", "Amul", "ML",
                new BigDecimal("500.000"), new BigDecimal("27.00"), BigDecimal.ZERO, new BigDecimal("27.00"),
                40, true, false, null, 0);
    }

    public static ProductData eggs() {
        return new ProductData(20L, 1L, 3L, "Dairy & Eggs", "Eggs", "Farm fresh white eggs", null, "PIECE",
                new BigDecimal("6.000"), new BigDecimal("48.00"), BigDecimal.ZERO, new BigDecimal("48.00"),
                30, true, false, null, 0);
    }

    /** Tomato of the other store (Green Basket) */
    public static ProductData greenBasketTomato() {
        return new ProductData(51L, 2L, 1L, "Vegetables", "Tomato", "Organic tomatoes", null, "KG",
                new BigDecimal("1.000"), new BigDecimal("40.00"), BigDecimal.ZERO, new BigDecimal("40.00"),
                25, true, false, null, 0);
    }

    /** Has "tomato" in its name too: the search box suggests it for "tomoto" */
    public static ProductData tomatoKetchup() {
        return new ProductData(30L, 1L, 4L, "Snacks", "Kissan Fresh Tomato Ketchup", "Tomato ketchup", "Kissan", "G",
                new BigDecimal("500.000"), new BigDecimal("120.00"), BigDecimal.ZERO, new BigDecimal("120.00"),
                15, true, false, null, 0);
    }

    /** GET /api/v1/cart of a customer whose cart is empty */
    public static CartData emptyCart() {
        return new CartData(null, List.of(), null, null, new CartData.Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("99"), false));
    }

    /** A cart with packs of one product; the delivery fee is 30 */
    public static CartData cartWith(ProductData product, int packs) {
        return cartWith(product.storeId(), List.of(line(product, packs)));
    }

    /** A cart with these lines (all of one store); the delivery fee is 30 */
    public static CartData cartWith(long storeId, List<CartData.Item> lines) {
        BigDecimal itemsTotal = lines.stream().map(CartData.Item::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryFee = new BigDecimal("30.00");
        return new CartData(storeId, lines, null, null, new CartData.Totals(itemsTotal, BigDecimal.ZERO, BigDecimal.ZERO,
                deliveryFee, itemsTotal.add(deliveryFee), new BigDecimal("99"), itemsTotal.compareTo(new BigDecimal("99")) >= 0));
    }

    public static CartData.Item line(ProductData product, int packs) {
        return new CartData.Item(product.id(), product.name(), product.unit(), product.unitQuantity(), product.sellingPrice(),
                packs, product.sellingPrice().multiply(BigDecimal.valueOf(packs)));
    }

    public static List<CategoryData> categories() {
        return List.of(
                new CategoryData(1L, "Vegetables", "vegetables", "Fresh vegetables delivered daily"),
                new CategoryData(2L, "Fruits", "fruits", "Seasonal and exotic fruits"),
                new CategoryData(3L, "Dairy & Eggs", "dairy-eggs", "Milk, curd, butter, cheese, eggs"));
    }

    public static List<StoreData> stores() {
        return List.of(
                new StoreData(1L, "Fresh Mart", "Fresh vegetables, fruits, dairy", "9000000003", "45, 100 Feet Road", null,
                        "Bengaluru", "560038", true, true, List.of()),
                new StoreData(2L, "Green Basket", "Organic produce", "9000000006", "12, 80 Feet Road", null,
                        "Bengaluru", "560034", true, false, List.of()));
    }

    public static CouponData save10() {
        return new CouponData("SAVE10", "10% off (up to 100) on orders of 199 or more", null, "PERCENTAGE",
                new BigDecimal("10.00"), new BigDecimal("100.00"), new BigDecimal("199.00"), Instant.parse("2027-12-31T12:00:00Z"));
    }

    public static CouponData fresh20() {
        return new CouponData("FRESH20", "Fresh Mart: 20% off (up to 60) on 249 or more", 1L, "PERCENTAGE",
                new BigDecimal("20.00"), new BigDecimal("60.00"), new BigDecimal("249.00"), Instant.parse("2027-12-31T12:00:00Z"));
    }

    public static CouponData green30() {
        return new CouponData("GREEN30", "Green Basket: flat 30 off on 199 or more", 2L, "FLAT",
                new BigDecimal("30.00"), null, new BigDecimal("199.00"), Instant.parse("2027-12-31T12:00:00Z"));
    }
}
