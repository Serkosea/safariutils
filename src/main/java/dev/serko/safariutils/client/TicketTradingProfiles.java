package dev.serko.safariutils.client;

import dev.serko.safariutils.client.SafariConfig.SparklingConfig;
import dev.serko.safariutils.client.SafariConfig.SparklingConfig.TicketTraderProfile;
import dev.serko.safariutils.data.Critters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared profile validation and slot operations for Ticket Trading UI and automation. */
final class TicketTradingProfiles {
	static final int MAX_PROFILES = 64;
	static final int ROLE_SCHEMA = 2;
	enum HostMode {
		EVERY_RUN("Every Run"), SPARKLING("Selected Sparklings");
		final String label;
		HostMode(String label) { this.label = label; }
		HostMode next() { return values()[(ordinal() + 1) % values().length]; }
	}
	enum GuestMode {
		OFF("Off"), JOIN("Join Party"), SWITCH("Leave & Join");
		final String label;
		GuestMode(String label) { this.label = label; }
		GuestMode next() { return values()[(ordinal() + 1) % values().length]; }
	}

	private TicketTradingProfiles() { }

	static void sanitize(SparklingConfig config) {
		if (config.ticketTradingProfiles == null) config.ticketTradingProfiles = new ArrayList<>();
		if (config.ticketTradingHostQueue == null) config.ticketTradingHostQueue = new ArrayList<>();
		if (config.ticketTradingGuestPlayers == null) config.ticketTradingGuestPlayers = new ArrayList<>();
		long validMask = Critters.allSelectionMask();
		Set<String> seen = new HashSet<>();
		config.ticketTradingProfiles.removeIf(profile -> {
			if (profile == null) return true;
			profile.username = CanonicalPlayerNames.display(profile.username);
			profile.sparklingCritters &= validMask;
			profile.hostInviteMode = hostMode(profile).name();
			profile.guestInviteMode = guestMode(profile).name();
			return profile.username.isEmpty() || !seen.add(normalize(profile.username));
		});
		if (config.ticketTradingProfiles.size() > MAX_PROFILES) {
			config.ticketTradingProfiles.subList(MAX_PROFILES,
				config.ticketTradingProfiles.size()).clear();
		}
		Set<String> queued = new HashSet<>();
		config.ticketTradingHostQueue.removeIf(name -> {
			TicketTraderProfile profile = find(config, name);
			return profile == null || !queued.add(normalize(profile.username));
		});
		config.ticketTradingHostQueue.replaceAll(name -> find(config, name).username);
		Set<String> guests = new HashSet<>();
		config.ticketTradingGuestPlayers.removeIf(name -> {
			TicketTraderProfile profile = find(config, name);
			return profile == null || !guests.add(normalize(profile.username));
		});
		config.ticketTradingGuestPlayers.replaceAll(name -> find(config, name).username);
	}

	static List<TicketTraderProfile> sorted(SparklingConfig config) {
		sanitize(config);
		return config.ticketTradingProfiles.stream()
			.sorted(Comparator.comparing(profile -> profile.username.toLowerCase(Locale.ROOT)))
			.toList();
	}

	static TicketTraderProfile find(SparklingConfig config, String username) {
		String wanted = normalize(username);
		if (wanted.isEmpty() || config.ticketTradingProfiles == null) return null;
		for (TicketTraderProfile profile : config.ticketTradingProfiles) {
			if (profile != null && normalize(profile.username).equals(wanted)) return profile;
		}
		return null;
	}

	static List<TicketTraderProfile> hostQueue(SparklingConfig config) {
		sanitize(config);
		return config.ticketTradingHostQueue.stream().map(name -> find(config, name))
			.filter(java.util.Objects::nonNull).toList();
	}

	static List<TicketTraderProfile> guestPlayers(SparklingConfig config) {
		sanitize(config);
		return config.ticketTradingGuestPlayers.stream().map(name -> find(config, name))
			.filter(java.util.Objects::nonNull)
			.sorted(Comparator.comparing(profile -> profile.username.toLowerCase(Locale.ROOT)))
			.toList();
	}

	static void queue(SparklingConfig config, TicketTraderProfile profile, int index) {
		sanitize(config);
		if (profile == null) return;
		String normalized = normalize(profile.username);
		config.ticketTradingHostQueue.removeIf(name -> normalize(name).equals(normalized));
		int target = Math.clamp(index, 0, config.ticketTradingHostQueue.size());
		config.ticketTradingHostQueue.add(target, profile.username);
	}

	static void removeFromQueue(SparklingConfig config, TicketTraderProfile profile) {
		if (profile == null || config.ticketTradingHostQueue == null) return;
		String normalized = normalize(profile.username);
		config.ticketTradingHostQueue.removeIf(name -> normalize(name).equals(normalized));
	}

