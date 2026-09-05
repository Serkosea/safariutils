package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/** Stable sound IDs with an independently alphabetized presentation order. */
public final class AlertSounds {
	public record Choice(int id, String label) { }
	private record Note(SoundEvent sound, int delay, float pitch) { }
	private record Pending(SoundEvent sound, long dueTick, float volume, float pitch) { }

	private static final List<Choice> CHOICES = List.of(
		new Choice(0, "Challenge Complete"),
		new Choice(1, "Player Level Up"),
		new Choice(2, "Experience Orb"),
		new Choice(3, "Amethyst Chime"),
		new Choice(4, "Note Block Pling"),
		new Choice(5, "Note Block Bell"),
		new Choice(6, "Beacon Activate"),
		new Choice(7, "Button Click"),
		new Choice(8, "Totem Used"),
		new Choice(9, "Note Block Chime"),
		new Choice(10, "Note Block Xylophone"),
		new Choice(11, "Note Block Iron Xylophone"),
		new Choice(12, "Note Block Cow Bell"),
		new Choice(13, "Note Block Flute"),
		new Choice(14, "Note Block Harp"),
		new Choice(15, "Note Block Banjo"),
		new Choice(16, "Note Block Didgeridoo"),
		new Choice(17, "Enchanting Table"),
		new Choice(18, "Ender Chest Open"),
		new Choice(19, "Firework Twinkle"),
		new Choice(20, "Note Block Bass"),
		new Choice(21, "Note Block Bass Drum"),
		new Choice(22, "Note Block Bit"),
		new Choice(23, "Note Block Guitar"),
		new Choice(24, "Note Block Hi-Hat"),
		new Choice(25, "Note Block Snare"),
		new Choice(26, "Melody: Ascending Chime"),
		new Choice(27, "Melody: Celebration"),
		new Choice(28, "Melody: Gentle Arpeggio"),
		new Choice(29, "Melody: Major Fanfare"),
		new Choice(30, "Melody: Mystery"),
		new Choice(31, "Melody: Safari Adventure"),
		new Choice(32, "Melody: Success"),
		new Choice(33, "Melody: Warning Pulse"),
		new Choice(34, "Anvil Land"),
		new Choice(35, "Arrow Hit"),
		new Choice(36, "Bell Ring"),
		new Choice(37, "Brewing Complete"),
		new Choice(38, "Dragon Growl"),
		new Choice(39, "Evoker Summon"),
		new Choice(40, "Firework Blast"),
		new Choice(41, "Item Pickup"),
		new Choice(42, "Lightning Thunder"),
		new Choice(43, "Portal Travel"),
		new Choice(44, "Melody: Bright Discovery"),
		new Choice(45, "Melody: Canyon Call"),
		new Choice(46, "Melody: Enchanted Waltz"),
		new Choice(47, "Melody: Rare Find"),
		new Choice(48, "Melody: Sparkle Cascade"),
		new Choice(49, "Melody: Victory March"),
		new Choice(50, "Melody: Clockwork Waltz"),
		new Choice(51, "Melody: Desert Caravan"),
		new Choice(52, "Melody: Moonlit Lullaby"),
		new Choice(53, "Melody: Ocean Voyage"),
		new Choice(54, "Melody: Playful Steps"),
		new Choice(55, "Melody: Royal Arrival")
	);
	private static final List<Choice> ALPHABETICAL = CHOICES.stream()
		.sorted(Comparator.comparing(Choice::label, String.CASE_INSENSITIVE_ORDER)).toList();
	private static final String[] LABELS = labelsById();
	private static final List<Pending> PENDING = new ArrayList<>();
	private static final List<Pending> SPARKLING_PENDING = new ArrayList<>();
	private static long tick;
	private static final ThreadLocal<Boolean> PLAYING_ALERT = ThreadLocal.withInitial(() -> false);

	/** Identifies our playback call, not the vanilla sound ID another source might use. */
	public static boolean playingAlert() {
		return PLAYING_ALERT.get();
	}

	private static void playNote(Minecraft client, SoundEvent sound, float volume, float pitch) {
		boolean previous = PLAYING_ALERT.get();
		PLAYING_ALERT.set(true);
		try {
			client.player.playSound(sound, volume, pitch);
		} finally {
			PLAYING_ALERT.set(previous);
		}
	}

	private AlertSounds() {
	}

	public static List<Choice> alphabetical() {
		return ALPHABETICAL;
	}

