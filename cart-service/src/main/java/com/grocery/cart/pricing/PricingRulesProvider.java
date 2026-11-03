package com.grocery.cart.pricing;

/**
 * Where the current pricing rules come from. The real one reads the pricing_settings table
 * (DatabasePricingRulesProvider); unit tests simply pass fixed rules as a lambda.
 */
@FunctionalInterface
public interface PricingRulesProvider {

    PricingRules current();
}
