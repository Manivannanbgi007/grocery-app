package com.grocery.mcpserver.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * How many packs of a product the customer means, worked out here in code (never by the model).
 *
 * The shop sells packs: "Tomato, 1 kg", "Amul Butter, 100 g", "Eggs, 6 pcs". The customer may count packs ("3 packets
 * of milk"), count pieces ("12 eggs"), or name an amount ("2 kg of tomatoes"); an amount becomes packs of this
 * product's size (2 kg of a 500 g pack = 4 packs). When that is not a whole number of packs, or the unit does not fit
 * the product (kilos of eggs), the result is a question for the customer instead of a guess.
 */
final class PackCount {

    /** cart-service's limit: at most 20 packs of one product in a cart */
    static final int MAX_PACKS = 20;

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    /** What an amount measures. Every amount is turned into the smallest unit: grams, millilitres, pieces or packs. */
    private enum Measure { GRAMS, MILLILITRES, PIECES, PACKS }

    /** An amount in the smallest unit, e.g. 2 kg = (GRAMS, 2000). */
    private record Amount(Measure measure, BigDecimal value) {

        static Amount of(Measure measure, int value) {
            return new Amount(measure, BigDecimal.valueOf(value));
        }
    }

    private static final Amount ONE_PACK = Amount.of(Measure.PACKS, 1);

    /** A unit word -> one of it in the smallest unit (kg -> 1000 grams, dozen -> 12 pieces). */
    private static final Map<String, Amount> UNITS = Map.ofEntries(
            Map.entry("kg", Amount.of(Measure.GRAMS, 1000)), Map.entry("kgs", Amount.of(Measure.GRAMS, 1000)),
            Map.entry("kilo", Amount.of(Measure.GRAMS, 1000)), Map.entry("kilos", Amount.of(Measure.GRAMS, 1000)),
            Map.entry("kilogram", Amount.of(Measure.GRAMS, 1000)), Map.entry("kilograms", Amount.of(Measure.GRAMS, 1000)),
            Map.entry("g", Amount.of(Measure.GRAMS, 1)), Map.entry("gm", Amount.of(Measure.GRAMS, 1)),
            Map.entry("gms", Amount.of(Measure.GRAMS, 1)), Map.entry("gram", Amount.of(Measure.GRAMS, 1)),
            Map.entry("grams", Amount.of(Measure.GRAMS, 1)),
            Map.entry("l", Amount.of(Measure.MILLILITRES, 1000)), Map.entry("ltr", Amount.of(Measure.MILLILITRES, 1000)),
            Map.entry("litre", Amount.of(Measure.MILLILITRES, 1000)), Map.entry("litres", Amount.of(Measure.MILLILITRES, 1000)),
            Map.entry("liter", Amount.of(Measure.MILLILITRES, 1000)), Map.entry("liters", Amount.of(Measure.MILLILITRES, 1000)),
            Map.entry("ml", Amount.of(Measure.MILLILITRES, 1)), Map.entry("millilitre", Amount.of(Measure.MILLILITRES, 1)),
            Map.entry("millilitres", Amount.of(Measure.MILLILITRES, 1)), Map.entry("milliliter", Amount.of(Measure.MILLILITRES, 1)),
            Map.entry("milliliters", Amount.of(Measure.MILLILITRES, 1)),
            Map.entry("piece", Amount.of(Measure.PIECES, 1)), Map.entry("pieces", Amount.of(Measure.PIECES, 1)),
            Map.entry("pc", Amount.of(Measure.PIECES, 1)), Map.entry("pcs", Amount.of(Measure.PIECES, 1)),
            Map.entry("dozen", Amount.of(Measure.PIECES, 12)), Map.entry("dozens", Amount.of(Measure.PIECES, 12)),
            Map.entry("pack", ONE_PACK), Map.entry("packs", ONE_PACK), Map.entry("packet", ONE_PACK), Map.entry("packets", ONE_PACK),
            Map.entry("pkt", ONE_PACK), Map.entry("pkts", ONE_PACK), Map.entry("bottle", ONE_PACK), Map.entry("bottles", ONE_PACK),
            Map.entry("box", ONE_PACK), Map.entry("boxes", ONE_PACK), Map.entry("bag", ONE_PACK), Map.entry("bags", ONE_PACK),
            Map.entry("jar", ONE_PACK), Map.entry("jars", ONE_PACK), Map.entry("tin", ONE_PACK), Map.entry("tins", ONE_PACK));

    /** Either the number of packs, or the question to ask the customer. */
    record Result(Integer packs, String question) {

        boolean ok() {
            return packs != null;
        }
    }

    private PackCount() {
    }

    /**
     * @param quantity     the number the customer said ("2" in "2 kg")
     * @param unit         the unit the customer said (kg, g, l, ml, pieces, dozen, packs ...); none means packs
     * @param productName  for the question, e.g. "Tomato"
     * @param packUnit     the product's unit: KG, G, L, ML, PIECE, DOZEN or PACK
     * @param packQuantity how much of it one pack holds (1 for "1 kg", 500 for "500 g")
     */
    static Result of(Double quantity, String unit, String productName, String packUnit, BigDecimal packQuantity) {
        String packSize = Formats.packSize(packUnit, packQuantity);
        String pack = "PACK".equals(packUnit) ? productName : productName + " (" + packSize + " a pack)";
        if (quantity == null || quantity.isNaN() || quantity.isInfinite() || quantity <= 0) {
            return question("How many packs of " + pack + " should it be?");
        }
        Amount onePack = onePack(packUnit, packQuantity);
        Amount perUnit = unitOf(unit, onePack.measure());
        if (perUnit == null) {
            return question("How many packs of " + pack + " should it be?");
        }

        BigDecimal said = BigDecimal.valueOf(quantity);
        BigDecimal packs;
        if (perUnit.measure() == Measure.PACKS) {
            packs = said.multiply(perUnit.value());                  // "3 packets"
        } else if (perUnit.measure() == onePack.measure()) {
            packs = said.multiply(perUnit.value())                   // "2 kg" of a 500 g pack: 2000 g / 500 g = 4
                    .divide(onePack.value(), 6, RoundingMode.HALF_UP);
        } else {
            return question(productName + " is sold in packs of " + packSize + ", not by " + word(unit)
                    + ". How many packs should it be?");
        }

        if (packs.stripTrailingZeros().scale() > 0) {
            return notWhole(said, word(unit), perUnit, packs, pack, onePack);
        }
        if (packs.compareTo(BigDecimal.valueOf(MAX_PACKS)) > 0) {
            return question("That would be " + packs.toBigInteger() + " packs of " + pack + ", and a cart takes at most "
                    + MAX_PACKS + " packs of a product. How many packs should it be?");
        }
        return new Result(packs.intValueExact(), null);
    }

    /** True for the words that name a unit: "kg", "ml", "pcs", "packets" ... */
    static boolean isUnit(String word) {
        return UNITS.containsKey(word(word));
    }

    /**
     * True when the amount is exactly one pack of the product: "100 g" or "0.1 kg" of a 100 g pack. It tells
     * "Amul Butter (100 g)" from "Amul Butter (500 g)".
     */
    static boolean isPackSize(BigDecimal number, String unit, String packUnit, BigDecimal packQuantity) {
        Amount perUnit = UNITS.get(word(unit));
        Amount onePack = onePack(packUnit, packQuantity);
        if (perUnit == null || perUnit.measure() != onePack.measure()) {
            return false;
        }
        if (onePack.measure() == Measure.PACKS) {        // "Bread, 1 pack": the pack count itself
            return number.compareTo(packQuantity == null ? BigDecimal.ONE : packQuantity) == 0;
        }
        return number.multiply(perUnit.value()).compareTo(onePack.value()) == 0;
    }

    /** One pack in the smallest unit: "500 G" = 500 grams, "1 DOZEN" = 12 pieces, "1 PACK" = one pack. */
    private static Amount onePack(String packUnit, BigDecimal packQuantity) {
        BigDecimal size = packQuantity == null || packQuantity.signum() <= 0 ? BigDecimal.ONE : packQuantity;
        return switch (packUnit == null ? "" : packUnit) {
            case "KG" -> new Amount(Measure.GRAMS, size.multiply(THOUSAND));
            case "G" -> new Amount(Measure.GRAMS, size);
            case "L" -> new Amount(Measure.MILLILITRES, size.multiply(THOUSAND));
            case "ML" -> new Amount(Measure.MILLILITRES, size);
            case "PIECE" -> new Amount(Measure.PIECES, size);
            case "DOZEN" -> new Amount(Measure.PIECES, size.multiply(TWELVE));
            default -> ONE_PACK;
        };
    }

    /**
     * What one of the customer's unit is. No unit counts packs. Pieces of a pack product are packs ("2 loaves of
     * bread"). A word that is not a unit ("6 eggs", "2 loaves") counts what the product is sold in, pieces or packs;
     * for a product sold by weight or volume it is not clear (null).
     */
    private static Amount unitOf(String unit, Measure packMeasure) {
        String word = word(unit);
        if (word.isEmpty()) {
            return ONE_PACK;
        }
        Amount known = UNITS.get(word);
        if (known != null) {
            return known.measure() == Measure.PIECES && packMeasure == Measure.PACKS ? new Amount(Measure.PACKS, known.value()) : known;
        }
        return packMeasure == Measure.PIECES || packMeasure == Measure.PACKS ? Amount.of(packMeasure, 1) : null;
    }

    /** "1.2 kg is not a whole number of packs of Tomato (1 kg a pack). 1 kg (1 pack) or 2 kg (2 packs)?" */
    private static Result notWhole(BigDecimal said, String unit, Amount perUnit, BigDecimal packs, String pack, Amount onePack) {
        if (perUnit.measure() == Measure.PACKS) {
            return question("Only whole packs can be bought. How many packs of " + pack + " should it be?");
        }
        int fewer = packs.setScale(0, RoundingMode.FLOOR).intValue();
        String options = (fewer >= 1 ? option(fewer, perUnit, onePack, unit) + " or " : "") + option(fewer + 1, perUnit, onePack, unit);
        return question(said.stripTrailingZeros().toPlainString() + " " + unit + " is not a whole number of packs of " + pack
                + ". " + options + "?");
    }

    /** "1.5 kg (3 packs)": an option in the customer's own unit */
    private static String option(int packs, Amount perUnit, Amount onePack, String unit) {
        BigDecimal inTheirUnit = onePack.value().multiply(BigDecimal.valueOf(packs))
                .divide(perUnit.value(), 3, RoundingMode.HALF_UP).stripTrailingZeros();
        return inTheirUnit.toPlainString() + " " + unit + " (" + packs + (packs == 1 ? " pack)" : " packs)");
    }

    /** "Kg." -> "kg" */
    private static String word(String unit) {
        return unit == null ? "" : unit.strip().toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
    }

    private static Result question(String question) {
        return new Result(null, question);
    }
}