	public static String label(int id) {
		return id >= 0 && id < LABELS.length && LABELS[id] != null
			? LABELS[id] : CHOICES.getFirst().label;
	}

	private static String[] labelsById() {
		int max = CHOICES.stream().mapToInt(Choice::id).max().orElse(0);
		String[] labels = new String[max + 1];
		for (Choice choice : CHOICES) labels[choice.id] = choice.label;
		return labels;
	}

	public static void play(Minecraft client, int id, float volume, float pitch) {
		if (client.player == null || volume <= 0f) return;
		// Minecraft gives a single sound little additional audible gain above 1.
		// Split larger configured values into full-volume layers plus a remainder so
		// alerts and picker previews use the same perceptible volume scale.
		int layers = Math.max(1, (int) Math.ceil(volume));
		List<Note> melody = melody(id);
		if (melody.isEmpty()) {
			for (int layer = 0; layer < layers; layer++) {
				float layerVolume = Math.min(1f, volume - layer);
				playNote(client, sound(id), layerVolume, pitch);
			}
			return;
		}
		for (Note note : melody) {
			for (int layer = 0; layer < layers; layer++) {
				float layerVolume = Math.min(1f, volume - layer);
				PENDING.add(new Pending(note.sound, tick + note.delay, layerVolume, pitch * note.pitch));
			}
		}
	}

	/** Stops an earlier preview so rapidly auditioning melodies stays intelligible. */
	public static void preview(Minecraft client, int id, float volume, float pitch) {
		PENDING.clear();
		play(client, id, volume, pitch);
	}

	/** Dedicated five-second catch melody; intentionally absent from normal sound choices. */
	public static void playSparklingCall(Minecraft client) {
		if (client.player == null) return;
		SPARKLING_PENDING.clear();
		SoundEvent chime = SoundEvents.NOTE_BLOCK_CHIME.value();
		float[] pitches = {
			0.75f, 0.94f, 1.12f, 1.50f,
			0.84f, 1.12f, 1.26f, 1.68f,
			0.94f, 1.26f, 1.50f, 1.88f,
			1.12f, 1.50f, 1.68f, 2.00f,
			1.50f, 1.88f, 2.00f
		};
		int[] delays = {0, 5, 10, 16, 22, 27, 33, 39, 45, 50, 56, 62, 68, 74, 80, 86, 91, 95, 98};
		for (int note = 0; note < pitches.length; note++) {
			// A few local layers keep the melody bright without recreating the old
			// 150-sound burst or exposing it as a selectable ordinary alert sound.
			for (int layer = 0; layer < 6; layer++) {
				SPARKLING_PENDING.add(new Pending(chime, tick + delays[note], 1f, pitches[note]));
			}
		}
	}

	/** Loud, layered seven-second score reserved for the warned extreme catch effect. */
	public static void playExtremeSparklingCall(Minecraft client) {
		if (client.player == null) return;
		SPARKLING_PENDING.clear();
		SoundEvent chime = SoundEvents.NOTE_BLOCK_CHIME.value();
		SoundEvent bell = SoundEvents.NOTE_BLOCK_BELL.value();
		SoundEvent pling = SoundEvents.NOTE_BLOCK_PLING.value();
		float[] scale = {0.63f, 0.75f, 0.84f, 0.94f, 1.12f, 1.26f, 1.50f, 1.68f, 1.88f, 2.00f};
		for (int step = 0; step < 34; step++) {
			SoundEvent sound = step % 5 == 0 ? bell : step % 3 == 0 ? pling : chime;
			float pitch = scale[Math.floorMod(step * 3 + step / 6, scale.length)];
			long due = tick + step * 4L;
			for (int layer = 0; layer < 4; layer++) {
				SPARKLING_PENDING.add(new Pending(sound, due, 1f, pitch));
			}
			if (step == 0 || step == 12 || step == 24 || step == 33) {
				SPARKLING_PENDING.add(new Pending(SoundEvents.FIREWORK_ROCKET_BLAST, due, 1f, 1.25f));
				SPARKLING_PENDING.add(new Pending(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, due, 1f, 1.35f));
			}
		}
	}

