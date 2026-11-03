package com.grocery.mcpserver.tool.result;

import com.grocery.mcpserver.client.dto.SettingsData;
import com.grocery.mcpserver.tool.Formats;

/**
 * The delivery rules and the support contact (getDeliveryRules).
 *
 * @param summary the rules in one sentence, so a small model does not have to combine the numbers itself
 */
public record DeliveryRules(
        String deliveryFee,
        String freeDeliveryFrom,
        String minimumOrderValue,
        String summary,
        String supportPhone,
        String supportEmail) {

    public static DeliveryRules from(SettingsData settings) {
        String fee = Formats.rupees(settings.deliveryFee());
        String freeFrom = Formats.rupees(settings.freeDeliveryFrom());
        String minimum = Formats.rupees(settings.minimumOrderValue());
        String summary = "The smallest order is " + minimum + ". Orders of " + freeFrom + " or more are delivered free; "
                + "smaller orders pay a delivery fee of " + fee + ". Prices include GST.";
        return new DeliveryRules(fee, freeFrom, minimum, summary, settings.supportPhone(), settings.supportEmail());
    }
}
