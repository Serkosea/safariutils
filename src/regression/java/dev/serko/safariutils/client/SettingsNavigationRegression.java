package dev.serko.safariutils.client;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/** Exercises the actual navigation builder without opening a game screen. */
public final class SettingsNavigationRegression {
    public static int run() throws Exception {
        Field config = ConfigManager.class.getDeclaredField("config");
        config.setAccessible(true);
        Object previous = config.get(null);
        try {
            config.set(null, new SafariConfig());
            var screen = navigationOnlyScreen();
            List<?> areas = list(screen, "settingsAreas");
            Object huds = find(areas, "key", "display");
            List<?> tabs = list(huds, "tabs");
            check(tabs.size() == 5, "HUD tabs must not resurrect More");
            Object borders = find(tabs, "key", "borders");
            check(value(borders, "name").equals("Screen Borders"), "border tab title");
            List<?> borderPages = list(borders, "pages");
            check(borderPages.size() == 1, "screen borders must have no HUD layout or old hierarchy");
            check(list(borderPages.getFirst(), "fields").stream().anyMatch(f -> ((Field) f).getName().equals("personalBestsTabBorderColour")), "PB border remains accessible");
            check(list(borderPages.getFirst(), "fields").stream().map(f -> ((Field) f).getName()).toList()
                .equals(List.of("statsTabBorderColour","historyTabBorderColour","profitTabBorderColour","personalBestsTabBorderColour","currentRunTabBorderColour")), "screen border ordering");
            Map<?, ?> cards = (Map<?, ?>) value(screen, "normalSettingCardCache");
            var pityCard = (List<?>) cards.get(SafariConfig.DisplayConfig.class.getField("hitboxPityTitle"));
            check(pityCard.stream().map(f -> ((Field)f).getName()).toList().equals(List.of("hitboxPityTitle", "eagleRarity")), "pity and Eagle rarity share their card");
            var timerCard = (List<?>) cards.get(SafariConfig.DisplayConfig.class.getField("litterbugTimers"));
            check(timerCard.size() == 1, "Litterbug timer has its own card");
            check(cards.containsKey(SafariConfig.DisplayConfig.class.getField("hitboxRarityColour")), "marker colors must not be dropped by card slicing");
            int checks = 8;
            for (String name : List.of("Progress HUD", "Missing HUD", "Contest HUD", "Party Objective HUD")) {
                List<?> pages = list(find(tabs, "name", name), "pages");
                Object options = find(pages, "name", name + " Options");
                Object border = find(pages, "name", "Border Options");
                var depths = SafariSettingsScreen.class.getDeclaredMethod("calculateEffectiveDepths", List.class);
                depths.setAccessible(true);
                Map<?, ?> result = (Map<?, ?>) depths.invoke(null, pages);
                check(result.get(options).equals(result.get(border)), "HUD options indentation: " + name);
                checks++;
            }
            check(value(find(areas, "key", "sparkling"), "name").equals("Unique Mode"), "mode category title");
            Object sparklings = find(areas, "key", "sparklings");
            check(areas.indexOf(sparklings) == areas.indexOf(find(areas, "key", "sparkling")) + 1, "Sparklings follows Unique Mode");
            List<?> alertTabs = list(sparklings, "tabs");
            check(alertTabs.stream().map(t -> value(t, "name")).toList().equals(List.of("Banner", "Chat", "Special Catch")), "three Sparkling alert tabs");
            List<?> runTabs = list(find(areas, "key", "gameplay"), "tabs");
            check(runTabs.stream().map(t -> value(t, "name")).toList()
                .equals(List.of("General", "Tickets", "Party", "Visual", "Profit")), "run tab order without fallback More");
            List<?> ticketPages = list(find(runTabs, "key", "tickets"), "pages");
            check(ticketPages.stream().map(p -> value(p, "key")).toList().equals(dev.serko.safariutils.BuildVersion.SAFE
                ? List.of("gameplay.ticket-protection", "gameplay.ticket-selection", "gameplay.ticket-trading")
                : List.of("gameplay.ticket-protection", "gameplay.ticket-selection", "gameplay.ticket-sparkling-entry", "gameplay.ticket-trading")), "ticket card order");
            check(list(find(ticketPages, "key", "gameplay.ticket-selection"), "fields").stream()
                .map(f -> ((Field) f).getName()).toList().equals(List.of("fullClearTicket", "uniqueTicket", "sparklingTicket")), "mode choices stay grouped");
            find(list(find(runTabs, "key", "party"), "pages"), "key", "gameplay.coordination");
            List<?> general=list(find(runTabs,"key","safari"),"pages");
            check(general.stream().map(p->value(p,"key")).toList().equals(dev.serko.safariutils.BuildVersion.SAFE
                ? List.of("gameplay.auto-hideyho","gameplay.personal-bests","gameplay.honeyhives")
                : List.of("gameplay.auto-hideyho","gameplay.candles","gameplay.personal-bests","gameplay.honeyhives")),"general ordering");
            check(list(general.getLast(),"fields").stream().map(f->((Field)f).getName()).toList().equals(List.of("honeyhiveBeacons","honeyhiveColour")),"hive color grouped below toggle");
            var visual=list(find(runTabs,"key","visual"),"pages");
            check(visual.stream().map(p->value(p,"key")).toList().equals(List.of("display.safari.chat","display.safari.comfort","gameplay.prediction","display.safari.water","display.safari.prefix")),"visual order with prefix last");
            return checks + 11;
        } finally { config.set(null, previous); }
    }
    // Screen's constructor requires a running Minecraft renderer. This test only
    // exercises the navigation model; no GUI or game singleton is fabricated.
    private static SafariSettingsScreen navigationOnlyScreen() throws Exception {
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        var allocator = (sun.misc.Unsafe) singleton.get(null);
        var screen = (SafariSettingsScreen) allocator.allocateInstance(SafariSettingsScreen.class);
        for (String name : List.of("categories", "settingsAreas")) {
            Field field = SafariSettingsScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(screen, new java.util.ArrayList<>());
        }
        for (String name : List.of("effectiveDepthCache", "settingCardCache", "normalSettingCardCache",
                "settingOrderCache", "settingTextLayoutCache")) {
            Field field = SafariSettingsScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(screen, new java.util.HashMap<>());
        }
        var load = SafariSettingsScreen.class.getDeclaredMethod("loadCategories");
        load.setAccessible(true);
        load.invoke(screen);
        return screen;
    }
    private static Object value(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static List<?> list(Object owner, String name) { return (List<?>) value(owner, name); }
    private static Object find(List<?> values, String field, String expected) {
        return values.stream().filter(v -> value(v, field).equals(expected)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing navigation: " + expected));
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
