package dev.serko.safariutils.state;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.api.PartyItemSyncProviders;
import dev.serko.safariutils.client.BirdfeederWatch;
import dev.serko.safariutils.client.CritterState;
import dev.serko.safariutils.client.CommandCoordinator;
import dev.serko.safariutils.client.EncounterAlerts;
import dev.serko.safariutils.client.FloorDrops;
import dev.serko.safariutils.client.HideyhoSolver;
import dev.serko.safariutils.client.HotspotWatch;
import dev.serko.safariutils.client.JoinWindowDiagnostics;
import dev.serko.safariutils.client.MoundSpotter;
import dev.serko.safariutils.client.NestTracker;
import dev.serko.safariutils.client.SafariLocation;
import dev.serko.safariutils.client.SafariObjectives;
import dev.serko.safariutils.client.ShiningCoinWatch;
import dev.serko.safariutils.client.StillCritters;
import dev.serko.safariutils.client.StructuredDiagnostics;
import dev.serko.safariutils.client.TicketTrading;
import dev.serko.safariutils.session.SessionManager;

/** The one ordered fan-out boundary for normalized Safari observations. */
public final class SafariEventDispatcher {
	private SafariEventDispatcher() { }

	public static void dispatch(SafariEvent event) {
		if (event == null) return;
		StructuredDiagnostics.event(event);
		String line = event.rawText();
		CommandCoordinator.observeServerMessage(line);
		CritterState.onCritterEvent(event.critterEvent(), line);
		PartyItemSyncProviders.onServerMessage(line, event.critterEvent());
		SafariLocation.onChatMessage(line);
		SessionManager.onChatMessage(line, event.critterEvent());
		TicketTrading.onChatMessage(line);
		if (BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS)) {
			JoinWindowDiagnostics.onChatMessage(line);
			dev.serko.safariutils.client.EntityResearchLog.onChatMessage(line);
		}
		EncounterAlerts.onChatMessage(line);
		NestTracker.onChatMessage(line);
		BirdfeederWatch.onChatMessage(line);
		ShiningCoinWatch.onChatMessage(line);
		SafariObjectives.onChatMessage(line);
		HotspotWatch.onChatMessage(line);
		HideyhoSolver.onChatMessage(line);
		StillCritters.onChatMessage(line, event.critterEvent());
		FloorDrops.onChatMessage(line);
        dev.serko.safariutils.client.ScrappyFeeding.onChatMessage(line);
		MoundSpotter.onChatMessage(line);
	}
}
