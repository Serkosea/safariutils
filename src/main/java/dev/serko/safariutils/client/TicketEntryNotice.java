package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import dev.serko.safariutils.state.PartyState;

/** Shared by manual and automatic entry in every edition; only server-confirmed admission sends. */
public final class TicketEntryNotice {
    private static final Admission admission = new Admission();
    private TicketEntryNotice() { }
    static final class Admission {
        private Object world;
        private long visit;
        boolean consume(Object activeWorld, long activeVisit, boolean sparkling) {
            if (activeWorld == null || activeVisit <= 0 || world == activeWorld && visit == activeVisit) return false;
            world = activeWorld;
            visit = activeVisit;
            return sparkling;
        }
    }
    public static void onTicketActivated() {
        boolean known = PartySparklingDiscoveries.activeMask() != 0 || SparklingWatch.hasOutstanding();
        if (!admission.consume(Minecraft.getInstance().level, SafariLocation.insideSinceMillis(), known)) return;
        if (PartyState.snapshot().inParty()) ChatQueue.enqueueSafariScoped("pc [SU] In Run Now!", true);
        else ClientMessages.send("In Run Now!", ClientMessages.Tone.SUCCESS);
        DebugLog.line("TICKET/ENTRY", "confirmed admission after known sparkling; entry notice queued");
    }
}
