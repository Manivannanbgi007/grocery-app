package com.grocery.inventory.client;

import com.grocery.inventory.constant.HeaderNames;
import com.grocery.inventory.filter.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Passes the current request's correlation id on to the service we call,
 * so one user action can be followed through the logs of every service it touches.
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
