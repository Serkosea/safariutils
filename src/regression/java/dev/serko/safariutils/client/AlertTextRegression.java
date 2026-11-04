package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import java.util.Locale;

/** Exercises formatting without a world, network, settings writes, or outgoing messages. */
public final class AlertTextRegression {
    public static int run() throws Exception {
        int checks = 0;
        var hideyhoCatch = dev.serko.safariutils.parse.ChatParser.sparklingCatch(
            "SPARKLING! Serkosea found a SPARKLING Hideyho!");
        check(hideyhoCatch != null && hideyhoCatch.critter().name().equals("Hideyho")
            && hideyhoCatch.catcher().equals("Serkosea"), "recorded interaction Sparkling announcement"); checks++;
        check(dev.serko.safariutils.parse.ChatParser.sparklingCatch(
            "SPARKLING! Serkosea caught a SPARKLING Flitter!") != null, "capsule announcement preserved"); checks++;
        check(dev.serko.safariutils.parse.ChatParser.sparklingCatch(
            "Party > Serkosea: SPARKLING! Serkosea found a SPARKLING Hideyho!") == null,
            "quoted interaction announcement rejected"); checks++;
        var reward = dev.serko.safariutils.parse.ChatParser.parse(
            "CAPTURE! You found the SPARKLING Hideyho, and as a reward it gave you a Rainbow Feather and 60x Hideyho Shard!", "Serkosea");
        check(reward != null && reward.sparkling() && reward.isCatch(), "recorded Hideyho reward remains a catch"); checks++;

        check(ClientMessages.playerLookupFailure(new java.util.concurrent.CompletionException(
            new dev.serko.safariutils.api.LookupCooldownException(1001))).equals("Lookup cooldown — try again in 2s"), "rounded cooldown survives wrapper"); checks++;
        check(ClientMessages.playerLookupFailure(new java.util.concurrent.CompletionException(
            new dev.serko.safariutils.api.ServiceRequestException("HTTP 429", 429, 8))).contains("8s"), "server retry delay survives wrapper"); checks++;
        check(ClientMessages.playerLookupFailure(new IllegalStateException("A lookup is already loading")).contains("still loading"), "busy admission"); checks++;
        check(ClientMessages.playerLookupFailure(new java.net.http.HttpTimeoutException("request timed out")).contains("timed out"), "timeout reason"); checks++;
        check(ClientMessages.playerLookupFailure(new java.io.IOException("Minecraft player not found: Test")).contains("check the username"), "unknown player reason"); checks++;
        check(!ClientMessages.playerLookupFailure(new java.io.IOException("private internal detail")).contains("private internal detail"), "unknown failure does not leak raw response"); checks++;

        String plain = "Sparkling Rockmite Found!";
        check(AlertText.format(plain) == plain, "tag-free fast path"); checks++;
        check(AlertText.format(null).isEmpty(), "null template"); checks++;
        for (SafariBiome biome : SafariBiome.values()) {
            String name = biome.displayName();
            String letter = name.substring(0,1).toLowerCase(Locale.ROOT);
            String text = AlertText.format("<BIOMELETTER>d <BIOME> <BIOME_UPPER> <BIOMELETTER_UPPER>",
                "<BIOME>", name);
            check(text.equals(letter + "d " + name + " " + name.toUpperCase(Locale.ROOT) + " "
                + letter.toUpperCase(Locale.ROOT)), "biome derivatives " + name); checks++;
            check(AlertText.format(ChatTemplateFormats.BIOME_UNIQUES.mutualsTemplate(),
                "<BIOME>", name).equals(letter + "d"), "Mutuals completion wire format"); checks++;
            check(AlertText.format(ChatTemplateFormats.HOTSPOT.mutualsTemplate(),
                "<BIOME>", name).equals("[SU] Hotspot: " + name), "Mutuals hotspot wire format"); checks++;
        }
        check(AlertText.format("<CRITTER> <RARITY> <BIOMELETTER>",
            "<CRITTER>", "Gemzie").equals("Gemzie EPIC <BIOMELETTER>"), "species event context"); checks++;
        check(AlertText.format("<BIOME>", "<CRITTER>", "Gemzie", "<BIOME>", "Forest")
            .equals("Forest"), "explicit biome wins"); checks++;
        check(AlertText.format("<BIOMELETTER>", "<BIOME>", "Unrecognized")
            .equals("?"), "invalid explicit biome must not use player biome"); checks++;
        check(AlertText.format("<ITEMS> <MODE>", "<ITEMS>", "literal <MODE>")
            .equals("literal <MODE> <MODE>"), "replacement text was recursively expanded"); checks++;
        check(AlertText.format("<UNKNOWN> <CRITTER> <COUNT>").equals("<UNKNOWN> <CRITTER> <COUNT>"),
            "unsupported/event-only tokens must be preserved"); checks++;
        check(AlertText.format("x <BROKEN").equals("x <BROKEN"), "unclosed token"); checks++;
        check(AlertText.format("<<COUNT>>", "<COUNT>", "2").equals("<2>"), "nested delimiters"); checks++;
        check(AlertText.format("<COUNT>/<COUNT>", "<COUNT>", "3", "<COUNT>", "8", "odd")
            .equals("3/3"), "legacy first replacement and odd argument handling"); checks++;
        check(AlertText.format("<COUNT>", "<COUNT>", null).equals("<COUNT>"), "null replacement"); checks++;
        check(AlertText.format("<PLAYER> <MODE> <RUN_TIME> <X> <STAGE> <BIOMELETTER>")
            .equals("<PLAYER> <MODE> <RUN_TIME> <X> <STAGE> <BIOMELETTER>"), "removed context has no runtime fallback"); checks++;
        check(AlertText.spawn("<CRITTER> <X> <Y> <Z>", "Macaw", new net.minecraft.world.phys.Vec3(-12.36, 65.04, 0.06))
            .equals("Macaw -12.4 65.0 0.1"), "spawn coordinates to tenths"); checks++;
        check(AlertText.spawn("<CRITTER> <X> <Y> <Z>", "Macaw", null).equals("Macaw ? ? ?"),
            "remote spawn must not report player position"); checks++;
        check(AlertText.coordinate(-0.01).equals("0.0") && AlertText.coordinate(Double.NaN).equals("?"), "coordinate edge cases"); checks++;
        var configField = ConfigManager.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object original = configField.get(null);
        try {
            var settings = new SafariConfig();
            configField.set(null, settings);
        var config = new SafariConfig.PartyConfig();
        for (var format : ChatTemplateFormats.values()) {
            var field = SafariConfig.PartyConfig.class.getField(format.setting());
            String before = (String)field.get(config);
            check(format.format(config, "<BIOME>", "Icy").equals(before.replace("<BIOME>", "Icy")),
                "custom text was replaced by compatibility preset"); checks++;
            check(before.equals(field.get(config)), "formatting mutated preferences"); checks++;
        }

            for (Object owner : new Object[]{settings.alerts, settings.party}) {
                for (var field : owner.getClass().getFields()) {
                    if (field.getType() == String.class && field.getName().endsWith("Text")
                        && (field.getName().startsWith("gemzie") || field.getName().startsWith("wumpa")
                            || field.getName().startsWith("doomspiral")))
                        field.set(owner, "<CRITTER>|<STAGE>|<RARITY>|<BIOMELETTER>");
                }
            }
            Class<?> stages = Class.forName("dev.serko.safariutils.client.EncounterAlerts$Stage");
            for (String methodName : new String[]{"encounterBannerText", "encounterChatText"}) {
                var method = EncounterAlerts.class.getDeclaredMethod(methodName, String.class, stages);
                method.setAccessible(true);
                for (String species : new String[]{"Gemzie", "Wumpa", "Doomspiral"}) {
                    var critter = dev.serko.safariutils.data.Critters.byName(species);
                    for (Object stage : stages.getEnumConstants()) {
                        String expected = species + "|<STAGE>|" + critter.rarity().name() + "|<BIOMELETTER>";
                        check(method.invoke(null, species, stage).equals(expected), "live encounter context " + species); checks++;
                    }
                }
            }
        } finally { configField.set(null, original); }
        String many = "<".repeat(10000) + "COUNT>";
        check(AlertText.format(many, "<COUNT>", "2").equals("<".repeat(9999)+"2"),
            "long malformed prefix"); checks++;
        return checks;
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
