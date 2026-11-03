package com.grocery.store.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * Centralized logging with Spring AOP: the ONE place that logs the outcome and the duration of the work this service
 * does. Controllers, services and Kafka consumers do not need their own "started / finished / failed" log lines.
 *
 * <pre>
 * Kind      Which methods (by package or annotation)                OK      SLOW (*)  FAILED
 * API       classes in a "controller" package (REST endpoints)      INFO    WARN      WARN
 * JOB       methods with @Scheduled                                 INFO    WARN      WARN
 * EVENT     classes in "messaging.consumer" (Kafka listeners)       DEBUG   WARN      WARN
 * INTERNAL  classes in "service" or "client", and @Loggable         DEBUG   WARN      -   (the API / JOB / EVENT line reports it)
 *
 * (*) slower than app.logging.slow-call-threshold (application.yml, default 1 second)
 * </pre>
 *
 * Example lines:
 * <pre>
 * API POST /api/v1/cart/items CartController.addItem OK in 35 ms
 * SLOW INTERNAL ProductCatalogClient.getProduct took 1240 ms (limit 1000 ms)
 * API POST /api/v1/cart/items CartController.addItem FAILED in 12 ms: CartStoreConflictException: Your cart has ...
 * </pre>
 *
 * Each line also carries the key-value pairs kind, method, outcome and durationMs. The console shows only the text;
 * in Grafana Loki they are fields to filter on, e.g. {app="cart-service"} | outcome="FAILED".
 *
 * NEVER logged: method arguments and return values. They can contain passwords, tokens, addresses or payment details.
 * When you need an id in the log, write it in your own code ("Order id=18 placed").
 *
 * Good to know: Spring AOP works with proxies. It only sees calls from OUTSIDE a bean (a method calling another
 * method of the same class is not logged again), and only public methods of Spring beans.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // outermost: the measured time includes the database transaction (commit)
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /** Exception messages are cut here, so one huge message cannot flood the logs. */
    private static final int MAX_MESSAGE_LENGTH = 300;

    /** The kinds of calls, with the log level of a successful, fast call. */
    enum Kind {
        API(Level.INFO), JOB(Level.INFO), EVENT(Level.DEBUG), INTERNAL(Level.DEBUG);

        private final Level successLevel;

        Kind(Level successLevel) {
            this.successLevel = successLevel;
        }
    }

    private final long slowCallMillis;

    public LoggingAspect(@Value("${app.logging.slow-call-threshold:1s}") Duration slowCallThreshold) {
        this.slowCallMillis = slowCallThreshold.toMillis();
    }

    // ----- WHERE: pointcuts = which methods the logging runs around -----

    @Pointcut("within(com.grocery..controller..*)")
    void restEndpoint() {
    }

    @Pointcut("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    void scheduledJob() {
    }

    @Pointcut("within(com.grocery..messaging.consumer..*)")
    void eventHandler() {
    }

    @Pointcut("within(com.grocery..service..*) || within(com.grocery..client..*)"
            + " || @within(com.grocery.store.logging.Loggable) || @annotation(com.grocery.store.logging.Loggable)")
    void internalCall() {
    }

    // ----- WHAT: advice = the code that runs around those methods (each method gets exactly one of them) -----

    @Around("restEndpoint()")
    public Object logRestEndpoint(ProceedingJoinPoint call) throws Throwable {
        return logCall(call, Kind.API);
    }

    @Around("scheduledJob() && !restEndpoint()")
    public Object logScheduledJob(ProceedingJoinPoint call) throws Throwable {
        return logCall(call, Kind.JOB);
    }

    @Around("eventHandler() && !restEndpoint() && !scheduledJob()")
    public Object logEventHandler(ProceedingJoinPoint call) throws Throwable {
        return logCall(call, Kind.EVENT);
    }

    @Around("internalCall() && !restEndpoint() && !scheduledJob() && !eventHandler()")
    public Object logInternalCall(ProceedingJoinPoint call) throws Throwable {
        return logCall(call, Kind.INTERNAL);
    }

    /** Runs the method, measures it, writes one log line and passes the result (or the exception) on unchanged. */
    private Object logCall(ProceedingJoinPoint call, Kind kind) throws Throwable {
        String method = ClassUtils.getUserClass(call.getTarget()).getSimpleName() + "." + call.getSignature().getName();
        String description = kind == Kind.API ? httpRequest() + method : method;
        long start = System.nanoTime();
        try {
            Object result = call.proceed();
            long millis = millisSince(start);
            if (millis >= slowCallMillis) {
                log(Level.WARN, kind, method, "SLOW", millis, "SLOW {} {} took {} ms (limit {} ms)",
                        kind, description, millis, slowCallMillis);
            } else {
                log(kind.successLevel, kind, method, "OK", millis, "{} {} OK in {} ms", kind, description, millis);
            }
            return result;
        } catch (Throwable ex) {
            if (kind != Kind.INTERNAL) {
                long millis = millisSince(start);
                log(Level.WARN, kind, method, "FAILED", millis, "{} {} FAILED in {} ms: {}",
                        kind, description, millis, describe(ex));
            }
            throw ex;   // the exception handler (or Kafka / the scheduler) still decides what happens with it
        }
    }

    /**
     * The text for the console, plus key-value pairs for Grafana Loki (SLF4J's fluent API; logback-spring.xml sends
     * them to Loki as structured metadata).
     */
    private static void log(Level level, Kind kind, String method, String outcome, long millis,
                            String message, Object... arguments) {
        log.atLevel(level)
                .addKeyValue("kind", kind)
                .addKeyValue("method", method)
                .addKeyValue("outcome", outcome)
                .addKeyValue("durationMs", millis)
                .log(message, arguments);
    }

    /** "POST /api/v1/cart/items " for the current HTTP request; no query string (it can contain search texts). */
    private static String httpRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getMethod() + " " + request.getRequestURI() + " ";
        }
        return "";
    }

    /** "CartStoreConflictException: Your cart has ..." - the class and the (shortened) message, no stack trace. */
    private static String describe(Throwable ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }
        return ex.getClass().getSimpleName() + ": " + message;
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
