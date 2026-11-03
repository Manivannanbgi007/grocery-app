package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.grocery.mcpserver.client.dto.CouponData;
import com.grocery.mcpserver.tool.Formats;

/**
 * A coupon customers can use now (listCurrentOffers).
 *
 * @param code         what the customer types in the cart, e.g. "SAVE10"
 * @param benefit      "₹50 off" or "10% off, up to ₹100"
 * @param minimumOrder the items total the cart needs, e.g. "₹199" (null when there is no minimum)
 * @param validUntil   last day, e.g. "31 Dec 2027"
 * @param validIn      "All stores" or "Only at Fresh Mart"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferInfo(String code, String description, String benefit, String minimumOrder, String validUntil, String validIn) {

    public static OfferInfo from(CouponData coupon, String storeName) {
        String validIn = coupon.storeId() == null ? "All stores" : "Only at " + (storeName != null ? storeName : "one store");
        boolean hasMinimum = coupon.minOrderAmount() != null && coupon.minOrderAmount().signum() > 0;
        return new OfferInfo(
                coupon.code(),
                coupon.description(),
                benefitOf(coupon),
                hasMinimum ? Formats.rupees(coupon.minOrderAmount()) : null,
                Formats.date(coupon.validUntil()),
                validIn);
    }

    private static String benefitOf(CouponData coupon) {
        if ("FLAT".equals(coupon.discountType())) {
            return Formats.rupees(coupon.discountValue()) + " off";
        }
        String percent = Formats.percent(coupon.discountValue()) + " off";
        return coupon.maxDiscountAmount() != null ? percent + ", up to " + Formats.rupees(coupon.maxDiscountAmount()) : percent;
    }
}
