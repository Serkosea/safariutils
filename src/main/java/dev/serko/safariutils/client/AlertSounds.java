package dev.serko.safariutils.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Stable sound IDs with an independently alphabetized presentation order. */
public final class AlertSounds {
	public record Choice(int id, String label) { }
	private record Note(SoundEvent sound, int delay, float pitch) { }
	private record Pending(SoundEvent sound, long dueTick, float volume, float pitch, int layers) {
		private Pending(SoundEvent sound, long dueTick, float volume, float pitch) {
			this(sound, dueTick, volume, pitch, 1);
		}
	}

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
	private static final List<Pending> TICKET_REMINDER_PENDING = new ArrayList<>();
	private static final List<Note> TICKET_REMINDER_MELODY = ticketReminderMelody();
	private static boolean ticketReminderActive;
	private static float ticketReminderVolume;
	private static float ticketReminderPitch;
	private static final Deque<Pending> SPARKLING_PENDING = new ArrayDeque<>();
	private static final float SPARKLING_BASE_VOLUME_GAIN = 3.75f;
	public static final int SPARKLING_SONG_COUNT = 11;
	public static final int SPARKLING_SONG_OFF = SPARKLING_SONG_COUNT;
	private static final float[] SPARKLING_DURATION_CHOICES = {
		5f, 5.5f, 6f, 6.5f, 7f, 7.5f, 8f, 8.5f, 9f, 9.5f,
		10f, 10.5f, 11f, 11.5f, 12f, 12.5f, 13f, 13.5f, 14f, 14.5f,
		15f, 15.5f, 16f, 16.5f, 17f, 17.5f, 18f, 18.5f, 19f, 19.5f, 20f
	};
	private static final String[] SPARKLING_STEM_IDS = {
		"melody", "magic", "harmony", "counter", "bass", "pulse", "drums", "grandeur",
		"bells", "harp", "choir", "horns", "finale"
	};
	private static final int[] SPARKLING_STEM_LEVELS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
	private static final float[] SPARKLING_STEM_GAINS = {
		1f, .76f, .72f, .78f, .88f, .82f, 1.04f, .9f, .72f, .76f, .66f, .86f, .8f
	};
	private static final List<SoundInstance> ACTIVE_SPARKLING_SOUNDS = new ArrayList<>();
	private static final int SPARKLING_FADE_TICKS = 20;
	private static long sparklingFadeAt;
	private static long sparklingStopAt;
	private static long sparklingScoreStartedAt;
	private static long sparklingScoreOffsetMillis;
	private static long sparklingScoreCycle = -1;
	private static final int[] SPARKLING_EVENT_INDEX = new int[13];
	private static int activeSparklingSong = -1;
	private static int activeSparklingTheme;
	private static float activeSparklingGain;
	private static final int[] SAMPLE_ANCHORS = {48, 66, 84};
	private static final int NOTE_KIND_COUNT = 9;
	private static final int SCORE_LOOP_MILLIS = 30_000;
	private static final ScoreData SPARKLING_SCORES = loadSparklingScores();
	private static final SoundEvent[][][] NOTE_SAMPLES = noteSamples();
	private static final SoundEvent[] DRUM_SAMPLES = drumSamples();

	private static final class ScoreData {
		String[] kinds = new String[0];
		float[][][][] songs = new float[0][][][];
	}

	/** Keeps the current arrangement playing while its real channel volume fades. */
	private static final class FadingSparklingSound extends AbstractTickableSoundInstance {
		private final float fullVolume;

		private FadingSparklingSound(SoundEvent sound, float volume, float pitch) {
			super(sound, SoundSource.PLAYERS, RandomSource.create());
			this.fullVolume = volume;
			this.volume = volume;
			this.pitch = pitch;
			this.looping = false;
			this.delay = 0;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.relative = true;
		}

