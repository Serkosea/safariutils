package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import java.util.ArrayList;
import java.util.List;

/** On-demand help: shared mode metadata, existing chat palette, no network or tick work. */
final class SafariCommandHelp {
    record Entry(String command, String aliases, String description) { }
    private SafariCommandHelp() { }

    static List<Entry> entries(boolean debug) {
        List<Entry> entries = new ArrayList<>();
        if (debug) {
            entries.add(new Entry("/safari debug", "", "Diagnostic summary"));
            String[][] commands = {
                {"status", "Logging and entity-research status"}, {"log", "Automatic logging status"},
                {"save", "Save this Safari run's log"}, {"mark <note>", "Add a note to the log"},
                {"performance", "Performance profiling summary"}, {"run", "Current run state"},
                {"entities", "Nearby entity details"}, {"nearby", "Closest entity details"},
                {"critters", "Critter pairing details"}, {"waypoints", "Marker diagnostics"},
                {"block", "Targeted entity or block details"}, {"catalogs", "Detection catalog summary"},
                {"tablist", "Server tab-list details"}, {"jointimer", "Ticket connection window"},
                {"room", "Party-sync room status"}, {"replay", "Replay capture diagnostics"},
                {"testalert", "Preview Safari alerts"},
                {"useticket <basic|economy|premium|firstclass>", "Test automatic entry (consumes a real ticket)"},
                {"useticket cancel", "Cancel automatic entry"}
            };
            for (String[] command : commands)
                entries.add(new Entry("/safari debug " + command[0], "", command[1]));
            return List.copyOf(entries);
        }
        entries.add(new Entry("/safari", "", "Settings"));
        entries.add(new Entry("/safari trading", "", "Ticket trading members and settings"));
        entries.add(new Entry("/safari gui", "", "Edit HUD positions"));
        entries.add(new Entry("/safari stats", "", "Stats, history, PBs, profit & collections"));
        entries.add(new Entry("/sparkling", "", "Open your Sparklings tab"));
        entries.add(new Entry("/safari profit", "", "Estimated critter earnings"));
        entries.add(new Entry("/safari shared", "", "Current party's known shared Sparklings"));
        entries.add(new Entry("/safari lookup [name]", "", "Profile lookup; omit name for yourself"));
        entries.add(new Entry("/safari missing [name]", "", "Missing Sparklings; omit name for yourself"));
        entries.add(new Entry("/safari tickets [name]", "", "Ticket counts; omit name for yourself"));
        for (RunMode mode : RunMode.values()) {
            String aliases = String.join(", ", mode.commands().subList(1, mode.commands().size()));
            entries.add(new Entry("/safari " + mode.commands().getFirst(), aliases, "Select " + mode.title() + " mode"));
        }
        entries.add(new Entry("/safari mode", "", "Show or select a run mode"));
        entries.add(new Entry("/safari help", "", "Show this command list"));
        if (BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS))
            entries.add(new Entry("/safari help debug", "", "Developer commands"));
        return List.copyOf(entries);
    }

    static int show(boolean debug) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(debug ? "Developer Commands" : "Safari Commands")
            .withColor(0x8DE8FF).withStyle(style -> style.withBold(true)));
        for (Entry entry : entries(debug)) {
            String suggestion = entry.command().replace("<note>", "").replace("[name]", "");
            var command = Component.literal(entry.command()).withColor(StatColours.COINS)
                .withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(suggestion))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to fill chat — " + entry.description())
                        .withColor(0x8DE8FF))));
            if (!entry.aliases().isEmpty())
                command.append(Component.literal(" (" + entry.aliases() + ")").withColor(0x8DE8FF));
            lines.add(command.append(Component.literal(" — " + entry.description()).withColor(0xF2F5FA)));
        }
        lines.add(Component.literal("/su and /safariutils work too • Click a command to fill chat")
            .withColor(0x8DE8FF));
        ClientMessages.sendLines(lines);
        return 1;
    }
}
