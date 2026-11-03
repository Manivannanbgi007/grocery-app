package com.grocery.order.controller;

import com.grocery.order.config.VendorOrderStreamProperties;
import com.grocery.order.constant.HeaderNames;
import com.grocery.order.dto.response.OrderChange;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.live.VendorOrderStreams;
import com.grocery.order.service.VendorOrderService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The vendor's live order stream (Server-Sent Events) through Spring MVC, with the real VendorOrderStreams. MockMvc
 * keeps a stream open (an async request): what was sent so far is the response's content.
 * Every test starts with no open stream (a new context: the streams of a test stay open until it closes).
 */
@WebMvcTest(VendorOrderController.class)
@ExtendWith(OutputCaptureExtension.class)   // a test can read what was logged
@Import({VendorOrderStreams.class, VendorOrderControllerTest.StreamSetup.class})
@TestPropertySource(properties = "app.vendor-order-stream.max-streams=2")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class VendorOrderControllerTest {

    private static final long FRESH_MART = 1L;
    private static final long OTHER_STORE = 2L;

    /** What VendorOrderStreams needs beyond the web layer: its settings (application.yml) and a metrics registry. */
    @TestConfiguration
    @EnableConfigurationProperties(VendorOrderStreamProperties.class)
    static class StreamSetup {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private VendorOrderStreams streams;

    @MockitoBean
    private VendorOrderService vendorOrderService;

    @Test
    void stream_saysReadyAtOnce_andThenEveryChangeOfTheStoresOrders() throws Exception {
        MvcResult stream = openStream(FRESH_MART);
        assertThat(stream.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(stream.getResponse().getHeader("Cache-Control")).isEqualTo("no-cache");
        assertThat(stream.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(content(stream)).isEqualTo("event:ready\ndata:{}\n\n");

        streams.sendOrderChange(FRESH_MART, new OrderChange(194L, OrderStatus.CONFIRMED));
        streams.sendOrderChange(OTHER_STORE, new OrderChange(195L, OrderStatus.CONFIRMED));   // not Fresh Mart's

        assertThat(content(stream)).isEqualTo("""
                event:ready
                data:{}

                event:order
                data:{"orderId":194,"status":"CONFIRMED"}

                """);
        assertThat(streams.openStreams()).isEqualTo(1);
    }

    @Test
    void aBrowserThatLeft_isForgottenAtOnce_andNotLoggedAsAnError(CapturedOutput log) throws Exception {
        MvcResult stream = openStream(FRESH_MART);
        assertThat(streams.openStreams()).isEqualTo(1);

        // What the servlet container reports when the connection breaks (page closed, network lost)
        MockAsyncContext asyncContext = (MockAsyncContext) stream.getRequest().getAsyncContext();
        for (AsyncListener listener : asyncContext.getListeners()) {
            listener.onError(new AsyncEvent(asyncContext, new IOException("An established connection was aborted")));
        }
        assertThat(streams.openStreams()).isZero();

        // Spring then hands the error to the exception handlers: a closed page is normal, not an "Unexpected error"
        mockMvc.perform(asyncDispatch(stream));
        assertThat(log).doesNotContain("Unexpected error");
    }

    @Test
    void stream_isRefusedWith503_whileThisInstanceHasAsManyStreamsAsItMay() throws Exception {
        openStream(FRESH_MART);
        openStream(OTHER_STORE);   // max-streams=2 in this test

        mockMvc.perform(streamRequest().header(HeaderNames.STORE_ID, String.valueOf(FRESH_MART)))
                .andExpect(status().isServiceUnavailable());
        assertThat(streams.openStreams()).isEqualTo(2);
    }

    @Test
    void stream_needsTheVendorsStore() throws Exception {
        // A vendor without an approved store (no X-Store-Id from the gateway): refused, no stream opened
        mockMvc.perform(streamRequest())
                .andExpect(status().isForbidden());
        assertThat(streams.openStreams()).isZero();
    }

    /**
     * A vendor's request as the gateway passes it on. The browser accepts JSON too (api/client.ts apiStream): with
     * text/event-stream alone, Spring could not write an error answer (403 ...) and the browser would get a 500.
     */
    private MockHttpServletRequestBuilder streamRequest() {
        return get("/api/v1/vendor/orders/stream")
                .header(HeaderNames.USER_ID, "2")
                .header(HeaderNames.USER_ROLE, "VENDOR")
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON);
    }

    private MvcResult openStream(long storeId) throws Exception {
        return mockMvc.perform(streamRequest().header(HeaderNames.STORE_ID, String.valueOf(storeId)))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private static String content(MvcResult stream) throws Exception {
        return stream.getResponse().getContentAsString();
    }
}
