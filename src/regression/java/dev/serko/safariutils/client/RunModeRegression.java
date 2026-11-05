package dev.serko.safariutils.client;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** Covers old settings combinations, every mode transition, and public command aliases. */
public final class RunModeRegression {
    public static int run() throws Exception {
        int checks = 0;
        var field = ConfigManager.class.getDeclaredField("config");
        field.setAccessible(true);
        Object saved = field.get(null);
        var gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
        try {
            for (boolean enabled : new boolean[]{false, true}) {
                for (boolean ignore : new boolean[]{false, true}) {
                    for (RunMode target : RunMode.values()) {
                        var config = new SafariConfig();
                        config.sparkling.sparklingMode = enabled;
                        config.sparkling.sparklingIgnoreUniques = ignore;
                        config.sparkling.alwaysShowProfitableCritters = true;
                        config.sparkling.sparklingAlwaysActiveCritters = 123;
                        config.sparkling.ticketTradingEnabled = true;
                        field.set(null, config);
                        RunMode original = enabled ? ignore ? RunMode.SPARKLING : RunMode.UNIQUE : RunMode.FULL_CLEAR;
                        check(RunMode.current() == original, "saved mode mapping"); checks++;
                        var expected = gson.toJsonTree(config).getAsJsonObject();
                        boolean changed = target.applyTo(config.sparkling);
                        check(changed == (original != target) && RunMode.current() == target, "transition"); checks++;
                        check(!target.applyTo(config.sparkling), "selection must be idempotent"); checks++;
                        check(SparklingMode.enabled() == target.focused()
                            && SparklingMode.ignoreUniques() == (target == RunMode.SPARKLING)
                            && SparklingMode.displayName().equals(target.hudTitle()), "HUD/filter agreement"); checks++;
                        var actual = gson.toJsonTree(config).getAsJsonObject();
                        for (var json : new com.google.gson.JsonObject[]{expected, actual}) {
                            json.getAsJsonObject("sparkling").remove("sparklingMode");
                            json.getAsJsonObject("sparkling").remove("sparklingIgnoreUniques");
                        }
                        check(expected.equals(actual), "mode selection changed unrelated settings"); checks++;
                        var restored = gson.fromJson(gson.toJson(config.sparkling), SafariConfig.SparklingConfig.class);
                        check(RunMode.from(restored) == target, "mode persistence round trip"); checks++;
                    }
                }
            }
            var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
            for (String rootName : new String[]{"safari", "su", "safariutils"}) {
                var root = ClientCommands.literal(rootName);
                SafariCommands.addModeCommands(root);
                dispatcher.register(root);
                for (RunMode mode : RunMode.values()) {
                    for (String alias : mode.commands()) {
                        for (String middle : new String[]{" ", " mode "}) {
                            var parsed = dispatcher.parse(rootName + middle + alias, null);
                            check(!parsed.getReader().canRead() && parsed.getContext().getCommand() != null,
                                "mode command unavailable: " + rootName + middle + alias); checks++;
                        }
                    }
                }
                check(dispatcher.getRoot().getChild(rootName).getChild("mode").getCommand() != null,
                    "mode status unavailable"); checks++;
            }
            var commands = new CommandDispatcher<FabricClientCommandSource>();
            SafariCommands.register(commands);
            boolean developer = dev.serko.safariutils.BuildVersion.has(
                dev.serko.safariutils.BuildVersion.Capability.DEVELOPER_TOOLS);
            for (String rootName : new String[]{"safari", "su", "safariutils"}) {
                check(commands.getRoot().getChild(rootName).getChild("refresh") == null, "refresh alias removed"); checks++;
                for (String lookup : new String[]{"lookup", "tickets", "missing"}) {
                    for (String suffix : new String[]{"", " TestPlayer"}) {
                        var parsed = commands.parse(rootName + " " + lookup + suffix, null);
                        check(!parsed.getReader().canRead() && parsed.getContext().getCommand() != null,
                            "self and named lookup available on every alias"); checks++;
                    }
                }
                check(commands.getRoot().getChild(rootName).getChild("standard") == null
                    && commands.getRoot().getChild(rootName).getChild("mode").getChild("standard") == null,
                    "removed standard alias remains"); checks++;
                check((commands.getRoot().getChild(rootName).getChild("help").getChild("debug") != null)
                    == developer, "developer help edition boundary"); checks++;
            }
            for (boolean debug : developer ? new boolean[]{false, true} : new boolean[]{false}) {
                for (var entry : SafariCommandHelp.entries(debug)) {
                    String command = entry.command().substring(1).replace("<note>", "test note").replace("<name>", "TestPlayer").replace("[name]", "TestPlayer").replace("<basic|economy|premium|firstclass>", "basic");
                    var parsed = commands.parse(command, null);
                    check(!parsed.getReader().canRead() && parsed.getContext().getCommand() != null,
                        "help lists an unavailable command: " + command); checks++;
                }
            }
            var listed = SafariCommandHelp.entries(false).stream().map(SafariCommandHelp.Entry::command).toList();
            for (var child : commands.getRoot().getChild("safari").getChildren()) {
                String name = child.getName();
                boolean modeAlias = java.util.Arrays.stream(RunMode.values()).anyMatch(mode -> mode.commands().contains(name));
                check(name.equals("debug") || modeAlias || listed.stream().anyMatch(command -> command.equals("/safari " + name) || command.startsWith("/safari " + name + " ")),
                    "player command missing from help: " + name); checks++;
            }
            if (developer) for (var child : commands.getRoot().getChild("safari").getChild("debug").getChildren()) {
                check(SafariCommandHelp.entries(true).stream().anyMatch(entry ->
                    entry.command().equals("/safari debug " + child.getName())
                    || entry.command().startsWith("/safari debug " + child.getName() + " ")),
                    "developer command missing from help: " + child.getName()); checks++;
            }
        } finally { field.set(null, saved); }
        return checks;
    }
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