		@Override
		public void tick() {
			if (tick >= sparklingStopAt) {
				stop();
				return;
			}
			if (tick < sparklingFadeAt) return;
			float remaining = (sparklingStopAt - tick) / (float) SPARKLING_FADE_TICKS;
			volume = fullVolume * Math.clamp(remaining, 0f, 1f);
		}
	}
	private record SparklingSong(int bodyTicks, int cadenceTicks, int[] melody,
		int[] chords, int[] counter, int[] cadence, int[] cadenceChords) { }
	/** Distinct major-key compositions ordered from intimate discovery to grand finale. */
	private static final SparklingSong[] SPARKLING_SONGS = {
		// Prismatic Discovery — compact G-major reveal.
		new SparklingSong(160, 40,
			new int[]{0,67, 8,71, 16,74, 28,79, 40,78, 52,74, 64,71, 76,67,
				80,69, 92,72, 104,71, 116,74, 128,79, 140,78, 152,79},
			new int[]{0,55,4, 40,50,4, 80,52,3, 120,48,4},
			new int[]{20,86, 60,83, 100,88, 140,86},
			new int[]{0,74, 8,79, 16,81, 24,83, 32,79, 39,79},
			new int[]{0,50,4, 20,48,4, 32,55,4}),
		// Starlight Ascent — buoyant D-major 6/8 climb.
		new SparklingSong(144, 48,
			new int[]{0,62, 6,66, 12,69, 18,74, 30,73, 36,69, 48,66, 54,69,
				60,74, 72,76, 78,78, 84,81, 96,78, 108,76, 120,74, 132,81},
			new int[]{0,50,4, 36,57,4, 72,47,3, 108,55,4},
			new int[]{15,81, 45,78, 75,86, 105,83, 135,90},
			new int[]{0,78, 8,81, 16,83, 24,86, 32,81, 40,78, 47,74},
			new int[]{0,55,4, 24,57,4, 40,50,4}),
		// Enchanted Dawn — warm C-major call and response.
		new SparklingSong(160, 40,
			new int[]{0,64, 10,67, 20,72, 30,71, 40,69, 50,67, 60,76, 70,72,
				80,67, 90,72, 100,76, 110,79, 120,77, 130,76, 140,72, 150,79},
			new int[]{0,48,4, 40,45,3, 80,53,4, 120,55,4},
			new int[]{25,79, 55,76, 85,84, 115,81, 145,83},
			new int[]{0,76, 8,77, 16,79, 24,84, 32,83, 39,84},
			new int[]{0,53,4, 20,55,4, 32,48,4}),
		// Aurora Waltz — lilting F-major three-beat phrase.
		new SparklingSong(144, 48,
			new int[]{0,65, 8,69, 16,72, 24,77, 36,76, 48,72, 56,69, 64,72,
				72,74, 84,77, 96,81, 108,79, 120,77, 132,72, 140,77},
			new int[]{0,53,4, 36,48,4, 72,50,3, 108,55,4},
			new int[]{12,84, 36,81, 60,79, 84,86, 108,84, 132,81},
			new int[]{0,72, 8,77, 16,81, 24,79, 32,76, 40,77, 47,77},
			new int[]{0,55,4, 24,48,4, 40,53,4}),
		// Crystal Reverie — shimmering A-major arpeggio and countermelody.
		new SparklingSong(144, 48,
			new int[]{0,69, 6,73, 12,76, 18,81, 24,80, 30,76, 36,73, 48,71,
				54,74, 60,78, 66,83, 78,81, 90,78, 102,76, 114,73, 126,81, 138,85},
			new int[]{0,57,4, 36,52,4, 72,54,3, 108,50,4},
			new int[]{9,88, 27,85, 45,83, 63,90, 81,88, 99,85, 117,92, 135,90},
			new int[]{0,76, 8,81, 16,83, 24,85, 32,88, 40,85, 47,81},
			new int[]{0,50,4, 24,52,4, 40,57,4}),
		// Celestial Voyage — broad E-major adventure theme.
		new SparklingSong(160, 40,
			new int[]{0,64, 8,68, 16,71, 24,76, 36,78, 48,80, 60,78, 72,76,
				80,71, 88,76, 96,80, 104,83, 116,85, 128,83, 140,80, 152,88},
			new int[]{0,52,4, 40,47,4, 80,49,3, 120,45,4},
			new int[]{12,83, 32,80, 52,85, 72,83, 92,88, 112,85, 132,92, 152,88},
			new int[]{0,80, 7,83, 14,85, 21,88, 28,87, 34,83, 39,88},
			new int[]{0,45,4, 20,47,4, 32,52,4}),
		// Mythic Coronation — B-flat major ceremonial fanfare.
		new SparklingSong(160, 40,
			new int[]{0,70, 6,74, 12,77, 20,82, 28,77, 36,82, 44,86, 56,84,
				64,82, 72,79, 80,77, 88,82, 96,86, 104,89, 116,91, 128,89, 140,86, 152,94},
			new int[]{0,58,4, 40,53,4, 80,55,3, 120,51,4},
			new int[]{8,89, 24,86, 40,94, 56,91, 72,89, 88,98, 104,94, 120,91, 136,98, 152,94},
			new int[]{0,82, 6,86, 12,89, 18,94, 25,91, 31,89, 36,86, 39,94},
			new int[]{0,53,4, 18,51,4, 32,58,4}),
		// Eternal Radiance — soaring G-major grand finale in 12/8.
		new SparklingSong(144, 48,
			new int[]{0,67, 4,71, 8,74, 12,79, 18,83, 24,81, 30,79, 36,86,
				42,83, 48,88, 54,86, 60,83, 66,91, 72,88, 78,86, 84,83,
				90,79, 96,83, 102,86, 108,91, 114,93, 120,95, 126,93, 132,91, 138,98},
			new int[]{0,55,4, 36,50,4, 72,52,3, 108,48,4},
			new int[]{6,86, 18,90, 30,86, 42,95, 54,91, 66,98, 78,95, 90,91,
				102,100, 114,98, 126,102, 138,98},
			new int[]{0,86, 5,91, 10,93, 15,95, 20,98, 26,95, 32,93, 38,91, 43,98, 47,103},
			new int[]{0,50,4, 16,48,4, 32,55,4})
	};
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

