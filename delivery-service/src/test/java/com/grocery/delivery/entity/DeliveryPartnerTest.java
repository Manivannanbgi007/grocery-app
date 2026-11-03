package com.grocery.delivery.entity;

import com.grocery.delivery.enums.KycStatus;
import com.grocery.delivery.enums.VehicleType;
import com.grocery.delivery.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The KYC rules (plain JUnit).
 */
class DeliveryPartnerTest {

    @Test
    void kyc_submitted_thenApproved() {
        DeliveryPartner partner = DeliveryPartner.register(20L, "Vijay Kumar", "9845011111");
        assertThat(partner.getKycStatus()).isEqualTo(KycStatus.NOT_SUBMITTED);

        partner.submitKyc(VehicleType.SCOOTER, "KA03XY9999", "KA0320200011111", zone());
        assertThat(partner.getKycStatus()).isEqualTo(KycStatus.PENDING);

        partner.approveKyc();
        assertThat(partner.isApproved()).isTrue();
    }

    @Test
    void rejectedKyc_canBeSubmittedAgain() {
        DeliveryPartner partner = DeliveryPartner.register(21L, "Vijay Kumar", "9845011111");
        partner.submitKyc(VehicleType.SCOOTER, "KA03XY9999", "KA0320200011111", zone());
        partner.rejectKyc("Licence photo unreadable");
        assertThat(partner.getKycNote()).isEqualTo("Licence photo unreadable");

        partner.submitKyc(VehicleType.SCOOTER, "KA03XY9999", "KA0320200022222", zone());

        assertThat(partner.getKycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(partner.getKycNote()).isNull();
    }

    @Test
    void onlyAPendingKycCanBeDecided() {
        DeliveryPartner partner = DeliveryPartner.register(22L, "Vijay Kumar", "9845011111");

        assertThatThrownBy(partner::approveKyc).isInstanceOf(BusinessException.class);
    }

    private DeliveryZone zone() {
        DeliveryZone zone = new DeliveryZone();
        zone.setName("Indiranagar");
        zone.setActive(true);
        return zone;
    }

    @Test
    void ratingAverage_isRoundedToOneDecimal() {
        DeliveryPartner partner = DeliveryPartner.register(22L, "Vijay Kumar", "9845011111");
        assertThat(partner.getRatingAverage()).isNull();

        partner.addRating(5);
        partner.addRating(4);
        partner.addRating(4);

        assertThat(partner.getRatingCount()).isEqualTo(3);
        assertThat(partner.getRatingAverage()).isEqualByComparingTo("4.3");
    }
}
