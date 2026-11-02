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
    private static boolean consumed;
    private static int requested;
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
        String normalized = name.replaceAll("§.", "").replace('-', ' ').trim();
        for (int i = 0; i < TIERS.length; i++)
            if (normalized.equalsIgnoreCase(TIERS[i] + " Ticket") || normalized.equalsIgnoreCase(TIERS[i] + " Safari Ticket")) return i + 1;
        return 0;
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
        if (client.player.containerMenu != current || !current.getCarried().isEmpty()) return;
        long now = MonotonicTime.nowMillis();
        if (menu != current) {
            menu = current; opened = now; nextCheck = now + 150; consumed = false;
            requested = preference(ConfigManager.get().gameplay, RunMode.current());
        }
        if (consumed || requested == 0) return;
        if (preference(ConfigManager.get().gameplay, RunMode.current()) != requested) { consumed = true; return; }
        if (now - opened > 5000) {
            consumed = true;
            DebugLog.line("TICKET/SELECT", "manual fallback: menu unavailable or party protection active");
            return;
        }
        if (now < nextCheck) return;
        nextCheck = now + 100;
        if (TicketProtection.shouldBlock() || ConfigManager.get().gameplay.protectSafariTicket
            && !dev.serko.safariutils.state.PartyState.snapshot().known()) return;
        int slotId = -1;
        Set<Integer> tiers = new HashSet<>();
        Boolean usable = null;
        for (int index = 0; index < current.slots.size(); index++) {
            var slot = current.slots.get(index);
            var stack = slot.getItem();
            if (slot.container == client.player.getInventory() || !stack.is(Items.NAME_TAG)) continue;
            int tier = tier(stack.getHoverName().getString());
            if (tier == 0) continue;
            if (!tiers.add(tier)) return; // Ambiguous or partially refreshed menu.
            if (tier != requested) continue;
            slotId = index;
            var lore = stack.get(DataComponents.LORE);
            if (lore != null) usable = available(lore.lines().stream().map(line -> line.getString()).toList());
        }
        if (tiers.size() != 4 || slotId < 0 || usable == null) return;
        consumed = true; // Never retry a click after a delayed/ambiguous server response.
        if (!usable) {
            ClientMessages.send(TIERS[requested - 1] + " ticket unavailable; choose a ticket manually", ClientMessages.Tone.INFO);
            return;
        }
        if (ClientCompat.pickupSlot(current.containerId, slotId)) {
            StartingItemsWatch.onTicketSubmitted("automatic " + TIERS[requested - 1] + " ticket");
            DebugLog.line("TICKET/SELECT", "submitted=" + TIERS[requested - 1]);
        } else {
            ClientMessages.send("Automatic ticket selection unavailable; choose a ticket manually", ClientMessages.Tone.INFO);
        }
    }
    private static void resetMenu() { menu = null; consumed = false; requested = 0; }
    private static void reset() { resetMenu(); world = null; connection = null; }
}
