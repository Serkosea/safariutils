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

    static String limitCode(String code) {
        return switch (code == null ? "" : code) {
            case "sync-reconnect", "sync-concurrent", "sync-global", "sync-cooldown",
                 "upstream-account", "lookup-concurrent", "lookup-window", "sync-room-full", "ingress-rate", "service-rate", "client-rate",
                 "auth-rate", "auth-account-rate", "service-burst", "burst-challenge", "burst-session", "burst-sync", "burst-data", "storage-budget" -> code;
            default -> "unspecified";
        };
    }

    static String limited(String code) {
        String description = switch (limitCode(code)) {
            case "sync-reconnect" -> "too many recent connection attempts";
            case "sync-concurrent" -> "your account still has the maximum active connections";
            case "sync-global" -> "the service has reached its active connection limit";
            case "sync-room-full" -> "this Safari lobby has reached its connection limit";
            case "lookup-window" -> "your player lookup allowance for this minute was reached";
            case "lookup-concurrent" -> "too many player lookups are already running for your account";
            case "sync-cooldown" -> "temporary cooldown after repeated connection rejections";
            case "upstream-account" -> "your account's player-lookup request limit was reached";
            case "ingress-rate" -> "too many recent requests from your network";
            case "client-rate" -> "too many recent requests from your account";
            case "auth-rate" -> "too many recent authentication requests from your network";
            case "auth-account-rate" -> "too many recent authentication requests from your account";
            case "service-rate", "service-burst" -> "the service's short-term request limit was reached";
            case "burst-challenge" -> "the service authentication challenge limit was reached";
            case "burst-session" -> "the service session creation limit was reached";
            case "burst-sync" -> "the service party-sync admission limit was reached";
            case "burst-data" -> "the service lookup request limit was reached";
            case "storage-budget" -> "the service's storage allowance was reached";
            default -> "request limit reached; server did not specify which limit";
        };
        return description + ". Retrying automatically.";
    }

    static String closedReason(int status, String reason) {
        if (status == 1008 && "message rate exceeded".equals(reason)) return "too many messages on this connection. Retrying automatically after the message limit.";
        if (status == 1008 && "session renewal rejected".equals(reason)) return reason(401, null);
        if (status == 1008 && "lease expired".equals(reason)) return "the connection lease expired. Reconnecting.";
        if (status == 1013 && "storage-budget".equals(reason)) return limited(reason);
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
