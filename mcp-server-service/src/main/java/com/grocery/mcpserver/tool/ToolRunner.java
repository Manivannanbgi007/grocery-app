package com.grocery.mcpserver.tool;

import com.grocery.mcpserver.exception.ExternalServiceException;
import com.grocery.mcpserver.exception.LoginRequiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Runs the work of one tool call, the same way for every tool:
 *
 * - logs which tool ran, its result status and how long it took (one INFO line per call);
 * - turns "a service did not answer" into SERVICE_UNAVAILABLE (with a message the customer may read);
 * - turns "no valid customer login" (customer tools) into LOGIN_REQUIRED;
 * - turns any unexpected error into SERVICE_UNAVAILABLE too: the details go to the log, never to the model.
 */
@Slf4j
@Component
public class ToolRunner {

    private static final String UNEXPECTED_ERROR = "This information is not available right now. Please try again later.";

    public <T> ToolResult<T> run(String toolName, Supplier<ToolResult<T>> work) {
        long start = System.nanoTime();
        ToolResult<T> result;
        try {
            result = work.get();
        } catch (ExternalServiceException ex) {
            log.warn("Tool {}: {}", toolName, ex.getMessage(), ex.getCause());
            result = ToolResult.unavailable(ex.getMessage());
        } catch (LoginRequiredException ex) {
            result = ToolResult.loginRequired(ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Tool {} failed unexpectedly", toolName, ex);
            result = ToolResult.unavailable(UNEXPECTED_ERROR);
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        log.info("Tool {} -> {} in {} ms", toolName, result.status(), millis);
        return result;
    }
}
