package dev.serko.safariutils.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/** Compact effect recipe shared by the full alert and its settings preview. */
public record SparklingAlertStyle(
	int stars, int comets, int bursts, int orbits, int confetti,
	int ribbons, int corners, int rays, int shockwaves, int glitterWaves,
	int prisms, int fireworks, int halos, int nebulae, int spirals,
	int curtains, int chromaticRain, int diamonds, int horizonFlares,
	int constellations, int lattice, int embers, int kaleidoscope, int eclipses,
	int runes, int crownRays, int hearts, int mirrorComets, int sweeps,
	int twinHelix, int lightning, int prismFans, int satellites,
	int hourglass, int glyphRain, int novaCrosses, int portalGates,
	int crystalShards, int infinityTrails, int gravityWells,
	int spectrumSteps, int starfallColumns,
	int[] additions,
	int pulse, int frame, int callout, int textShake, int textGradientSpeed,
	int soundSong, int soundTheme, float durationSeconds,
	int soundVolumePercent) {
	/** Validated, reusable binding for one custom-editor control. */
	public static final class Effect {
		private final String label;
		private final Field field;
		private final Method accessor;
		private final int maximum;
		private final int additionIndex;

		private Effect(String label, String fieldName, int maximum) {
			this(label, fieldName, maximum, -1);
		}

		private Effect(String label, String fieldName, int maximum, int additionIndex) {
			this.label = label;
			this.maximum = maximum;
			this.additionIndex = additionIndex;
			try {
				field = SafariConfig.SparklingConfig.class.getField(fieldName);
				if (additionIndex >= 0) accessor = null;
				else {
					String suffix = fieldName.substring("customAlert".length());
					String component = Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
					if (component.equals("song")) component = "soundSong";
					accessor = SparklingAlertStyle.class.getMethod(component);
				}
			} catch (NoSuchFieldException | NoSuchMethodException error) {
				throw new ExceptionInInitializerError(error);
			}
		}

		public String label() {
			return label;
		}

		public int maximum() {
			return maximum;
		}

		public boolean soundIntensity() {
			return field.getName().equals("customAlertSoundTheme");
		}

		public boolean soundSong() {
			return field.getName().equals("customAlertSong");
		}

		public boolean gradientSpeed() {
			return field.getName().equals("customAlertTextGradientSpeed");
		}

		public int value(SafariConfig.SparklingConfig config) {
			try {
				return field.getInt(config);
			} catch (IllegalAccessException error) {
				throw new IllegalStateException(error);
			}
		}

		public void set(SafariConfig.SparklingConfig config, int value) {
			try {
				field.setInt(config, Math.clamp(value, 0, maximum));
			} catch (IllegalAccessException error) {
				throw new IllegalStateException(error);
			}
		}

		private void copyFrom(SafariConfig.SparklingConfig config, SparklingAlertStyle style) {
			try {
				set(config, additionIndex >= 0 ? style.addition(additionIndex)
					: (Integer) accessor.invoke(style));
			} catch (ReflectiveOperationException error) {
				throw new IllegalStateException(error);
			}
		}
	}

	public static final int CUSTOM_INDEX = 41;
	public static final int EFFECTS_PER_PAGE = 13;
	public static final int VOLUME_SLIDER_MAX = 300;
	public static final int VOLUME_MANUAL_MAX = 999;
	public static final String[] EFFECT_PAGE_NAMES = {
		"Foundation", "Particles", "Motion", "Geometry", "Atmosphere"
	};
	public static final String PRISMATIC_WHISPER = "Prismatic Whisper";
	public static final String PRISMATIC_GLEAM = "Prismatic Gleam";
	public static final String STARLIGHT_DRIFT = "Starlight Drift";
	public static final String STARLIGHT_WALTZ = "Starlight Waltz";
	public static final String AURORA_RISE = "Aurora Rise";
	public static final String AURORA_CASCADE = "Aurora Cascade";
	public static final String COMET_SHOWER = "Comet Shower";
	public static final String COMET_TEMPEST = "Comet Tempest";
	public static final String FIREWORK_BLOOM = "Firework Bloom";
	public static final String FIREWORK_SYMPHONY = "Firework Symphony";
	public static final String CELESTIAL_CHORUS = "Celestial Chorus";
	public static final String CELESTIAL_CARNIVAL = "Celestial Carnival";
	public static final String NEBULA_SURGE = "Nebula Surge";
	public static final String NEBULA_RHAPSODY = "Nebula Rhapsody";
	public static final String GALACTIC_PULSE = "Galactic Pulse";
	public static final String GALACTIC_TEMPEST = "Galactic Tempest";
	public static final String ASTRAL_REVERIE = "Astral Reverie";
	public static final String ASTRAL_ASCENSION = "Astral Ascension";
	public static final String SUPERNOVA = "Supernova";
	public static final String HYPERNOVA = "Hypernova";
	public static final String COSMIC_JUBILEE = "Cosmic Jubilee";
	public static final String COSMIC_OVERDRIVE = "Cosmic Overdrive";
	public static final String RADIANT_SINGULARITY = "Radiant Singularity";
	public static final String CELESTIAL_TRANSCENDENCE = "Celestial Transcendence";
	public static final String EVENT_HORIZON = "Event Horizon";
	public static final String INFINITE_RADIANCE = "Infinite Radiance";
	public static final String ENDLESS_COSMOS = "Endless Cosmos";
	public static final String CHROMATIC_ZENITH = "Chromatic Zenith";
	public static final String ETHEREAL_GENESIS = "Ethereal Genesis";
	public static final String ETHEREAL_ASCENDANCE = "Ethereal Ascendance";
	public static final String MYTHIC_DOMINION = "Mythic Dominion";
	public static final String MYTHIC_ETERNITY = "Mythic Eternity";
	public static final String CELESTIAL_GENESIS = "Celestial Genesis";
	public static final String CELESTIAL_ASCENDANCE = "Celestial Ascendance";
	public static final String INFINITE_TRANSCENDENCE = "Infinite Transcendence";
	public static final String INFINITE_APOTHEOSIS = "Infinite Apotheosis";
	public static final String ETERNAL_DAWN = "Eternal Dawn";
	public static final String ETERNAL_RESONANCE = "Eternal Resonance";
	public static final String ETERNAL_DOMINION = "Eternal Dominion";
	public static final String ETERNAL_TRANSCENDENCE = "Eternal Transcendence";
	public static final String ETERNAL_APOTHEOSIS = "Eternal Apotheosis";
	public static final String CUSTOM = "Custom";
	public static final String[] NAMES = {
		PRISMATIC_WHISPER, PRISMATIC_GLEAM, STARLIGHT_DRIFT, STARLIGHT_WALTZ,
		AURORA_RISE, AURORA_CASCADE, COMET_SHOWER, COMET_TEMPEST,
		FIREWORK_BLOOM, FIREWORK_SYMPHONY, CELESTIAL_CHORUS, CELESTIAL_CARNIVAL,
		NEBULA_SURGE, NEBULA_RHAPSODY, GALACTIC_PULSE, GALACTIC_TEMPEST,
		ASTRAL_REVERIE, ASTRAL_ASCENSION, SUPERNOVA, HYPERNOVA,
		COSMIC_JUBILEE, COSMIC_OVERDRIVE, RADIANT_SINGULARITY,
		CELESTIAL_TRANSCENDENCE, EVENT_HORIZON, INFINITE_RADIANCE,
		ENDLESS_COSMOS, CHROMATIC_ZENITH,
		ETHEREAL_GENESIS, ETHEREAL_ASCENDANCE, MYTHIC_DOMINION, MYTHIC_ETERNITY,
		CELESTIAL_GENESIS, CELESTIAL_ASCENDANCE, INFINITE_TRANSCENDENCE,
		INFINITE_APOTHEOSIS, ETERNAL_DAWN, ETERNAL_RESONANCE, ETERNAL_DOMINION,
		ETERNAL_TRANSCENDENCE, ETERNAL_APOTHEOSIS, CUSTOM
	};
	private static final int[] ORIGINAL_EFFECT_ORDER = {
		0, 5, 43, 3, 1, 6, 9, 44, 7, 12, 2, 8, 10, 11, 13, 14,
		15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 4, 42
	};
	private static final SparklingAlertStyle[] PRESETS = createPresets();
	private static SparklingAlertStyle cachedCustom;
	public static final List<Effect> EFFECTS = List.of(
		new Effect("Screen Pulse", "customAlertPulse", 6),
		new Effect("Rainbow Frame", "customAlertFrame", 6),
		new Effect("Capture Callout", "customAlertCallout", 6),
		new Effect("Text Shake", "customAlertTextShake", 6),
		new Effect("Text Gradient Speed", "customAlertTextGradientSpeed", 6),
		new Effect("Song", "customAlertSong", AlertSounds.SPARKLING_SONG_OFF),
		new Effect("Sound Intensity", "customAlertSoundTheme", 12),
		new Effect("Magic Circle", "customAlertMagicCircle", 6, 0),
		new Effect("Arcane Seal", "customAlertArcaneSeal", 6, 8),
		new Effect("Reality Ripple", "customAlertRealityRipple", 6, 14),
		new Effect("Crown Formation", "customAlertCrownFormation", 6, 13),
		new Effect("Celestial Clock", "customAlertCelestialClock", 6, 4),
		new Effect("Constellation Reveal", "customAlertConstellationReveal", 6, 1),

		new Effect("Star Field", "customAlertStars", 6),
		new Effect("Confetti", "customAlertConfetti", 6),
		new Effect("Rising Embers", "customAlertEmbers", 6),
		new Effect("Chromatic Rain", "customAlertChromaticRain", 6),
		new Effect("Glyph Rain", "customAlertGlyphRain", 6),
		new Effect("Crystal Shards", "customAlertCrystalShards", 6),
		new Effect("Starfall Columns", "customAlertStarfallColumns", 6),
		new Effect("Meteor Shower", "customAlertMeteorShower", 6, 12),
		new Effect("Stardust Vortex", "customAlertStardustVortex", 6, 6),
		new Effect("Glitter Waves", "customAlertGlitterWaves", 6),
		new Effect("Firework Blooms", "customAlertFireworks", 6),
		new Effect("Diamond Bursts", "customAlertDiamonds", 6),
		new Effect("Pixel Hearts", "customAlertHearts", 6),

		new Effect("Comet Trails", "customAlertComets", 6),
		new Effect("Mirror Comets", "customAlertMirrorComets", 6),
		new Effect("Starlight Sweep", "customAlertSweeps", 6),
		new Effect("Twin Helix", "customAlertTwinHelix", 6),
		new Effect("Aurora Ribbons", "customAlertRibbons", 6),
		new Effect("Star Curtains", "customAlertCurtains", 6),
		new Effect("Lightning Forks", "customAlertLightning", 6),
		new Effect("Satellite Trails", "customAlertSatellites", 6),
		new Effect("Hourglass Trails", "customAlertHourglass", 6),
		new Effect("Infinity Trails", "customAlertInfinityTrails", 6),
		new Effect("Gravity Wells", "customAlertGravityWells", 6),
		new Effect("Comet Impact", "customAlertCometImpact", 6, 7),
		new Effect("Dimensional Tear", "customAlertDimensionalTear", 6, 10),

		new Effect("Radial Bursts", "customAlertBursts", 6),
		new Effect("Orbiting Stars", "customAlertOrbits", 6),
		new Effect("Shockwaves", "customAlertShockwaves", 6),
		new Effect("Prism Columns", "customAlertPrisms", 6),
		new Effect("Halo Rings", "customAlertHalos", 6),
		new Effect("Pixel Spirals", "customAlertSpirals", 6),
		new Effect("Prism Lattice", "customAlertLattice", 6),
		new Effect("Kaleidoscope Petals", "customAlertKaleidoscope", 6),
		new Effect("Eclipse Arcs", "customAlertEclipses", 6),
		new Effect("Runic Orbit", "customAlertRunes", 6),
		new Effect("Crown Rays", "customAlertCrownRays", 6),
		new Effect("Prism Fans", "customAlertPrismFans", 6),
		new Effect("Nova Crosses", "customAlertNovaCrosses", 6),

		new Effect("Light Rays", "customAlertRays", 6),
		new Effect("Nebula Clouds", "customAlertNebulae", 6),
		new Effect("Horizon Flares", "customAlertHorizonFlares", 6),
		new Effect("Constellation Web", "customAlertConstellations", 6),
		new Effect("Portal Gates", "customAlertPortalGates", 6),
		new Effect("Spectrum Steps", "customAlertSpectrumSteps", 6),
		new Effect("Corner Fountains", "customAlertCorners", 6),
		new Effect("Northern Lights", "customAlertNorthernLights", 6, 3),
		new Effect("Prismatic Wings", "customAlertPrismaticWings", 6, 2),
		new Effect("Celestial Wings", "customAlertCelestialWings", 6, 11),
		new Effect("Crystal Bloom", "customAlertCrystalBloom", 6, 5),
		new Effect("Light Pillars", "customAlertLightPillars", 6, 9));

	public SparklingAlertStyle {
		stars = clamp(stars);
		comets = clamp(comets);
		bursts = clamp(bursts);
		orbits = clamp(orbits);
		confetti = clamp(confetti);
		ribbons = clamp(ribbons);
		corners = clamp(corners);
		rays = clamp(rays);
		shockwaves = clamp(shockwaves);
		glitterWaves = clamp(glitterWaves);
		prisms = clamp(prisms);
		fireworks = clamp(fireworks);
		halos = clamp(halos);
		nebulae = clamp(nebulae);
		spirals = clamp(spirals);
		curtains = clamp(curtains);
		chromaticRain = clamp(chromaticRain);
		diamonds = clamp(diamonds);
		horizonFlares = clamp(horizonFlares);
		constellations = clamp(constellations);
		lattice = clamp(lattice);
		embers = clamp(embers);
		kaleidoscope = clamp(kaleidoscope);
		eclipses = clamp(eclipses);
		runes = clamp(runes);
		crownRays = clamp(crownRays);
		hearts = clamp(hearts);
		mirrorComets = clamp(mirrorComets);
		sweeps = clamp(sweeps);
		twinHelix = clamp(twinHelix);
		lightning = clamp(lightning);
		prismFans = clamp(prismFans);
		satellites = clamp(satellites);
		hourglass = clamp(hourglass);
		glyphRain = clamp(glyphRain);
		novaCrosses = clamp(novaCrosses);
		portalGates = clamp(portalGates);
		crystalShards = clamp(crystalShards);
		infinityTrails = clamp(infinityTrails);
		gravityWells = clamp(gravityWells);
		spectrumSteps = clamp(spectrumSteps);
		starfallColumns = clamp(starfallColumns);
		additions = additions == null ? new int[15] : additions.clone();
		if (additions.length != 15) additions = java.util.Arrays.copyOf(additions, 15);
		for (int index = 0; index < additions.length; index++) {
			additions[index] = clamp(additions[index]);
		}
		pulse = clamp(pulse);
		frame = clamp(frame);
		callout = clamp(callout);
		textShake = clamp(textShake);
		textGradientSpeed = clamp(textGradientSpeed);
		soundSong = Math.clamp(soundSong, 0, AlertSounds.SPARKLING_SONG_OFF);
		soundTheme = Math.clamp(soundTheme, 0, 12);
		durationSeconds = Math.clamp(durationSeconds, 1f, 999f);
		soundVolumePercent = Math.clamp(soundVolumePercent, 0, VOLUME_MANUAL_MAX);
	}

	public int addition(int index) {
		return index >= 0 && index < additions.length ? additions[index] : 0;
	}

	public static SparklingAlertStyle custom() {
		if (cachedCustom != null) return cachedCustom;
		SafariConfig.SparklingConfig value = ConfigManager.get().sparkling;
		cachedCustom = new SparklingAlertStyle(value.customAlertStars, value.customAlertComets,
			value.customAlertBursts, value.customAlertOrbits, value.customAlertConfetti,
			value.customAlertRibbons, value.customAlertCorners, value.customAlertRays,
			value.customAlertShockwaves, value.customAlertGlitterWaves,
			value.customAlertPrisms, value.customAlertFireworks, value.customAlertHalos,
			value.customAlertNebulae, value.customAlertSpirals, value.customAlertCurtains,
			value.customAlertChromaticRain, value.customAlertDiamonds,
			value.customAlertHorizonFlares, value.customAlertConstellations,
			value.customAlertLattice, value.customAlertEmbers,
			value.customAlertKaleidoscope, value.customAlertEclipses,
			value.customAlertRunes, value.customAlertCrownRays,
			value.customAlertHearts, value.customAlertMirrorComets, value.customAlertSweeps,
			value.customAlertTwinHelix, value.customAlertLightning,
			value.customAlertPrismFans, value.customAlertSatellites,
			value.customAlertHourglass, value.customAlertGlyphRain,
			value.customAlertNovaCrosses, value.customAlertPortalGates,
			value.customAlertCrystalShards, value.customAlertInfinityTrails,
			value.customAlertGravityWells, value.customAlertSpectrumSteps,
			value.customAlertStarfallColumns, customAdditions(value), value.customAlertPulse,
			value.customAlertFrame, value.customAlertCallout, value.customAlertTextShake,
			value.customAlertTextGradientSpeed, value.customAlertSong, value.customAlertSoundTheme,
			value.customAlertDuration, value.customAlertSoundVolume);
		return cachedCustom;
	}

	public static void invalidateCustom() {
		cachedCustom = null;
	}

	private static int[] customAdditions(SafariConfig.SparklingConfig value) {
		return new int[] {value.customAlertMagicCircle, value.customAlertConstellationReveal,
			value.customAlertPrismaticWings, value.customAlertNorthernLights,
			value.customAlertCelestialClock, value.customAlertCrystalBloom,
			value.customAlertStardustVortex, value.customAlertCometImpact,
			value.customAlertArcaneSeal, value.customAlertLightPillars,
			value.customAlertDimensionalTear, value.customAlertCelestialWings,
			value.customAlertMeteorShower, value.customAlertCrownFormation,
			value.customAlertRealityRipple};
	}

	private static void setCustomAdditions(SafariConfig.SparklingConfig value, int[] levels) {
		value.customAlertMagicCircle = levels[0];
		value.customAlertConstellationReveal = levels[1];
		value.customAlertPrismaticWings = levels[2];
		value.customAlertNorthernLights = levels[3];
		value.customAlertCelestialClock = levels[4];
		value.customAlertCrystalBloom = levels[5];
		value.customAlertStardustVortex = levels[6];
		value.customAlertCometImpact = levels[7];
		value.customAlertArcaneSeal = levels[8];
		value.customAlertLightPillars = levels[9];
		value.customAlertDimensionalTear = levels[10];
		value.customAlertCelestialWings = levels[11];
		value.customAlertMeteorShower = levels[12];
		value.customAlertCrownFormation = levels[13];
		value.customAlertRealityRipple = levels[14];
	}

	public static String exportCustom() {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		StringBuilder recipe = new StringBuilder("2");
		for (Effect effect : EFFECTS) recipe.append(',').append(effect.value(config));
		recipe.append('|').append(config.customAlertDuration)
			.append('|').append(config.customAlertSoundVolume)
			.append('|').append(config.customAlertPerformanceBudget)
			.append('|').append(config.customAlertTimingMode)
			.append('|').append(Base64.getUrlEncoder().withoutPadding().encodeToString(
				config.customAlertCalloutText.getBytes(StandardCharsets.UTF_8)));
		return "SUCA:" + Base64.getUrlEncoder().withoutPadding().encodeToString(
			recipe.toString().getBytes(StandardCharsets.UTF_8));
	}

	public static boolean importCustom(String encoded) {
		try {
			if (encoded == null || !encoded.startsWith("SUCA:")) return false;
			String decoded = new String(Base64.getUrlDecoder().decode(encoded.substring(5)),
				StandardCharsets.UTF_8);
			String[] sections = decoded.split("\\|", -1);
			String[] values = sections[0].split(",");
			if (values.length != EFFECTS.size() + 1
					|| (!values[0].equals("1") && !values[0].equals("2"))) return false;
			boolean legacySongs = values[0].equals("1");
			SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
			for (int index = 0; index < EFFECTS.size(); index++) {
				Effect effect = EFFECTS.get(index);
				int value = Integer.parseInt(values[index + 1]);
				if (legacySongs && effect.soundSong()) value = migrateLegacySong(value);
				effect.set(config, value);
			}
			config.customAlertDuration = Math.clamp(Float.parseFloat(sections[1]), 1f, 999f);
			config.customAlertSoundVolume = Math.clamp(Integer.parseInt(sections[2]), 0,
				VOLUME_MANUAL_MAX);
			config.customAlertPerformanceBudget = 2;
			config.customAlertTimingMode = Math.clamp(Integer.parseInt(sections[4]), 0, 4);
			config.customAlertCalloutText = new String(Base64.getUrlDecoder().decode(sections[5]),
				StandardCharsets.UTF_8);
			invalidateCustom();
			return true;
		} catch (IllegalArgumentException | IndexOutOfBoundsException error) {
			return false;
		}
	}

	public static String describeCustomPreset(String encoded) {
		try {
			if (encoded == null || !encoded.startsWith("SUCA:")) return "Invalid preset";
			String decoded = new String(Base64.getUrlDecoder().decode(encoded.substring(5)),
				StandardCharsets.UTF_8);
			String[] sections = decoded.split("\\|", -1);
			String[] values = sections[0].split(",");
			if (values.length != EFFECTS.size() + 1) return "Invalid preset";
			boolean legacySongs = values[0].equals("1");
			int enabled = 0;
			int song = AlertSounds.SPARKLING_SONG_OFF;
			for (int index = 0; index < EFFECTS.size(); index++) {
				int value = Integer.parseInt(values[index + 1]);
				Effect effect = EFFECTS.get(index);
				if (effect.soundSong()) song = legacySongs ? migrateLegacySong(value) : value;
				else if (!effect.soundIntensity() && value > 0) enabled++;
			}
			return sections[1] + "s  ·  " + AlertSounds.sparklingSongShortLabel(song)
				+ "  ·  " + enabled + " effects";
		} catch (IllegalArgumentException | IndexOutOfBoundsException error) {
			return "Invalid preset";
		}
	}

	private static int migrateLegacySong(int song) {
		return song >= 18 ? AlertSounds.SPARKLING_SONG_OFF
			: Math.clamp(Math.round(song * 6f / 17f), 0, 6);
	}

	public static void saveCustomPreset(String requestedName) {
		String name = requestedName == null || requestedName.isBlank()
			? "Custom Alert" : requestedName.trim();
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		if (config.customAlertSavedPresets == null) config.customAlertSavedPresets = new java.util.ArrayList<>();
		String unique = name;
		int suffix = 2;
		boolean duplicate;
		do {
			duplicate = false;
			for (SafariConfig.SavedAlertPreset preset : config.customAlertSavedPresets) {
				if (preset.name.equalsIgnoreCase(unique)) {
					duplicate = true;
					unique = name + " (" + suffix++ + ")";
					break;
				}
			}
		} while (duplicate);
		config.customAlertSavedPresets.add(new SafariConfig.SavedAlertPreset(unique, exportCustom()));
		ConfigManager.save();
	}

	public static void resetCustom() {
		SafariConfig.SparklingConfig value = ConfigManager.get().sparkling;
		SafariConfig.SparklingConfig defaults = new SafariConfig.SparklingConfig();
		for (Effect effect : EFFECTS) effect.set(value, effect.value(defaults));
		value.customAlertDuration = defaults.customAlertDuration;
		value.customAlertSoundVolume = defaults.customAlertSoundVolume;
		value.customAlertPerformanceBudget = defaults.customAlertPerformanceBudget;
		value.customAlertTimingMode = defaults.customAlertTimingMode;
		value.customAlertCalloutText = defaults.customAlertCalloutText;
		invalidateCustom();
	}

	/** Seeds the custom editor from a preset without maintaining a second field map. */
	public static void copyToCustom(SparklingAlertStyle style) {
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		for (Effect effect : EFFECTS) effect.copyFrom(config, style);
		config.customAlertDuration = style.durationSeconds;
		config.customAlertSoundVolume = style.soundVolumePercent;
		config.customAlertCalloutText = "SPARKLING CAPTURE";
		invalidateCustom();
	}

	public static SparklingAlertStyle preset(int index) {
		return PRESETS[Math.clamp(index, 0, CUSTOM_INDEX - 1)];
	}

	/** Precomputes a monotonic sequence and phases every new effect into later presets. */
	private static SparklingAlertStyle[] createPresets() {
		SparklingAlertStyle[] presets = new SparklingAlertStyle[CUSTOM_INDEX];
		for (int index = 0; index < presets.length; index++) {
			int[] effects = new int[45];
			int baseIndex = Math.min(index, 27);
			int baseBudget = 3 + baseIndex * (ORIGINAL_EFFECT_ORDER.length * 6 - 3) / 27;
			for (int point = 0; point < baseBudget; point++) {
				int effect = ORIGINAL_EFFECT_ORDER[point % ORIGINAL_EFFECT_ORDER.length];
				effects[effect] = Math.min(6, effects[effect] + 1);
			}
			if (index >= 28) {
				int newBudget = Math.min(48, (index - 27) * 6);
				for (int point = 0; point < newBudget; point++) {
					int effect = 29 + point % 8;
					effects[effect] = Math.min(6, effects[effect] + 1);
				}
			}
			if (index >= 36) {
				int newestBudget = (index - 35) * 6;
				for (int point = 0; point < newestBudget; point++) {
					int effect = 37 + point % 5;
					effects[effect] = Math.min(6, effects[effect] + 1);
				}
			}
			int[] additions = new int[15];
			for (int addition = 0; addition < additions.length; addition++) {
				int start = 8 + addition * 2;
				if (index > start) additions[addition] = Math.min(6,
					1 + (index - start) * 5 / Math.max(1, CUSTOM_INDEX - 1 - start));
			}
			int soundTheme = Math.min(12, index * 13 / CUSTOM_INDEX);
			int soundSong = Math.min(AlertSounds.SPARKLING_SONG_COUNT - 1,
				index * AlertSounds.SPARKLING_SONG_COUNT / CUSTOM_INDEX);
			int duration = AlertSounds.defaultSparklingThemeDurationSeconds(soundTheme);
			int textShake = index < 4 ? 0
				: Math.min(6, 1 + (index - 4) * 6 / (CUSTOM_INDEX - 4));
			int textGradientSpeed = Math.min(6, 2 + index * 5 / CUSTOM_INDEX);
			presets[index] = new SparklingAlertStyle(
				effects[0], effects[1], effects[2], effects[3], effects[4], effects[5],
				effects[6], effects[7], effects[8], effects[9], effects[10], effects[11],
				effects[12], effects[13], effects[14], effects[15], effects[16], effects[17],
				effects[18], effects[19], effects[20], effects[21], effects[22], effects[23],
				effects[24], effects[25], effects[26], effects[27], effects[28], effects[29],
				effects[30], effects[31], effects[32], effects[33], effects[34], effects[35],
				effects[36], effects[37], effects[38], effects[39], effects[40], effects[41],
				additions, effects[42], effects[43], effects[44],
				textShake, textGradientSpeed,
				soundSong, soundTheme, duration, 100);
		}
		return presets;
	}

	public long displayMillis() {
		return Math.round(durationSeconds * 1_000f);
	}

	private static int clamp(int value) {
		return Math.clamp(value, 0, 6);
	}
}
