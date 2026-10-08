package io.github.lehamohan.payments.client;

/** Raised when an API call could not complete at the transport level (timeouts, resets) after all retries. */
public class ApiTransportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int attempts;

    public ApiTransportException(String operation, int attempts, Throwable cause) {
        super(operation + " failed after " + attempts + " attempt(s): " + cause, cause);
        this.attempts = attempts;
    }

    public int attempts() {
        return attempts;
    }
}
