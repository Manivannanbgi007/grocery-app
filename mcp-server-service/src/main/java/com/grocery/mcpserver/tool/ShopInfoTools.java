package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.CouponData;
import com.grocery.mcpserver.client.dto.StoreData;
import com.grocery.mcpserver.tool.result.DeliveryRules;
import com.grocery.mcpserver.tool.result.OfferInfo;
import com.grocery.mcpserver.tool.result.StoreInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The shop information tools: offers (coupons), delivery rules, stores. Read-only, live data through the API gateway.
 */
@Component
@RequiredArgsConstructor
public class ShopInfoTools {

    private final ShopInfoClient shopInfo;
    private final ToolRunner runner;

    @McpTool(name = "listCurrentOffers",
            description = """
                    The coupons customers can use now: the code to type in the cart, the discount, the minimum order, \
                    the last day and in which stores it works. Optionally only the coupons that work in one store.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<OfferInfo>> listCurrentOffers(
            @McpToolParam(description = "Only coupons that work in this store, e.g. \"Fresh Mart\" (see listStores)", required = false) String storeName) {
        return runner.run("listCurrentOffers", () -> {
            String name = ToolInputs.clean(storeName);
            if (name != null && !ToolInputs.isUsableText(name)) {
                return ToolResult.invalidInput("storeName must have letters and at most " + ToolInputs.MAX_TEXT_LENGTH + " characters.");
            }
            List<StoreData> stores = shopInfo.stores();
            Map<Long, String> storeNames = stores.stream().collect(Collectors.toMap(StoreData::id, StoreData::name));

            // The same coupon comes back for several stores (platform coupons): keep each code once, in order
            Map<String, CouponData> coupons = new LinkedHashMap<>();
            if (name != null) {
                Optional<StoreData> store = stores.stream().filter(candidate -> ToolInputs.looselyMatches(name, candidate.name())).findFirst();
                if (store.isEmpty()) {
                    return ToolResult.notFound("There is no store called \"" + name + "\". The stores are: "
                            + String.join(", ", storeNames.values()) + ".");
                }
                shopInfo.couponsOfStore(store.get().id()).forEach(coupon -> coupons.putIfAbsent(coupon.code(), coupon));
            } else {
                shopInfo.platformCoupons().forEach(coupon -> coupons.putIfAbsent(coupon.code(), coupon));
                for (StoreData store : stores) {
                    shopInfo.couponsOfStore(store.id()).forEach(coupon -> coupons.putIfAbsent(coupon.code(), coupon));
                }
            }

            if (coupons.isEmpty()) {
                return ToolResult.notFound("There are no coupons at the moment.");
            }
            List<OfferInfo> offers = coupons.values().stream()
                    .map(coupon -> OfferInfo.from(coupon, storeNames.get(coupon.storeId())))
                    .toList();
            return ToolResult.ok(offers.size() + (offers.size() == 1 ? " coupon" : " coupons") + " can be used now.", offers);
        });
    }

    @McpTool(name = "getDeliveryRules",
            description = """
                    The delivery fee, the order amount from which delivery is free, the minimum order value, and the \
                    customer support phone number and email address.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<DeliveryRules> getDeliveryRules() {
        return runner.run("getDeliveryRules", () -> ToolResult.ok("The current delivery rules.", DeliveryRules.from(shopInfo.settings())));
    }

    @McpTool(name = "listStores",
            description = """
                    The stores of the shop: address, phone number, opening hours for every day of the week (India time) \
                    and whether they are open now. Use it for every question about a store: where it is, how to call it, \
                    when it opens or closes. Each order is delivered by one store.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public ToolResult<List<StoreInfo>> listStores() {
        return runner.run("listStores", () -> {
            List<StoreInfo> stores = shopInfo.stores().stream().map(StoreInfo::from).toList();
            if (stores.isEmpty()) {
                return ToolResult.notFound("No store is open for business on the platform at the moment.");
            }
            return ToolResult.ok(stores.size() + (stores.size() == 1 ? " store." : " stores."), stores);
        });
    }
}
