package com.grocery.store;

import com.grocery.store.constant.HeaderNames;
import com.grocery.store.entity.OutboxEvent;
import com.grocery.store.repository.OutboxEventRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: a vendor registers a store, an admin approves it, customers can find it,
 * and both events are waiting in the outbox for Debezium.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StoreFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void vendorRegistersStore_adminApprovesIt_andCustomersCanFindIt() throws Exception {
        // 1. Vendor 500 registers a store
        String registered = mockMvc.perform(post("/api/v1/vendor/store").header(HeaderNames.USER_ID, "500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Flow Test Mart", "phone": "9845012345", "addressLine1": "1 Test Street",
                                  "city": "Mysuru", "state": "Karnataka", "pincode": "570001" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        Integer storeId = JsonPath.read(registered, "$.data.id");

        // 2. Not visible to customers yet
        mockMvc.perform(get("/api/v1/stores/{id}", storeId)).andExpect(status().isNotFound());

        // 3. Admin 2 approves it
        mockMvc.perform(post("/api/v1/admin/stores/{id}/approve", storeId).header(HeaderNames.USER_ID, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.approvedAt").isNotEmpty());

        // 4. Approving twice is a business-rule error
        mockMvc.perform(post("/api/v1/admin/stores/{id}/approve", storeId).header(HeaderNames.USER_ID, "2"))
                .andExpect(status().isBadRequest());

        // 5. Customers find it by city and by PIN code
        mockMvc.perform(get("/api/v1/stores").param("pincode", "570001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].name", hasItem("Flow Test Mart")));

        // 6. Both events are in the outbox, in order
        assertThat(outboxEventRepository.findByAggregateTypeAndAggregateId("store", String.valueOf(storeId)))
                .extracting(OutboxEvent::getEventType)
                .containsExactlyInAnyOrder("StoreRegistered", "StoreApproved");
    }
}
