package dev.serko.safariutils.client;

import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.state.MonotonicTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.regex.Pattern;

/** One menu-owned, bounded ticket action. Capsule allocation remains the run-start authority. */
public final class TicketSelection {
    private static final String[] TIERS = {"Basic", "Economy", "Premium", "First Class"};
    private static final Pattern NEGATIVE = Pattern.compile("(?i)(?:don't|do not|doesn't|not enough|no tickets|no .*tickets|cannot|can't|insufficient|not own|not available|unavailable)");
    private static final Pattern COUNT = Pattern.compile("(?i)(?:owned|available|amount|tickets?(?: owned)?|you (?:have|own))\\s*:?\\s*([0-9]+)");
    private static final Pattern ACTION = Pattern.compile("(?i)\\bclick to (?:enter|use|select|redeem|consume)");
    private static Object menu, world, connection;
    private static long opened, nextCheck;
    private static boolean consumed, loggedOptions;
    private static int requested;
    private static Option readyOption;
    private static String waiting = "menu contents";
    private TicketSelection() { }

    static int preference(SafariConfig.GameplayConfig settings, RunMode mode) {
        int value = switch (mode) {
            case FULL_CLEAR -> settings.fullClearTicket;
            case UNIQUE -> settings.uniqueTicket;
            case SPARKLING -> settings.sparklingTicket;
        };
        return value >= 1 && value <= 4 ? value : 0;
    }
    static int tier(String name) {
        String normalized = plain(name).replace('-', ' ').replaceAll("\\s+", " ");
        for (int i = 0; i < TIERS.length; i++)
            if (normalized.matches("(?i)" + TIERS[i] + "(?: (?:Safari )?(?:Ticket|Experience))?(?: \\([0-9,]+\\))?")) return i + 1;
        return 0;
    }
    static String plain(String text) { return text.replaceAll("§.", "").trim(); }
    static boolean entryTitle(String text) { return plain(text).equalsIgnoreCase("Critter Safari Entry"); }
    record Option(int slot, int tier, Boolean usable) { }
    static Option choose(List<Option> options, int requested) {
        Option selected = null;
        for (Option option : options) {
            if (option.tier() != requested) continue;
            if (selected != null) return null;
            selected = option;
        }
        return selected != null && selected.usable() != null ? selected : null;
    }
    /** Prefer downgrades before spending a higher tier; never skip unconfirmed menu data. */
    static Option chooseAvailable(List<Option> options, int preferred) {
        if (preferred < 1 || preferred > 4) return null;
        for (int candidate = preferred; candidate >= 1; candidate--) {
            Option option = choose(options, candidate);
            if (option == null || option.usable()) return option;
        }
        for (int candidate = preferred + 1; candidate <= 4; candidate++) {
            Option option = choose(options, candidate);
            if (option == null || option.usable()) return option;
        }
        return choose(options, preferred); // All four tiers are confirmed unavailable.
    }
    static Boolean available(List<String> lore) {
        String text = String.join(" ", lore).replaceAll("§.", "").replace(",", "");
        if (NEGATIVE.matcher(text).find()) return false;
        var count = COUNT.matcher(text);
        if (count.find()) {
            try { return Long.parseLong(count.group(1)) > 0; }
            catch (NumberFormatException ignored) { return null; }
        }
        return ACTION.matcher(text).find() ? true : null;
    }
    static void manualInteraction(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container && TicketProtection.isEntryScreen(screen)) {
            AutomaticTicketEntry.manualOverride();
            menu = container.getMenu(); consumed = true;
            Minecraft client = Minecraft.getInstance(); world = client.level; connection = client.getConnection();
        }
    }
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != world || client.getConnection() != connection) {
            reset(); world = client.level; connection = client.getConnection();
        }
        Screen screen = ClientCompat.screen();
        if (!SafariLocation.inside() || client.player == null || client.gameMode == null
            || SessionManager.current() != null || !TicketProtection.isEntryScreen(screen)) { resetMenu(); return; }
        var container = (AbstractContainerScreen<?>) screen;
        var current = container.getMenu();
        long now = MonotonicTime.nowMillis();
        if (menu != current) {
            menu = current; loggedOptions = false; opened = now; nextCheck = now + 150; consumed = false; readyOption = null; waiting = "menu contents";
            requested = AutomaticTicketEntry.requestedTier(preference(ConfigManager.get().gameplay, RunMode.current()));
            DebugLog.line("TICKET/SELECT", "opened mode=" + RunMode.current() + " requested=" + requested);
        }
        if (AutomaticTicketEntry.blocksSelection()) consumed = true;
        if (consumed || requested == 0) return;
        if (AutomaticTicketEntry.requestedTier(preference(ConfigManager.get().gameplay, RunMode.current())) != requested) { consumed = true; return; }
        if (now - opened > 5000) {
            consumed = true;
            DebugLog.line("TICKET/SELECT", "manual fallback: " + waiting + " requested=" + TIERS[requested - 1]);
            if (DebugLog.isEnabled()) {
                int logged = 0;
                for (var slot : current.slots) {
                    var stack = slot.getItem();
                    if (slot.container == client.player.getInventory() || !stack.is(Items.NAME_TAG)) continue;
                    if (++logged > 8) break;
                    var lore = stack.get(DataComponents.LORE);
                    String details = plain(stack.getHoverName().getString()) + " lore="
                        + (lore == null ? "[]" : lore.lines().stream().limit(16).map(line -> plain(line.getString())).toList());
                    DebugLog.line("TICKET/SELECT", details.substring(0, Math.min(details.length(), 1200)));
                }
            }
            ClientMessages.send("Automatic ticket selection could not confirm the menu; choose a ticket manually", ClientMessages.Tone.INFO);
            return;
        }
        if (now < nextCheck) return;
        nextCheck = now + 100;
        if (client.player.containerMenu != current || !current.getCarried().isEmpty()) {
            waiting = "container mismatch or held cursor item"; readyOption = null; return;
        }
        if (TicketProtection.shouldBlock()) {
            waiting = "party protection"; readyOption = null; return;
        }
        List<Option> options = new ArrayList<>();
        for (int index = 0; index < current.slots.size(); index++) {
            var slot = current.slots.get(index);
            var stack = slot.getItem();
            if (slot.container == client.player.getInventory() || !stack.is(Items.NAME_TAG)) continue;
            int tier = tier(stack.getHoverName().getString());
            if (tier == 0) continue;
            var lore = stack.get(DataComponents.LORE);
            Boolean usable = lore == null ? null : available(lore.lines().stream().map(line -> line.getString()).toList());
            if (!loggedOptions && DebugLog.isEnabled() && lore != null) {
                String details = "option=" + tier + " usable=" + usable + " name=" + plain(stack.getHoverName().getString())
                    + " lore=" + lore.lines().stream().limit(16).map(line -> plain(line.getString())).toList();
                DebugLog.line("TICKET/SELECT", details.substring(0, Math.min(1200, details.length())));
            }
            options.add(new Option(index, tier, usable));
        }
        if (!options.isEmpty()) loggedOptions = true;
        boolean automaticEntry = AutomaticTicketEntry.choosingTicket();
        Option selected = automaticEntry ? chooseAvailable(options, requested) : choose(options, requested);
        if (selected == null) {
            waiting = "requested option missing, ambiguous, or availability unknown";
            readyOption = null; return;
        }
        // Require the same confirmed option on two polls, not unrelated ticket tiers.
        if (!selected.equals(readyOption)) { readyOption = selected; return; }
        int slotId = selected.slot();
        boolean usable = selected.usable();
        consumed = true; // Never retry a click after a delayed/ambiguous server response.
        if (!usable) {
            if (automaticEntry) { AutomaticTicketEntry.unavailable(); return; }
            ClientMessages.send(TIERS[requested - 1] + " ticket unavailable; choose a ticket manually", ClientMessages.Tone.INFO);
            return;
        }
        AutomaticTicketEntry.submitting();
        if (ClientCompat.pickupSlot(current.containerId, slotId)) {
            StartingItemsWatch.onTicketSubmitted("automatic " + TIERS[selected.tier() - 1] + " ticket");
            DebugLog.line("TICKET/SELECT", "submitted=" + TIERS[selected.tier() - 1] + " preferred=" + TIERS[requested - 1] + " fallback=" + (selected.tier() != requested));
        } else {
            AutomaticTicketEntry.submissionFailed();
            ClientMessages.send("Automatic ticket selection unavailable; choose a ticket manually", ClientMessages.Tone.INFO);
        }
    }
    private static void resetMenu() { menu = null; consumed = false; requested = 0; readyOption = null; }
    private static void reset() { resetMenu(); world = null; connection = null; }
}