	static void move(SparklingConfig config, int from, int to) {
		sanitize(config);
		if (from < 0 || from >= config.ticketTradingHostQueue.size()) return;
		String name = config.ticketTradingHostQueue.remove(from);
		config.ticketTradingHostQueue.add(Math.clamp(to, 0,
			config.ticketTradingHostQueue.size()), name);
	}

	static boolean save(SparklingConfig config, TicketTraderProfile profile,
			String previousName, String username, HostMode hostMode,
			GuestMode guestMode, long critterMask) {
		String display = displayName(username);
		if (display.isEmpty()) return false;
		TicketTraderProfile conflict = find(config, display);
		if (conflict != null && conflict != profile) return false;
		String old = normalize(previousName);
		profile.username = display;
		profile.hostInviteMode = hostMode.name();
		profile.guestInviteMode = guestMode.name();
		profile.sparklingCritters = critterMask & Critters.allSelectionMask();
		if (!config.ticketTradingProfiles.contains(profile)) config.ticketTradingProfiles.add(profile);
		config.ticketTradingHostQueue.replaceAll(name ->
			!old.isEmpty() && normalize(name).equals(old) ? display : name);
		config.ticketTradingGuestPlayers.replaceAll(name ->
			!old.isEmpty() && normalize(name).equals(old) ? display : name);
		return true;
	}

	static boolean applyCanonicalName(SparklingConfig config, TicketTraderProfile profile,
			String canonical) {
		if (profile == null || config.ticketTradingProfiles == null
				|| !config.ticketTradingProfiles.contains(profile)) return false;
		String valid = displayName(canonical);
		if (valid.isEmpty() || profile.username.equals(valid)) return false;
		TicketTraderProfile conflict = find(config, valid);
		if (conflict != null && conflict != profile) return false;
		String previous = normalize(profile.username);
		profile.username = valid;
		config.ticketTradingHostQueue.replaceAll(name ->
			normalize(name).equals(previous) ? valid : name);
		config.ticketTradingGuestPlayers.replaceAll(name ->
			normalize(name).equals(previous) ? valid : name);
		sanitize(config);
		return true;
	}

	static void remove(SparklingConfig config, TicketTraderProfile profile) {
		if (profile == null || config.ticketTradingProfiles == null) return;
		String removed = normalize(profile.username);
		config.ticketTradingProfiles.remove(profile);
		config.ticketTradingHostQueue.removeIf(name -> normalize(name).equals(removed));
		config.ticketTradingGuestPlayers.removeIf(name -> normalize(name).equals(removed));
	}

	static HostMode hostMode(TicketTraderProfile profile) {
		try { return HostMode.valueOf(profile.hostInviteMode); }
		catch (RuntimeException ignored) { return HostMode.EVERY_RUN; }
	}

	static GuestMode guestMode(TicketTraderProfile profile) {
		try { return GuestMode.valueOf(profile.guestInviteMode); }
		catch (RuntimeException ignored) { return GuestMode.OFF; }
	}

	static void setGuestMode(TicketTraderProfile profile, GuestMode mode) {
		if (profile != null) profile.guestInviteMode = mode.name();
	}

	static void enableGuest(SparklingConfig config, TicketTraderProfile profile) {
		sanitize(config);
		if (profile == null || guestListed(config, profile)) return;
		config.ticketTradingGuestPlayers.add(profile.username);
	}

	static void disableGuest(SparklingConfig config, TicketTraderProfile profile) {
		if (profile == null || config.ticketTradingGuestPlayers == null) return;
		String normalized = normalize(profile.username);
		config.ticketTradingGuestPlayers.removeIf(name -> normalize(name).equals(normalized));
	}

	static boolean guestEnabled(SparklingConfig config, TicketTraderProfile profile) {
		return guestListed(config, profile) && guestMode(profile) != GuestMode.OFF;
	}

	private static boolean guestListed(SparklingConfig config, TicketTraderProfile profile) {
		if (profile == null || config.ticketTradingGuestPlayers == null) return false;
		String normalized = normalize(profile.username);
		return config.ticketTradingGuestPlayers.stream()
			.anyMatch(name -> normalize(name).equals(normalized));
	}

	static void setHostMode(TicketTraderProfile profile, HostMode mode) {
		if (profile == null) return;
		profile.hostInviteMode = mode.name();
	}

	static boolean queued(SparklingConfig config, TicketTraderProfile profile) {
		if (profile == null || config.ticketTradingHostQueue == null) return false;
		String normalized = normalize(profile.username);
		return config.ticketTradingHostQueue.stream()
			.anyMatch(name -> normalize(name).equals(normalized));
	}

	static String normalize(String value) {
		return displayName(value).toLowerCase(Locale.ROOT);
	}

	private static String displayName(String value) {
		if (value == null) return "";
		String trimmed = value.trim();
		return trimmed.matches("[A-Za-z0-9_]{1,16}") ? trimmed : "";
	}
}
