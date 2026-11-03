package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.client.ShopInfoClient;
import com.grocery.mcpserver.client.dto.SettingsData;
import com.grocery.mcpserver.client.dto.StoreData;
import com.grocery.mcpserver.client.dto.StoreData.OpeningHoursData;
import com.grocery.mcpserver.tool.ToolResult.Status;
import com.grocery.mcpserver.tool.result.OfferInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopInfoToolsTest {

    @Mock
    private ShopInfoClient shopInfo;

    private ShopInfoTools tools;

    @BeforeEach
    void setUp() {
        tools = new ShopInfoTools(shopInfo, new ToolRunner());
        lenient().when(shopInfo.stores()).thenReturn(TestData.stores());
    }

    @Test
    void offersListEveryCouponOnceWithItsStore() {
        when(shopInfo.platformCoupons()).thenReturn(List.of(TestData.save10()));
        // Every store answers the platform coupons again, plus its own
        when(shopInfo.couponsOfStore(1L)).thenReturn(List.of(TestData.save10(), TestData.fresh20()));
        when(shopInfo.couponsOfStore(2L)).thenReturn(List.of(TestData.save10(), TestData.green30()));

        ToolResult<List<OfferInfo>> result = tools.listCurrentOffers(null);

        assertThat(result.message()).isEqualTo("3 coupons can be used now.");
        assertThat(result.data()).extracting(OfferInfo::code).containsExactly("SAVE10", "FRESH20", "GREEN30");
        OfferInfo save10 = result.data().get(0);
        assertThat(save10.benefit()).isEqualTo("10% off, up to ₹100");
        assertThat(save10.minimumOrder()).isEqualTo("₹199");
        assertThat(save10.validIn()).isEqualTo("All stores");
        assertThat(result.data().get(1).validIn()).isEqualTo("Only at Fresh Mart");
        assertThat(result.data().get(2).benefit()).isEqualTo("₹30 off");
    }

    @Test
    void offersOfOneStoreFindTheStoreByAPartOfItsName() {
        when(shopInfo.couponsOfStore(2L)).thenReturn(List.of(TestData.save10(), TestData.green30()));

        ToolResult<List<OfferInfo>> result = tools.listCurrentOffers("green basket store");

        assertThat(result.data()).extracting(OfferInfo::code).containsExactly("SAVE10", "GREEN30");
        verify(shopInfo, never()).platformCoupons();
    }

    @Test
    void offersOfAnUnknownStoreNameTheStores() {
        ToolResult<List<OfferInfo>> result = tools.listCurrentOffers("Big Bazaar");

        assertThat(result.status()).isEqualTo(Status.NOT_FOUND);
        assertThat(result.message()).isEqualTo("There is no store called \"Big Bazaar\". The stores are: Fresh Mart, Green Basket.");
    }

    @Test
    void deliveryRulesComeWithASummarySentence() {
        when(shopInfo.settings()).thenReturn(new SettingsData(new BigDecimal("30.00"), new BigDecimal("299.00"), new BigDecimal("99.00"),
                "support@grocery.com", "1800-123-4567"));

        var rules = tools.getDeliveryRules().data();

        assertThat(rules.freeDeliveryFrom()).isEqualTo("₹299");
        assertThat(rules.summary()).isEqualTo("The smallest order is ₹99. Orders of ₹299 or more are delivered free; "
                + "smaller orders pay a delivery fee of ₹30. Prices include GST.");
    }

    @Test
    void storesShowAddressStatusAndHours() {
        var hours = List.of(new OpeningHoursData("MONDAY", "07:00:00", "22:00:00", false));
        when(shopInfo.stores()).thenReturn(List.of(
                new StoreData(1L, "Fresh Mart", null, "9000000003", "45, 100 Feet Road", "Opposite CMH Hospital", "Bengaluru", "560038", true, true, hours),
                new StoreData(4L, "New Shop", null, "9000000099", "1, Main Road", null, "Bengaluru", "560001", false, false, List.of())));

        var stores = tools.listStores().data();

        assertThat(stores.get(0).address()).isEqualTo("45, 100 Feet Road, Opposite CMH Hospital, Bengaluru, 560038");
        assertThat(stores.get(0).status()).isEqualTo("Open now");
        assertThat(stores.get(0).openingHours()).containsExactly("Monday: 07:00-22:00");
        assertThat(stores.get(1).status()).isEqualTo("Not taking orders at the moment");
    }
}
