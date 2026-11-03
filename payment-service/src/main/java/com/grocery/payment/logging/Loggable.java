package com.grocery.payment.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Asks LoggingAspect to log a Spring bean that is NOT in a controller, service, client or messaging.consumer package
 * (those are logged anyway): the duration of every call at DEBUG, and a WARN line when a call is slow.
 *
 * <pre>
 * &#64;Loggable                    // on a class: all its public methods
 * &#64;Component
 * public class InvoicePdfRenderer { ... }
 * </pre>
 *
 * It also works on single methods. Arguments and return values are never logged.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Loggable {
}