	/** A continuous rising and falling discovery call reserved for ticket-trading guests. */
	public static void playTicketTradingReminder(Minecraft client, float volume, float pitch) {
		if (client.player == null || volume <= 0f) return;
		ticketReminderVolume = volume;
		ticketReminderPitch = pitch;
		if (ticketReminderActive) return;
		ticketReminderActive = true;
		TICKET_REMINDER_PENDING.clear();
		queueLayers(TICKET_REMINDER_PENDING, TICKET_REMINDER_MELODY,
			ticketReminderVolume, ticketReminderPitch);
	}

	private static List<Note> ticketReminderMelody() {
		SoundEvent chime = SoundEvents.NOTE_BLOCK_CHIME.value();
		return List.of(
			new Note(chime, 0, .63f), new Note(chime, 3, .71f),
			new Note(chime, 6, .79f), new Note(chime, 9, .89f),
			new Note(chime, 12, 1f), new Note(chime, 15, 1.12f),
			new Note(chime, 18, 1.26f), new Note(chime, 21, 1.41f),
			new Note(chime, 24, 1.26f), new Note(chime, 27, 1.12f),
			new Note(chime, 30, 1f), new Note(chime, 33, .89f),
			new Note(chime, 36, .79f), new Note(chime, 39, .71f),
			new Note(chime, 42, .63f));
	}

	public static void stopTicketTradingReminder() {
		if (!ticketReminderActive) {
			TICKET_REMINDER_PENDING.clear();
			return;
		}
		ticketReminderActive = false;
		TICKET_REMINDER_PENDING.clear();
		SoundEvent chime = SoundEvents.NOTE_BLOCK_CHIME.value();
		queueLayeredNote(TICKET_REMINDER_PENDING, chime, 0,
			ticketReminderVolume * .55f, ticketReminderPitch * .79f);
		queueLayeredNote(TICKET_REMINDER_PENDING, chime, 4,
			ticketReminderVolume * .30f, ticketReminderPitch * .71f);
		queueLayeredNote(TICKET_REMINDER_PENDING, chime, 8,
			ticketReminderVolume * .12f, ticketReminderPitch * .63f);
	}

	private static void queueLayers(List<Pending> target, List<Note> notes,
			float volume, float pitch) {
		for (Note note : notes) {
			queueLayeredNote(target, note.sound, note.delay, volume, pitch * note.pitch);
		}
	}

	private static void queueLayeredNote(List<Pending> target, SoundEvent sound,
			int delay, float volume, float pitch) {
		int layers = Math.max(1, (int) Math.ceil(volume));
		for (int layer = 0; layer < layers; layer++) {
			float layerVolume = Math.min(1f, volume - layer);
			if (layerVolume > 0f) {
				target.add(new Pending(sound, tick + delay, layerVolume, pitch));
			}
		}
	}

	public static String sparklingIntensityLabel(int theme) {
		return switch (Math.clamp(theme, 0, 12)) {
			case 0 -> "Delicate";
			case 1 -> "Gentle";
			case 2 -> "Luminous";
			case 3 -> "Radiant";
			case 4 -> "Celebratory";
			case 5 -> "Triumphant";
			case 6 -> "Majestic";
			case 7 -> "Grand";
			case 8 -> "Epic";
			case 9 -> "Mythic";
			case 10 -> "Legendary";
			case 11 -> "Transcendent";
			default -> "Apotheosis";
		};
	}

	public static String sparklingIntensityShortLabel(int theme) {
		return sparklingIntensityLabel(theme);
	}

