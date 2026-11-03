package com.grocery.mcpserver.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What every tool answers, so the model always gets the same shape:
 *
 * <pre>
 * { "status": "OK",        "message": "Found 2 products.",                 "data": [ ... ] }
 * { "status": "NOT_FOUND", "message": "No product of the shop matches ..." }
 * </pre>
 *
 * The status words are explained in the system prompt of mcp-client-service: with NOT_FOUND the assistant must say
 * it found nothing (never guess), with SERVICE_UNAVAILABLE it must say "try again later".
 * Tools never throw: a clear status is easier for a small model than an error.
 *
 * @param data null unless the status is OK (or CHOOSE: the products to choose from, when the question is which one)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResult<T>(Status status, String message, T data) {

    public enum Status {
        /** the data is there */
        OK,
        /** nothing matches (the answer must say so, not guess) */
        NOT_FOUND,
        /** the arguments were wrong; the message says how to call the tool correctly */
        INVALID_INPUT,
        /** a service did not answer; the customer should try again later */
        SERVICE_UNAVAILABLE,
        /** a customer tool without a valid customer login; the customer should log in on the website */
        LOGIN_REQUIRED,
        /** the shop said no to a change (out of stock, another store's cart ...); the message says why, in plain words */
        REFUSED,
        /** the customer must decide first (never guess): which product (the data lists them) or how much; the message asks */
        CHOOSE
    }

    public static <T> ToolResult<T> ok(String message, T data) {
        return new ToolResult<>(Status.OK, message, data);
    }

    public static <T> ToolResult<T> notFound(String message) {
        return new ToolResult<>(Status.NOT_FOUND, message, null);
    }

    public static <T> ToolResult<T> invalidInput(String message) {
        return new ToolResult<>(Status.INVALID_INPUT, message, null);
    }

    public static <T> ToolResult<T> unavailable(String message) {
        return new ToolResult<>(Status.SERVICE_UNAVAILABLE, message, null);
    }

    public static <T> ToolResult<T> loginRequired(String message) {
        return new ToolResult<>(Status.LOGIN_REQUIRED, message, null);
    }

    public static <T> ToolResult<T> refused(String message) {
        return new ToolResult<>(Status.REFUSED, message, null);
    }

    /** @param options the products to choose from; null when the question is how much */
    public static <T> ToolResult<T> choose(String message, T options) {
        return new ToolResult<>(Status.CHOOSE, message, options);
    }
}
