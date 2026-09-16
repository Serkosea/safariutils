package dev.serko.safariutils.api;

/** Current eligibility for a manual private-API party refresh. */
public record PartyRefreshStatus(int players, long refreshAvailableAt, String error) {
	public boolean available() {
		return error == null && System.currentTimeMillis() >= refreshAvailableAt;
	}
}
