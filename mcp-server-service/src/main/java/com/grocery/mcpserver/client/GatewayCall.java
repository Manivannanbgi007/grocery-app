package com.grocery.mcpserver.client;

import com.grocery.mcpserver.client.dto.GatewayResponse;
import com.grocery.mcpserver.exception.ExternalServiceException;
import com.grocery.mcpserver.exception.LoginRequiredException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Runs one call to the API gateway and unwraps the standard response, the same way for every client.
 *
 * - success -> the "data" of the response
 * - 401 / 403 (calls with a user's token only: the login expired, or the account may not see this, e.g. a vendor who
 *   logged in before the store was approved) -> LoginRequiredException, which the tools turn into LOGIN_REQUIRED
 * - any other failure (service down, timeout, 5xx, an unexpected 4xx) -> ExternalServiceException,
 *   which the tools turn into SERVICE_UNAVAILABLE
 * - optionalDataOf only: 404 -> Optional.empty() ("no such product")
 *
 * Only reads: no tool changes data (the customer's click on a card does, from the browser).
 */
final class GatewayCall {

    private GatewayCall() {
    }

    /**
     * @param what what is being asked, for the message: "The product catalog" -> "The product catalog is not available ..."
     */
    static <T> T dataOf(String what, Supplier<GatewayResponse<T>> request) {
        GatewayResponse<T> response;
        try {
            response = request.get();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw new LoginRequiredException(
                    "The login has expired, or this account may not see this information. Ask them to log in again on the website.");
        } catch (RestClientException ex) {
            throw unavailable(what, ex);
        }
        if (response == null || !response.success()) {
            throw unavailable(what, null);
        }
        return response.data();
    }

    /** Like dataOf, but a 404 ("not found") gives Optional.empty() instead of an exception. */
    static <T> Optional<T> optionalDataOf(String what, Supplier<GatewayResponse<T>> request) {
        try {
            return Optional.ofNullable(dataOf(what, request));
        } catch (ExternalServiceException ex) {
            if (ex.getCause() instanceof HttpClientErrorException.NotFound) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private static ExternalServiceException unavailable(String what, Throwable cause) {
        return new ExternalServiceException(what + " is not available right now. Please try again later.", cause);
    }
}
