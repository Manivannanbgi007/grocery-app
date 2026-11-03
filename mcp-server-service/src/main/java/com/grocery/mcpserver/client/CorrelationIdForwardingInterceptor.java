package com.grocery.mcpserver.client;

import com.grocery.mcpserver.constant.HeaderNames;
import com.grocery.mcpserver.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Passes the current request's correlation id on to the API gateway,
 * so one chat question can be followed through the logs of every service it touches.
 */
public class CorrelationIdForwardingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            request.getHeaders().set(HeaderNames.CORRELATION_ID, correlationId);
        }
        return execution.execute(request, body);
    }
}