	public static String sparklingSongLabel(int song) {
		return switch (Math.clamp(song, 0, SPARKLING_SONG_OFF)) {
			case 0 -> "Glimmering Discovery";
			case 1 -> "Faelight Dance";
			case 2 -> "Starlight Ascent";
			case 3 -> "Enchanted Voyage";
			case 4 -> "Celestial Awakening";
			case 5 -> "Mythic Triumph";
			case 6 -> "Infinite Wonder";
			case 7 -> "Astral Jubilee";
			case 8 -> "Empyrean Revelation";
			case 9 -> "Arcane Tempest";
			case 10 -> "Crown of Stars";
			default -> "Off";
		};
	}

	public static String sparklingSongShortLabel(int song) {
		return switch (Math.clamp(song, 0, SPARKLING_SONG_OFF)) {
			case 0 -> "Glimmer";
			case 1 -> "Faelight";
			case 2 -> "Ascent";
			case 3 -> "Voyage";
			case 4 -> "Awakening";
			case 5 -> "Triumph";
			case 6 -> "Infinite";
			case 7 -> "Jubilee";
			case 8 -> "Empyrean";
			case 9 -> "Tempest";
			case 10 -> "Crown";
			default -> "Off";
		};
	}

	public static int sparklingDurationChoiceCount() {
		return SPARKLING_DURATION_CHOICES.length;
	}

	public static float sparklingDurationChoice(int index) {
		return SPARKLING_DURATION_CHOICES[Math.clamp(index, 0,
			SPARKLING_DURATION_CHOICES.length - 1)];
	}

	public static int nearestSparklingDurationChoice(float seconds) {
		int nearest = 0;
		for (int index = 1; index < SPARKLING_DURATION_CHOICES.length; index++) {
			if (Math.abs(SPARKLING_DURATION_CHOICES[index] - seconds)
					< Math.abs(SPARKLING_DURATION_CHOICES[nearest] - seconds)) nearest = index;
		}
		return nearest;
	}

	public static int defaultSparklingThemeDurationSeconds(int theme) {
		return switch (Math.clamp(theme, 0, 12)) {
			case 0, 1 -> 5;
			case 2, 3 -> 8;
			case 4, 5 -> 12;
			case 6, 7 -> 16;
			case 8, 9 -> 20;
			case 10, 11 -> 24;
			default -> 30;
		};
	}

	/** Starts a loopable arrangement that fades its current phrase at the configured duration. */
	public static void playSparklingTheme(Minecraft client, int song, int theme, float durationSeconds,
			int volumePercent) {
		playSparklingTheme(client, song, theme, durationSeconds, volumePercent, 0);
	}

	private static void playSparklingTheme(Minecraft client, int song, int theme,
			float durationSeconds, int volumePercent, long offsetMillis) {
		if (client.player == null) return;
		stopSparklingSongs(client);
		if (song == SPARKLING_SONG_OFF || SPARKLING_SCORES.songs.length == 0) return;
		activeSparklingSong = Math.clamp(song, 0, SPARKLING_SONG_COUNT - 1);
		// Each intensity setting unlocks exactly one additional arrangement stem.
		// Song choice changes the composition, not the meaning of the intensity control.
		activeSparklingTheme = Math.clamp(theme, 0, 12);
		activeSparklingGain = Math.clamp(volumePercent, 0,
			SparklingAlertStyle.VOLUME_MANUAL_MAX) / 100f * SPARKLING_BASE_VOLUME_GAIN;
		long durationMillis = Math.round(Math.clamp(durationSeconds, 1f, 999f) * 1_000L);
		long normalizedOffset = Math.floorMod(offsetMillis, durationMillis);
		int durationTicks = Math.max(1,
			(int) Math.ceil((durationMillis - normalizedOffset) / 50.0));
		sparklingStopAt = tick + durationTicks;
		sparklingFadeAt = Math.max(tick, sparklingStopAt - SPARKLING_FADE_TICKS);
		sparklingScoreStartedAt = tick;
		sparklingScoreOffsetMillis = normalizedOffset;
		sparklingScoreCycle = -1;
		playSparklingScoreTick(client);
	}

	private static void stopSparklingSongs(Minecraft client) {
		stopSparklingInstances(client);
		activeSparklingSong = -1;
		sparklingFadeAt = 0;
		sparklingStopAt = 0;
		sparklingScoreStartedAt = 0;
		sparklingScoreOffsetMillis = 0;
		sparklingScoreCycle = -1;
	}

