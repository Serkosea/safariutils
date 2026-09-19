package dev.serko.safariutils.api;

/** Automatic party-loading state and whether the public manual fallback is needed. */
public record PartyRefreshStatus(String error, boolean manualFallback) {
}
