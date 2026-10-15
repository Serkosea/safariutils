package dev.serko.safariutils.state;

import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.parse.CritterEvent;

/** Single parsing and sequencing boundary for server observations. */
public final class SafariEventStream {
	private static long sequence;

	private SafariEventStream() {
	}

	public static SafariEvent serverChat(String cleanLine, String selfName) {
		CritterEvent critter = ChatParser.parse(cleanLine, selfName);
		SafariEvent.Type type = typeOf(critter);
		return new SafariEvent(++sequence, System.currentTimeMillis(), System.nanoTime(),
			SafariRuntimeState.current(), type, EvidenceLevel.SERVER, "chat", cleanLine, critter);
	}

	private static SafariEvent.Type typeOf(CritterEvent event) {
		if (event == null) return SafariEvent.Type.SERVER_CHAT;
		return switch (event.type()) {
			case ATTEMPT -> SafariEvent.Type.CRITTER_ATTEMPT;
			case FAILED -> SafariEvent.Type.CRITTER_BREAKOUT;
			case OWN_CATCH, SHARED_CATCH -> SafariEvent.Type.CRITTER_CATCH;
			case ENTERED_SAFARI -> SafariEvent.Type.SAFARI_ENTRY;
		};
	}
}
