package dev.serko.safariutils.api;

/** Preserves HTTP failure information without exposing response bodies to chat. */
public final class ServiceRequestException extends java.io.IOException {
    private final int status;
    private final long retrySeconds;
    private final String limitReason;
    public String limitReason() { return limitReason; }
    public ServiceRequestException(String detail, int status, long retrySeconds) {
        this(detail, status, retrySeconds, "unspecified");
    }
    public ServiceRequestException(String detail, int status, long retrySeconds, String limitReason) {
        super(detail);
        this.status = status;
        this.limitReason = limitReason;
        this.retrySeconds = Math.max(0, retrySeconds);
    }
    public int status() { return status; }
    public long retrySeconds() { return retrySeconds; }
}
