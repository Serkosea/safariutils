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
	static final int ACTIVE_SLOTS = 3;
	static final int TOTAL_SLOTS = 6;

	private TicketTradingProfiles() { }

	static void sanitize(SparklingConfig config) {
		if (config.ticketTradingProfiles == null) config.ticketTradingProfiles = new ArrayList<>();
		long validMask = Critters.allSelectionMask();
		Set<String> seen = new HashSet<>();
		config.ticketTradingProfiles.removeIf(profile -> {
			if (profile == null) return true;
			profile.username = CanonicalPlayerNames.display(profile.username);
			profile.sparklingCritters &= validMask;
			return profile.username.isEmpty() || !seen.add(normalize(profile.username));
		});
		for (int index = 0; index < TOTAL_SLOTS; index++) {
			String slot = slotName(config, index);
			TicketTraderProfile profile = find(config, slot);
			setSlotName(config, index, profile == null ? "" : profile.username);
		}
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

	static TicketTraderProfile slot(SparklingConfig config, int index) {
		return find(config, slotName(config, index));
	}

	static void assign(SparklingConfig config, int index, TicketTraderProfile profile) {
		if (index < 0 || index >= TOTAL_SLOTS) return;
		String name = profile == null ? "" : profile.username;
		String normalized = normalize(name);
		for (int other = 0; other < TOTAL_SLOTS; other++) {
			if (other != index && normalize(slotName(config, other)).equals(normalized)) {
				setSlotName(config, other, "");
			}
		}
		setSlotName(config, index, name);
	}

	static boolean save(SparklingConfig config, TicketTraderProfile profile,
			String previousName, String username, boolean sparklingOnly, long critterMask) {
		String display = displayName(username);
		if (display.isEmpty()) return false;
		TicketTraderProfile conflict = find(config, display);
		if (conflict != null && conflict != profile) return false;
		String old = normalize(previousName);
		profile.username = display;
		profile.sparklingOnly = sparklingOnly;
		profile.sparklingCritters = critterMask & Critters.allSelectionMask();
		if (!config.ticketTradingProfiles.contains(profile)) config.ticketTradingProfiles.add(profile);
		for (int index = 0; index < TOTAL_SLOTS; index++) {
			if (!old.isEmpty() && normalize(slotName(config, index)).equals(old)) {
				setSlotName(config, index, display);
			}
		}
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
		for (int index = 0; index < TOTAL_SLOTS; index++) {
			if (normalize(slotName(config, index)).equals(previous)) {
				setSlotName(config, index, valid);
			}
		}
		sanitize(config);
		return true;
	}

	static void remove(SparklingConfig config, TicketTraderProfile profile) {
		if (profile == null || config.ticketTradingProfiles == null) return;
		String removed = normalize(profile.username);
		config.ticketTradingProfiles.remove(profile);
		for (int index = 0; index < TOTAL_SLOTS; index++) {
			if (normalize(slotName(config, index)).equals(removed)) setSlotName(config, index, "");
		}
	}

	static String slotName(SparklingConfig config, int index) {
		return switch (index) {
			case 0 -> config.ticketTradingSlot1;
			case 1 -> config.ticketTradingSlot2;
			case 2 -> config.ticketTradingSlot3;
			case 3 -> config.ticketTradingBackupSlot1;
			case 4 -> config.ticketTradingBackupSlot2;
			case 5 -> config.ticketTradingBackupSlot3;
			default -> "";
		};
	}

	private static void setSlotName(SparklingConfig config, int index, String name) {
		String value = name == null ? "" : name;
		switch (index) {
			case 0 -> config.ticketTradingSlot1 = value;
			case 1 -> config.ticketTradingSlot2 = value;
			case 2 -> config.ticketTradingSlot3 = value;
			case 3 -> config.ticketTradingBackupSlot1 = value;
			case 4 -> config.ticketTradingBackupSlot2 = value;
			case 5 -> config.ticketTradingBackupSlot3 = value;
			default -> { }
		}
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
