package com.grocery.mcpclient.config;

import com.grocery.mcpclient.context.UserContext;
import com.grocery.mcpclient.context.UserContextHolder;
import com.grocery.mcpclient.filter.CorrelationIdFilter;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What goes to mcp-server-service in the headers: the API key always, the user's token for every logged-in user (never
 * for guests), and the trace header of the chat request.
 */
class McpClientConfigTest {

    private static final String API_KEY = "test-mcp-api-key-0123456789abcdefghij";

    @AfterEach
    void cleanUp() {
        UserContextHolder.clear();
        MDC.clear();
    }

    @Test
    void aCustomersTokenAndTheCorrelationIdArePassedOn() {
        UserContextHolder.set(new UserContext(4L, "CUSTOMER", "customer-jwt"));
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-1");

        HttpRequest request = requestWith(McpClientConfig.currentTransportContext());

        assertThat(request.headers().firstValue("X-MCP-API-Key")).hasValue(API_KEY);
        assertThat(request.headers().firstValue("X-User-Token")).hasValue("customer-jwt");
        assertThat(request.headers().firstValue("X-Correlation-Id")).hasValue("corr-1");
    }

    @Test
    void vendorsPartnersAndAdminsPassTheirTokenToo_guestsPassNone() {
        UserContextHolder.set(new UserContext(10L, "VENDOR", "vendor-jwt"));
        assertThat(requestWith(McpClientConfig.currentTransportContext()).headers().firstValue("X-User-Token")).hasValue("vendor-jwt");

        UserContextHolder.set(new UserContext(1L, "SUPER_ADMIN", "owner-jwt"));
        assertThat(requestWith(McpClientConfig.currentTransportContext()).headers().firstValue("X-User-Token")).hasValue("owner-jwt");

        UserContextHolder.set(UserContext.GUEST);
        assertThat(requestWith(McpClientConfig.currentTransportContext()).headers().firstValue("X-User-Token")).isEmpty();
    }

    @Test
    void theTraceHeaderIsPassedOn_soTheToolCallJoinsTheChatRequestsTrace() {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        HttpRequest request = requestWith(McpClientConfig.currentTransportContext(Map.of("traceparent", traceparent)));

        assertThat(request.headers().firstValue("traceparent")).hasValue(traceparent);
    }

    @Test
    void withoutATraceNoTraceHeaderIsSent() {
        assertThat(McpClientConfig.traceHeaders(Tracer.NOOP, Propagator.NOOP)).isEmpty();
        assertThat(requestWith(McpClientConfig.currentTransportContext()).headers().firstValue("traceparent")).isEmpty();
    }

    @Test
    void theTokenIsNeverPrinted() {
        assertThat(new UserContext(4L, "CUSTOMER", "customer-jwt").toString()).doesNotContain("customer-jwt");
    }

    private static HttpRequest requestWith(McpTransportContext context) {
        var customizer = new McpClientConfig().mcpRequestHeaders(new McpProperties(API_KEY, Duration.ofSeconds(2)));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://mcp-server/mcp"));
        customizer.customize(builder, "POST", URI.create("http://mcp-server/mcp"), "{}", context);
        return builder.build();
    }
}
