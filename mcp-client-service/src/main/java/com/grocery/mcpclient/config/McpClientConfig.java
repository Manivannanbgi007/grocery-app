package com.grocery.mcpclient.config;

import com.grocery.mcpclient.constant.HeaderNames;
import com.grocery.mcpclient.context.UserContext;
import com.grocery.mcpclient.context.UserContextHolder;
import com.grocery.mcpclient.filter.CorrelationIdFilter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.slf4j.MDC;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;

/**
 * Adds our settings to the MCP client that Spring AI creates from spring.ai.mcp.client.* (application.yml).
 *
 * How a value gets from this request into the HTTP call to mcp-server-service:
 * 1. transportContext(): runs on the request's thread when a tool is called, and copies the correlation id, the
 *    trace headers and, for a logged-in user (customer, vendor, delivery partner, admin), the user's token into the
 *    MCP "transport context" (the MCP client may send the HTTP request on another thread, where the ThreadLocal
 *    values are not available).
 * 2. mcpRequestHeaders(): runs for every HTTP request to mcp-server-service and turns the API key and the
 *    values of the transport context into headers.
 * The token never goes into the prompt: the model cannot see it, repeat it or swap it for another one.
 *
 * Trace headers: the MCP client sends with the JDK HttpClient, which Spring does not instrument, so the W3C
 * "traceparent" header is added here. Without it mcp-server-service would start a new trace for every tool call,
 * and Jaeger could not show the tool's work inside the chat request.
 */
@Configuration
public class McpClientConfig {

    static final String CORRELATION_ID = "correlationId";
    static final String USER_TOKEN = "userToken";
    static final String TRACE_HEADERS = "traceHeaders";

    @Bean
    McpClientCustomizer<McpClient.SyncSpec> transportContext(Tracer tracer, Propagator propagator) {
        return (connectionName, spec) -> spec.transportContextProvider(
                () -> currentTransportContext(traceHeaders(tracer, propagator)));
    }

    /** The W3C trace headers ("traceparent") of the current span; empty when nothing is traced. */
    static Map<String, String> traceHeaders(Tracer tracer, Propagator propagator) {
        Map<String, String> headers = new HashMap<>();
        Span span = tracer.currentSpan();
        if (span != null) {
            propagator.inject(span.context(), headers, Map::put);
        }
        return headers;
    }

    /** What the current request passes on to mcp-server-service, without trace headers. */
    static McpTransportContext currentTransportContext() {
        return currentTransportContext(Map.of());
    }

    /** What the current request passes on to mcp-server-service (called on the request's thread). */
    static McpTransportContext currentTransportContext(Map<String, String> traceHeaders) {
        Map<String, Object> values = new HashMap<>();
        if (!traceHeaders.isEmpty()) {
            values.put(TRACE_HEADERS, traceHeaders);
        }
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            values.put(CORRELATION_ID, correlationId);
        }
        UserContext user = UserContextHolder.get();
        if (user.isLoggedIn()) {
            values.put(USER_TOKEN, user.accessToken());
        }
        return McpTransportContext.create(values);
    }

    @Bean
    McpSyncHttpClientRequestCustomizer mcpRequestHeaders(McpProperties mcpProperties) {
        return (request, method, endpoint, body, context) -> {
            request.header(HeaderNames.MCP_API_KEY, mcpProperties.apiKey());
            if (context.get(CORRELATION_ID) instanceof String correlationId) {
                request.header(HeaderNames.CORRELATION_ID, correlationId);
            }
            if (context.get(USER_TOKEN) instanceof String userToken) {
                request.header(HeaderNames.USER_TOKEN, userToken);
            }
            if (context.get(TRACE_HEADERS) instanceof Map<?, ?> traceHeaders) {
                traceHeaders.forEach((name, value) -> request.header(name.toString(), value.toString()));
            }
        };
    }

    /** Fail fast when mcp-server-service cannot be reached (the time for one MCP request is spring.ai.mcp.client.request-timeout). */
    @Bean
    McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> mcpConnectTimeout(McpProperties mcpProperties) {
        return (connectionName, transport) ->
                transport.clientBuilder(HttpClient.newBuilder().connectTimeout(mcpProperties.connectTimeout()));
    }
}