	/** Plays the editor preview through the same layered player as the real alert. */
	public static void playSparklingPreview(Minecraft client, int song, int theme,
			float durationSeconds, int volumePercent) {
		playSparklingTheme(client, song, theme, durationSeconds, volumePercent);
	}

	public static void playSparklingPreview(Minecraft client, int song, int theme,
			float durationSeconds, int volumePercent, long offsetMillis) {
		playSparklingTheme(client, song, theme, durationSeconds, volumePercent, offsetMillis);
	}

	/** Stops only the sparkling score used by the editor when its modal closes. */
	public static void stopSparklingPreview(Minecraft client) {
		stopSparklingSongs(client);
	}

	/** Stops queued Safari audio when the client leaves Hypixel. */
	public static void onConnectionExit() {
		PENDING.clear();
		SPARKLING_PENDING.clear();
		stopSparklingSongs(Minecraft.getInstance());
	}

	private static void stopSparklingInstances(Minecraft client) {
		for (SoundInstance sound : ACTIVE_SPARKLING_SOUNDS) {
			client.getSoundManager().stop(sound);
		}
		ACTIVE_SPARKLING_SOUNDS.clear();
	}

	private static void playSparklingLayers(Minecraft client, SoundEvent sound, float volume,
			float pitch) {
		int layers = Math.max(1, (int) Math.ceil(volume));
		for (int layer = 0; layer < layers; layer++) {
			float layerVolume = Math.min(1f, volume - layer);
			if (layerVolume <= .01f) continue;
			SoundInstance instance = new FadingSparklingSound(sound, layerVolume, pitch);
			ACTIVE_SPARKLING_SOUNDS.add(instance);
			boolean previous = PLAYING_ALERT.get();
			PLAYING_ALERT.set(true);
			try {
				client.getSoundManager().play(instance);
			} finally {
				PLAYING_ALERT.set(previous);
			}
		}
	}

	private static void playSparklingScoreTick(Minecraft client) {
		if (activeSparklingSong < 0 || activeSparklingSong >= SPARKLING_SCORES.songs.length) return;
		long elapsed = sparklingScoreOffsetMillis + (tick - sparklingScoreStartedAt) * 50L;
		long cycle = elapsed / SCORE_LOOP_MILLIS;
		int within = (int) (elapsed % SCORE_LOOP_MILLIS);
		float[][][] stems = SPARKLING_SCORES.songs[activeSparklingSong];
		if (cycle != sparklingScoreCycle) {
			sparklingScoreCycle = cycle;
			for (int stem = 0; stem < SPARKLING_EVENT_INDEX.length; stem++) {
				int index = 0;
				if (stem < stems.length) {
					while (index < stems[stem].length && stems[stem][index][0] < within) index++;
				}
				SPARKLING_EVENT_INDEX[stem] = index;
			}
		}
		for (int stem = 0; stem < Math.min(stems.length, SPARKLING_STEM_IDS.length); stem++) {
			int progress = activeSparklingTheme - SPARKLING_STEM_LEVELS[stem];
			if (progress < 0) continue;
			float stemGain = activeSparklingGain * SPARKLING_STEM_GAINS[stem]
				* (0.58f + Math.min(4, progress) * 0.12f);
			int index = SPARKLING_EVENT_INDEX[stem];
			while (index < stems[stem].length && stems[stem][index][0] <= within) {
				playScoreEvent(client, stems[stem][index], stemGain);
				index++;
			}
			SPARKLING_EVENT_INDEX[stem] = index;
		}
	}

	private static void playScoreEvent(Minecraft client, float[] event, float stemGain) {
		if (event.length < 5) return;
		int kind = Math.clamp(Math.round(event[1]), 0, SPARKLING_SCORES.kinds.length - 1);
		SoundEvent sample;
		float pitch = 1f;
		if (kind < NOTE_KIND_COUNT) {
			int note = Math.round(event[2]);
			int anchorIndex = 0;
			for (int index = 1; index < SAMPLE_ANCHORS.length; index++) {
				if (Math.abs(note - SAMPLE_ANCHORS[index])
						< Math.abs(note - SAMPLE_ANCHORS[anchorIndex])) anchorIndex = index;
			}
			int duration = Math.clamp(Math.round(event[3]), 0, 3);
			sample = NOTE_SAMPLES[kind][anchorIndex][duration];
			pitch = (float) Math.pow(2.0, (note - SAMPLE_ANCHORS[anchorIndex]) / 12.0);
		} else {
			sample = DRUM_SAMPLES[Math.clamp(kind - NOTE_KIND_COUNT, 0,
				DRUM_SAMPLES.length - 1)];
		}
		playSparklingLayers(client, sample, stemGain * event[4], pitch);
	}

