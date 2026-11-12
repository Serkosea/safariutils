package dev.serko.safariutils.backend;

/** Bounded, visit-scoped notices. Transport callbacks never write to Minecraft chat. */
final class ConnectionNotices {
    record Notice(String text, boolean recovered) { }
    private String scope = "";
    private String pendingFailure;
    private boolean warned, recovered;

    synchronized void visit(String next) {
        if (scope.equals(next)) return;
        scope = next;
        pendingFailure = null;
        warned = recovered = false;
    }

    synchronized void failure(String reason) {
        if (!scope.isEmpty() && !warned && pendingFailure == null) pendingFailure = reason;
    }

    synchronized Notice poll(boolean ready) {
        if (pendingFailure != null) {
            String reason = pendingFailure;
            pendingFailure = null;
            warned = true;
            return new Notice("Party sync unavailable: " + reason, false);
        }
        if (ready && warned && !recovered) {
            recovered = true;
            return new Notice("Party sync restored.", true);
        }
        return null;
    }

    static String closedReason(int status, String reason) {
        if (status == 1008 && "message rate exceeded".equals(reason)) return reason(429, null);
        if (status == 1008 && "session renewal rejected".equals(reason)) return reason(401, null);
        if (status == 1013) return reason(503, null);
        if (status == 1011) return "server connection error. Retrying automatically.";
        return "connection closed. Retrying automatically.";
    }

    static String reason(int status, Throwable cause) {
        return switch (status) {
            case 429 -> "service request limit reached. Retrying automatically.";
            case 401, 403 -> "session rejected. Retrying automatically.";
            case 426 -> "a mod update is required.";
            case 502, 503, 504 -> "service temporarily unavailable. Retrying automatically.";
            default -> {
                // Inspect only our fixed timeout descriptions; never expose exception text or URLs.
                boolean timeout = cause instanceof java.net.http.HttpTimeoutException
                    || cause != null && java.util.Set.of("Party lease renewal timeout", "Party sync response timeout",
                        "Party sync connection timeout").contains(java.util.Objects.toString(cause.getMessage(), ""));
                yield timeout ? "connection timed out. Retrying automatically."
                    : status > 0 ? "server error (" + status + "). Retrying automatically."
                    : "connection interrupted. Retrying automatically.";
            }
        };
    }
}