	/** Advances multi-note alert sounds without creating timers or worker threads. */
	public static void tick() {
		tick++;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			PENDING.clear();
			SPARKLING_PENDING.clear();
			return;
		}
		drain(client, PENDING);
		drain(client, SPARKLING_PENDING);
	}

	private static void drain(Minecraft client, List<Pending> sounds) {
		for (Iterator<Pending> iterator = sounds.iterator(); iterator.hasNext();) {
			Pending pending = iterator.next();
			if (pending.dueTick > tick) continue;
			playNote(client, pending.sound, pending.volume, pending.pitch);
			iterator.remove();
		}
	}

	private static List<Note> melody(int id) {
		SoundEvent harp = SoundEvents.NOTE_BLOCK_HARP.value();
		SoundEvent bell = SoundEvents.NOTE_BLOCK_BELL.value();
		SoundEvent chime = SoundEvents.NOTE_BLOCK_CHIME.value();
		SoundEvent pling = SoundEvents.NOTE_BLOCK_PLING.value();
		SoundEvent xylophone = SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
		SoundEvent bass = SoundEvents.NOTE_BLOCK_BASS.value();
		SoundEvent flute = SoundEvents.NOTE_BLOCK_FLUTE.value();
		SoundEvent guitar = SoundEvents.NOTE_BLOCK_GUITAR.value();
		SoundEvent banjo = SoundEvents.NOTE_BLOCK_BANJO.value();
		return switch (id) {
			case 26 -> List.of(new Note(chime, 0, 0.75f), new Note(chime, 4, 0.94f),
				new Note(chime, 8, 1.12f), new Note(chime, 12, 1.5f), new Note(chime, 18, 1.88f));
			case 27 -> List.of(new Note(harp, 0, 1f), new Note(harp, 3, 1f),
				new Note(harp, 7, 1.5f), new Note(harp, 12, 1.26f), new Note(harp, 16, 1.5f),
				new Note(harp, 23, 2f));
			case 28 -> List.of(new Note(harp, 0, 0.75f), new Note(harp, 7, 1.12f),
				new Note(harp, 14, 1.5f), new Note(harp, 21, 1.12f), new Note(harp, 28, 0.94f),
				new Note(harp, 35, 0.75f));
			case 29 -> List.of(new Note(bell, 0, 0.75f), new Note(bell, 3, 0.75f),
				new Note(bell, 8, 1.5f), new Note(bell, 14, 2f));
			case 30 -> List.of(new Note(chime, 0, 1f), new Note(chime, 7, 0.75f),
				new Note(chime, 13, 0.84f), new Note(chime, 22, 0.63f), new Note(chime, 30, 0.94f));
			case 31 -> List.of(new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0, 0.75f),
				new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 5, 1.12f),
				new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 11, 0.94f),
				new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 16, 1.5f),
				new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 24, 1.12f),
				new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 31, 1.88f));
			case 32 -> List.of(new Note(xylophone, 0, 1f), new Note(xylophone, 4, 1.5f),
				new Note(xylophone, 10, 2f));
			case 33 -> List.of(new Note(bass, 0, 0.7f), new Note(bass, 6, 0.7f),
				new Note(bass, 16, 0.9f), new Note(bass, 22, 0.9f));
			case 44 -> List.of(new Note(pling, 0, 1f), new Note(pling, 5, 1.5f),
				new Note(pling, 9, 1.26f), new Note(pling, 16, 2f));
			case 45 -> List.of(new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0, 1.5f),
				new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 7, 1.12f),
				new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 14, 0.94f),
				new Note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 23, 0.7f));
			case 46 -> List.of(new Note(harp, 0, 0.75f), new Note(harp, 4, 1.12f),
				new Note(harp, 8, 1.5f), new Note(harp, 14, 0.84f), new Note(harp, 18, 1.26f),
				new Note(harp, 22, 1.68f), new Note(harp, 28, 1f), new Note(harp, 32, 1.5f),
				new Note(harp, 36, 2f));
			case 47 -> List.of(new Note(xylophone, 0, 1.12f), new Note(xylophone, 7, 1.88f),
				new Note(xylophone, 15, 1.5f));
			case 48 -> List.of(new Note(chime, 0, 2f), new Note(chime, 3, 1.88f),
				new Note(chime, 6, 1.68f), new Note(chime, 9, 1.5f), new Note(chime, 12, 1.26f),
				new Note(chime, 15, 1.12f), new Note(chime, 18, 0.94f), new Note(chime, 22, 0.75f));
			case 49 -> List.of(new Note(bell, 0, 0.75f), new Note(bell, 3, 0.75f),
				new Note(bell, 7, 1.12f), new Note(bell, 12, 1.5f), new Note(bell, 16, 1.12f),
				new Note(bell, 20, 1.5f), new Note(bell, 27, 2f));
			case 50 -> List.of(new Note(xylophone, 0, 1f), new Note(xylophone, 4, 1.26f),
				new Note(xylophone, 9, 1.5f), new Note(xylophone, 14, 1.26f),
				new Note(xylophone, 20, 1.68f), new Note(xylophone, 27, 1.5f));
			case 51 -> List.of(new Note(guitar, 0, 0.75f), new Note(guitar, 7, 1.12f),
				new Note(guitar, 11, 0.94f), new Note(guitar, 19, 1.26f),
				new Note(guitar, 26, 0.84f), new Note(guitar, 34, 1.12f));
			case 52 -> List.of(new Note(flute, 0, 1.5f), new Note(flute, 10, 1.26f),
				new Note(flute, 20, 1.12f), new Note(flute, 32, 0.94f),
				new Note(flute, 44, 1.12f), new Note(flute, 56, 0.75f));
			case 53 -> List.of(new Note(chime, 0, 0.63f), new Note(chime, 8, 0.84f),
				new Note(chime, 17, 1.12f), new Note(chime, 25, 0.94f),
				new Note(chime, 36, 1.26f), new Note(chime, 48, 1.5f));
			case 54 -> List.of(new Note(banjo, 0, 1.5f), new Note(banjo, 3, 1.88f),
				new Note(banjo, 8, 1.26f), new Note(banjo, 13, 1.68f),
				new Note(banjo, 17, 1.12f), new Note(banjo, 23, 2f));
			case 55 -> List.of(new Note(bell, 0, 0.75f), new Note(bell, 5, 1f),
				new Note(bell, 10, 1.26f), new Note(bell, 18, 1.5f),
				new Note(bell, 24, 1.26f), new Note(bell, 31, 1.68f),
				new Note(bell, 40, 2f));
			default -> List.of();
		};
	}

	private static SoundEvent sound(int id) {
		return switch (id) {
			case 1 -> SoundEvents.PLAYER_LEVELUP;
			case 2 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
			case 3 -> SoundEvents.AMETHYST_BLOCK_CHIME;
			case 4 -> SoundEvents.NOTE_BLOCK_PLING.value();
			case 5 -> SoundEvents.NOTE_BLOCK_BELL.value();
			case 6 -> SoundEvents.BEACON_ACTIVATE;
			case 7 -> SoundEvents.UI_BUTTON_CLICK.value();
			case 8 -> SoundEvents.TOTEM_USE;
			case 9 -> SoundEvents.NOTE_BLOCK_CHIME.value();
			case 10 -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
			case 11 -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();
			case 12 -> SoundEvents.NOTE_BLOCK_COW_BELL.value();
			case 13 -> SoundEvents.NOTE_BLOCK_FLUTE.value();
			case 14 -> SoundEvents.NOTE_BLOCK_HARP.value();
			case 15 -> SoundEvents.NOTE_BLOCK_BANJO.value();
			case 16 -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
			case 17 -> SoundEvents.ENCHANTMENT_TABLE_USE;
			case 18 -> SoundEvents.ENDER_CHEST_OPEN;
			case 19 -> SoundEvents.FIREWORK_ROCKET_TWINKLE;
			case 20 -> SoundEvents.NOTE_BLOCK_BASS.value();
			case 21 -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
			case 22 -> SoundEvents.NOTE_BLOCK_BIT.value();
			case 23 -> SoundEvents.NOTE_BLOCK_GUITAR.value();
			case 24 -> SoundEvents.NOTE_BLOCK_HAT.value();
			case 25 -> SoundEvents.NOTE_BLOCK_SNARE.value();
			case 34 -> SoundEvents.ANVIL_LAND;
			case 35 -> SoundEvents.ARROW_HIT_PLAYER;
			case 36 -> SoundEvents.BELL_BLOCK;
			case 37 -> SoundEvents.BREWING_STAND_BREW;
			case 38 -> SoundEvents.ENDER_DRAGON_GROWL;
			case 39 -> SoundEvents.EVOKER_PREPARE_SUMMON;
			case 40 -> SoundEvents.FIREWORK_ROCKET_BLAST;
			case 41 -> SoundEvents.ITEM_PICKUP;
			case 42 -> SoundEvents.LIGHTNING_BOLT_THUNDER;
			case 43 -> SoundEvents.PORTAL_TRAVEL;
			default -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
		};
	}
}
