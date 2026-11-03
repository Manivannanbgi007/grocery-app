package com.grocery.gateway.config;

import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

/**
 * Puts the correlation id into the MDC of every log line of a request, like in the other services.
 *
 * The gateway is reactive: one request runs on several threads, so a value in the MDC (a ThreadLocal) would be lost.
 * CorrelationIdFilter therefore stores the id in the Reactor context of the request, and this registration tells
 * Reactor (spring.reactor.context-propagation=auto) to copy it into the MDC on whichever thread runs the request.
 * The trace id and span id are carried the same way by Micrometer Tracing.
 */
@Configuration(proxyBeanMethods = false)
public class CorrelationIdContextConfig {

    /** The key in the Reactor context and in the MDC (the log pattern prints %X{correlationId}). */
    public static final String CORRELATION_ID = "correlationId";

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(CORRELATION_ID,
                () -> MDC.get(CORRELATION_ID),
                value -> MDC.put(CORRELATION_ID, value),
                () -> MDC.remove(CORRELATION_ID));
    }
}