	private static ScoreData loadSparklingScores() {
		try (var stream = AlertSounds.class.getResourceAsStream(
				"/assets/safariutils/sparkling_scores.json")) {
			if (stream == null) return new ScoreData();
			return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
				ScoreData.class);
		} catch (Exception error) {
			OperationalLog.error("AUDIO_SCORE", error);
			return new ScoreData();
		}
	}

	private static SoundEvent[][][] noteSamples() {
		SoundEvent[][][] samples = new SoundEvent[NOTE_KIND_COUNT][SAMPLE_ANCHORS.length][4];
		String[] kinds = {"celesta", "glass", "flute", "strings", "brass", "radiance",
			"bass", "pluck", "pad"};
		for (int kind = 0; kind < kinds.length; kind++) {
			for (int anchor = 0; anchor < SAMPLE_ANCHORS.length; anchor++) {
				for (int duration = 0; duration < 4; duration++) {
					samples[kind][anchor][duration] = soundEvent("sparkling.sample."
						+ kinds[kind] + "." + SAMPLE_ANCHORS[anchor] + "." + duration);
				}
			}
		}
		return samples;
	}

	private static SoundEvent[] drumSamples() {
		return new SoundEvent[] {soundEvent("sparkling.sample.kick"),
			soundEvent("sparkling.sample.tom"), soundEvent("sparkling.sample.cymbal"),
			soundEvent("sparkling.sample.hat"), soundEvent("sparkling.sample.snare")};
	}

	private static SoundEvent soundEvent(String path) {
		return SoundEvent.createVariableRangeEvent(
			Identifier.fromNamespaceAndPath("safariutils", path));
	}

	/** Plays one distinct discovery composition with progressively richer orchestration. */
	private static void playMajorSparklingScore(Minecraft client, int song, int theme,
			float durationSeconds, int volumePercent) {
		if (client.player == null) return;
		SPARKLING_PENDING.clear();
		SparklingSong score = SPARKLING_SONGS[song];
		int arrangement = Math.min(12, theme + song);
		int durationTicks = Math.round(durationSeconds * 20);
		int cadenceLength = score.cadenceTicks();
		int bodyEnd = Math.max(0, durationTicks - Math.min(durationTicks, cadenceLength));
		float masterGain = volumePercent / 100f * SPARKLING_BASE_VOLUME_GAIN;
		float orchestrationGain = 1f / (1f + arrangement * 0.045f);

		int target = 0;
		while (target < bodyEnd) {
			int length = Math.min(score.bodyTicks(), bodyEnd - target);
			queueSongRange(score, 0, length, target, song, arrangement,
				orchestrationGain, masterGain);
			target += length;
		}
		queueCadence(score, bodyEnd, durationTicks - bodyEnd, song, arrangement,
			orchestrationGain, masterGain);
		queuePercussion(durationTicks, song, arrangement, orchestrationGain, masterGain);

		// Phrase percussion is queued after melody notes, so restore chronological
		// order once; playback then remains O(number of notes due this tick).
		List<Pending> ordered = new ArrayList<>(SPARKLING_PENDING);
		ordered.sort(Comparator.comparingLong(Pending::dueTick));
		SPARKLING_PENDING.clear();
		SPARKLING_PENDING.addAll(ordered);
	}

	private static void queueSongRange(SparklingSong score, int sourceFrom, int sourceTo,
			int targetStart, int song, int theme, float orchestrationGain, float masterGain) {
		queueNoteRange(score.melody(), 0, sourceFrom, sourceTo, targetStart, song, theme,
			orchestrationGain, masterGain);
		queueNoteRange(score.counter(), 3, sourceFrom, sourceTo, targetStart, song, theme,
			orchestrationGain, masterGain);
		queueChordRange(score.chords(), sourceFrom, sourceTo, targetStart, song, theme,
			orchestrationGain, masterGain);
	}

	private static void queueNoteRange(int[] notes, int role, int sourceFrom, int sourceTo,
			int targetStart, int song, int theme, float orchestrationGain, float masterGain) {
		for (int index = 0; index < notes.length; index += 2) {
			int sourceTick = notes[index];
			if (sourceTick < sourceFrom || sourceTick >= sourceTo) continue;
			queueScoreNote(tick + targetStart + sourceTick - sourceFrom, notes[index + 1],
				role, role == 0 ? 3 : 2, song, theme, orchestrationGain, masterGain);
		}
	}

	private static void queueChordRange(int[] chords, int sourceFrom, int sourceTo,
			int targetStart, int song, int theme, float orchestrationGain, float masterGain) {
		for (int index = 0; index < chords.length; index += 3) {
			int sourceTick = chords[index];
			if (sourceTick < sourceFrom || sourceTick >= sourceTo) continue;
			long due = tick + targetStart + sourceTick - sourceFrom;
			int root = chords[index + 1];
			int third = chords[index + 2];
			queueScoreNote(due, root + third, 1, 2, song, theme, orchestrationGain, masterGain);
			queueScoreNote(due, root + 7, 1, 2, song, theme, orchestrationGain, masterGain);
			queueScoreNote(due, root - 12, 2, 3, song, theme, orchestrationGain, masterGain);
		}
	}

	private static void queueCadence(SparklingSong score, int targetStart, int availableTicks,
			int song, int theme, float orchestrationGain, float masterGain) {
		if (availableTicks <= 0) return;
		float scale = Math.min(1f, availableTicks / (float) score.cadenceTicks());
		for (int index = 0; index < score.cadence().length; index += 2) {
			int offset = Math.min(availableTicks - 1, Math.round(score.cadence()[index] * scale));
			queueScoreNote(tick + targetStart + Math.max(0, offset), score.cadence()[index + 1],
				0, 3, song, theme, orchestrationGain, masterGain);
		}
		for (int index = 0; index < score.cadenceChords().length; index += 3) {
			int offset = Math.min(availableTicks - 1,
				Math.round(score.cadenceChords()[index] * scale));
			long due = tick + targetStart + Math.max(0, offset);
			int root = score.cadenceChords()[index + 1];
			int third = score.cadenceChords()[index + 2];
			queueScoreNote(due, root + third, 1, 2, song, theme, orchestrationGain, masterGain);
			queueScoreNote(due, root + 7, 1, 2, song, theme, orchestrationGain, masterGain);
			queueScoreNote(due, root - 12, 2, 3, song, theme, orchestrationGain, masterGain);
		}
	}

	private static void queueScoreNote(long due, int midi, int role, int strength,
			int song, int theme, float orchestrationGain, float masterGain) {
		float pitch = minecraftPitch(midi);
		float gain = orchestrationGain * switch (strength) {
			case 3 -> 1f;
			case 2 -> 0.88f;
			default -> 0.74f;
		};
		if (role == 0) {
			queueSparklingLayers(songLead(song), due, 4 + song / 3,
				pitch, gain, masterGain);
			queueSparklingLayers(songCompanion(song), due, 2 + song / 4,
				pitch, gain, masterGain);
			if (theme >= 3) queueSparklingLayers(SoundEvents.NOTE_BLOCK_PLING.value(), due, 1,
				pitch, gain, masterGain);
			if (theme >= 7) queueSparklingLayers(SoundEvents.NOTE_BLOCK_FLUTE.value(), due, 1,
				pitch, 0.82f * gain, masterGain);
			if (theme >= 11) queueSparklingLayers(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(),
				due, 1, pitch, 0.8f * gain, masterGain);
		} else if (role == 1 && theme >= 1) {
			queueSparklingLayers(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), due,
				theme >= 5 ? 2 : 1, pitch, 0.78f * gain, masterGain);
			if (theme >= 8) queueSparklingLayers(SoundEvents.NOTE_BLOCK_BELL.value(), due, 1,
				pitch, 0.7f * gain, masterGain);
		} else if (role == 2 && theme >= 2) {
			queueSparklingLayers(SoundEvents.NOTE_BLOCK_BASS.value(), due, 2,
				pitch, 0.88f * gain, masterGain);
			if (theme >= 6) queueSparklingLayers(SoundEvents.NOTE_BLOCK_GUITAR.value(), due, 1,
				pitch, 0.75f * gain, masterGain);
		} else if (role == 3 && theme >= 4) {
			queueSparklingLayers(SoundEvents.NOTE_BLOCK_PLING.value(), due, 1,
				pitch, 0.72f * gain, masterGain);
			if (theme >= 9) queueSparklingLayers(SoundEvents.NOTE_BLOCK_BELL.value(), due, 1,
				pitch, 0.68f * gain, masterGain);
		}
	}

	private static void queuePercussion(int durationTicks, int song, int theme,
			float orchestrationGain, float masterGain) {
		if (theme + song / 2 < 4) return;
		for (int position = 0; position < durationTicks - 2; position += 10) {
			if (position % 20 == 0) queueSparklingLayers(SoundEvents.NOTE_BLOCK_BASEDRUM.value(),
				tick + position, 1, 1f, 0.7f * orchestrationGain, masterGain);
			else queueSparklingLayers(SoundEvents.NOTE_BLOCK_SNARE.value(), tick + position,
				1, 1.08f, 0.62f * orchestrationGain, masterGain);
			if (theme >= 8) queueSparklingLayers(SoundEvents.NOTE_BLOCK_HAT.value(),
				tick + position + 5, 1, 1.2f, 0.48f * orchestrationGain, masterGain);
			if (theme >= 12 && position % 40 == 30) {
				queueSparklingLayers(SoundEvents.NOTE_BLOCK_COW_BELL.value(), tick + position,
					1, 1.12f, 0.5f * orchestrationGain, masterGain);
			}
		}
	}

	private static SoundEvent songLead(int song) {
		return switch (song) {
			case 1 -> SoundEvents.NOTE_BLOCK_FLUTE.value();
			case 2 -> SoundEvents.NOTE_BLOCK_BELL.value();
			case 3 -> SoundEvents.NOTE_BLOCK_HARP.value();
			case 4 -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
			case 5 -> SoundEvents.NOTE_BLOCK_PLING.value();
			case 6 -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();
			case 7 -> SoundEvents.NOTE_BLOCK_CHIME.value();
			default -> SoundEvents.NOTE_BLOCK_CHIME.value();
		};
	}

	private static SoundEvent songCompanion(int song) {
		return switch (song) {
			case 1, 4 -> SoundEvents.NOTE_BLOCK_CHIME.value();
			case 2, 6 -> SoundEvents.NOTE_BLOCK_HARP.value();
			case 3 -> SoundEvents.NOTE_BLOCK_FLUTE.value();
			case 5, 7 -> SoundEvents.NOTE_BLOCK_BELL.value();
			default -> SoundEvents.NOTE_BLOCK_HARP.value();
		};
	}

	private static float minecraftPitch(int midi) {
		while (midi < 54) midi += 12;
		while (midi > 78) midi -= 12;
		return (float) Math.pow(2.0, (midi - 66) / 12.0);
	}

	private static void queueSparklingLayers(SoundEvent sound, long due, int layers,
			float pitch, float volume, float masterGain) {
		float scaledLayers = Math.max(0f, layers * masterGain);
		int wholeLayers = (int) scaledLayers;
		if (wholeLayers > 0) {
			SPARKLING_PENDING.add(new Pending(sound, due, volume, pitch, wholeLayers));
		}
		float remainder = scaledLayers - wholeLayers;
		if (remainder > 0.01f) {
			SPARKLING_PENDING.add(new Pending(sound, due, volume * remainder, pitch));
		}
	}

	/** Advances multi-note alert sounds without creating timers or worker threads. */
	public static void tick() {
		tick++;
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			PENDING.clear();
			TICKET_REMINDER_PENDING.clear();
			ticketReminderActive = false;
			SPARKLING_PENDING.clear();
			stopSparklingSongs(client);
			return;
		}
		if (activeSparklingSong >= 0 && tick >= sparklingStopAt) {
			stopSparklingSongs(client);
		} else if (activeSparklingSong >= 0) {
			playSparklingScoreTick(client);
		}
		drain(client, PENDING);
		drain(client, TICKET_REMINDER_PENDING);
		if (ticketReminderActive && TICKET_REMINDER_PENDING.isEmpty()) {
			queueLayers(TICKET_REMINDER_PENDING, TICKET_REMINDER_MELODY,
				ticketReminderVolume, ticketReminderPitch);
		}
		drainSparkling(client);
	}

	private static void drain(Minecraft client, List<Pending> sounds) {
		for (Iterator<Pending> iterator = sounds.iterator(); iterator.hasNext();) {
			Pending pending = iterator.next();
			if (pending.dueTick > tick) continue;
			for (int layer = 0; layer < pending.layers; layer++) {
				playNote(client, pending.sound, pending.volume, pending.pitch);
			}
			iterator.remove();
		}
	}

	/** Sparkling scores are queued chronologically, so long custom durations stay O(due notes). */
	private static void drainSparkling(Minecraft client) {
		while (!SPARKLING_PENDING.isEmpty()
				&& SPARKLING_PENDING.peekFirst().dueTick <= tick) {
			Pending pending = SPARKLING_PENDING.removeFirst();
			for (int layer = 0; layer < pending.layers; layer++) {
				playNote(client, pending.sound, pending.volume, pending.pitch);
			}
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
