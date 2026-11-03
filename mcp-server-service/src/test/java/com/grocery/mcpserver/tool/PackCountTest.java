package com.grocery.mcpserver.tool;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The packs come from code, never from the model: amounts become packs of the product's size, or a question. */
class PackCountTest {

    private static final BigDecimal ONE = BigDecimal.ONE;

    @Test
    void anAmountBecomesPacksOfTheProductsSize() {
        assertThat(PackCount.of(2.0, "kg", "Tomato", "KG", ONE).packs()).isEqualTo(2);
        assertThat(PackCount.of(2.0, "kg", "Onion", "G", new BigDecimal("500")).packs()).isEqualTo(4);
        assertThat(PackCount.of(1.0, "L", "Milk", "ML", new BigDecimal("500")).packs()).isEqualTo(2);
        assertThat(PackCount.of(500.0, "g", "Onion", "G", new BigDecimal("500")).packs()).isEqualTo(1);
    }

    @Test
    void packsAndCountsAreTakenAsTheyAre() {
        assertThat(PackCount.of(3.0, null, "Bread", "PACK", ONE).packs()).isEqualTo(3);
        assertThat(PackCount.of(3.0, "packets", "Milk", "ML", new BigDecimal("500")).packs()).isEqualTo(3);
        assertThat(PackCount.of(2.0, "loaves", "Bread", "PACK", ONE).packs()).isEqualTo(2);
        assertThat(PackCount.of(2.0, "pieces", "Bread", "PACK", ONE).packs()).isEqualTo(2);
    }

    @Test
    void piecesBecomePacksOfPieces() {
        assertThat(PackCount.of(12.0, "eggs", "Eggs", "PIECE", new BigDecimal("6")).packs()).isEqualTo(2);
        assertThat(PackCount.of(1.0, "dozen", "Eggs", "PIECE", new BigDecimal("6")).packs()).isEqualTo(2);
        assertThat(PackCount.of(6.0, "bananas", "Banana", "DOZEN", ONE).question())
                .isEqualTo("6 bananas is not a whole number of packs of Banana (1 dozen a pack). 12 bananas (1 pack)?");
    }

    @Test
    void aPartOfAPackIsAQuestionWithTheNearestAmounts() {
        assertThat(PackCount.of(1.2, "kg", "Tomato", "KG", ONE).question())
                .isEqualTo("1.2 kg is not a whole number of packs of Tomato (1 kg a pack). 1 kg (1 pack) or 2 kg (2 packs)?");
        assertThat(PackCount.of(250.0, "g", "Onion", "G", new BigDecimal("500")).question())
                .isEqualTo("250 g is not a whole number of packs of Onion (500 g a pack). 500 g (1 pack)?");
        assertThat(PackCount.of(1.5, null, "Bread", "PACK", ONE).question())
                .isEqualTo("Only whole packs can be bought. How many packs of Bread should it be?");
    }

    @Test
    void aUnitThatDoesNotFitTheProductIsAQuestion() {
        assertThat(PackCount.of(2.0, "kg", "Eggs", "PIECE", new BigDecimal("6")).question())
                .isEqualTo("Eggs is sold in packs of 6 pcs, not by kg. How many packs should it be?");
        assertThat(PackCount.of(3.0, "pieces", "Tomato", "KG", ONE).question())
                .isEqualTo("Tomato is sold in packs of 1 kg, not by pieces. How many packs should it be?");
        // "3 tomatoes" of a product sold by weight: 3 kg? 3 pieces? ask
        assertThat(PackCount.of(3.0, "tomatoes", "Tomato", "KG", ONE).question())
                .isEqualTo("How many packs of Tomato (1 kg a pack) should it be?");
    }

    @Test
    void noAmountOrTooManyPacksIsAQuestion() {
        assertThat(PackCount.of(null, null, "Tomato", "KG", ONE).ok()).isFalse();
        assertThat(PackCount.of(-1.0, "kg", "Tomato", "KG", ONE).ok()).isFalse();
        assertThat(PackCount.of(25.0, "kg", "Tomato", "KG", ONE).question())
                .isEqualTo("That would be 25 packs of Tomato (1 kg a pack), and a cart takes at most 20 packs of a product. "
                        + "How many packs should it be?");
        assertThat(PackCount.of(1e12, null, "Tomato", "KG", ONE).ok()).isFalse();
    }

    @Test
    void aPackSizeInTheCustomersWordsIsRecognised() {
        assertThat(PackCount.isPackSize(new BigDecimal("500"), "g", "G", new BigDecimal("500"))).isTrue();
        assertThat(PackCount.isPackSize(new BigDecimal("0.5"), "kg", "G", new BigDecimal("500"))).isTrue();
        assertThat(PackCount.isPackSize(new BigDecimal("1"), "litre", "ML", new BigDecimal("1000"))).isTrue();
        assertThat(PackCount.isPackSize(new BigDecimal("100"), "g", "G", new BigDecimal("500"))).isFalse();
        assertThat(PackCount.isPackSize(new BigDecimal("6"), "pcs", "PIECE", new BigDecimal("6"))).isTrue();
        assertThat(PackCount.isPackSize(new BigDecimal("1"), "kg", "PIECE", new BigDecimal("6"))).isFalse();
        assertThat(PackCount.isUnit("Kg.")).isTrue();
        assertThat(PackCount.isUnit("up")).isFalse();
    }
}
