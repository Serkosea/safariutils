package dev.serko.safariutils.api;

/** Preserves HTTP failure information without exposing response bodies to chat. */
public final class ServiceRequestException extends java.io.IOException {
    private final int status;
    private final long retrySeconds;
    public ServiceRequestException(String detail, int status, long retrySeconds) {
        super(detail);
        this.status = status;
        this.retrySeconds = Math.max(0, retrySeconds);
    }
    public int status() { return status; }
    public long retrySeconds() { return retrySeconds; }
}
