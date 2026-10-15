package dev.serko.safariutils.client;

import dev.serko.safariutils.state.SafariEpoch;
import dev.serko.safariutils.state.SafariRuntimeState;
import dev.serko.safariutils.state.PartySnapshot;
import dev.serko.safariutils.state.PartyState;
import net.minecraft.client.Minecraft;

import dev.serko.safariutils.state.MonotonicCooldowns;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * The single boundary for commands sent automatically by SafariUtils.
 *
 * <p>Feature code still owns its gameplay deadlines and response parsing. This class owns
 * connection validation, shared cooldown classes, lifecycle/generation guards, and concise
 * diagnostics so two independent systems cannot unknowingly occupy the same command window.
 */
public final class CommandCoordinator {
	public enum Category {
		ROSTER,
		TICKET_HOST,
		TICKET_GUEST,
		TICKET_CLEANUP,
		PARTY_CHAT,
		ALERT_CHAT,
		PARTY_SYNC,
		OTHER
	}

	public enum Cooldown {
		NONE(0L),
		CHAT(1_200L),
		PARTY_ACTION(3_100L),
		PARTY_MEMBERSHIP(1_000L),
		PARTY_CLEANUP(1_000L);

		private final long millis;

		Cooldown(long millis) {
			this.millis = millis;
		}
	}

	public enum Scope { GLOBAL, CONNECTION, VISIT, RUN }

	public record Request(String command, Category category, Cooldown cooldown, Scope scope,
			long connection, long visit, long run, long partyGeneration,
			boolean requireLeader, boolean requireParty) {
		public Request {
			if (command == null) command = "";
			if (category == null) category = Category.OTHER;
			if (cooldown == null) cooldown = Cooldown.NONE;
			if (scope == null) scope = Scope.GLOBAL;
		}
	}

	private static final MonotonicCooldowns<Cooldown> cooldowns = new MonotonicCooldowns<>(System::nanoTime);
	private static final Deque<SentCommand> recent = new ArrayDeque<>();
	private static long sequence;
	private record SentCommand(long sequence, long sentAt, Category category, String root) { }

	private CommandCoordinator() { }

	public static Request request(String command, Category category, Cooldown cooldown,
			Scope scope, boolean requireLeader, boolean requireParty) {
		SafariEpoch epoch = SafariRuntimeState.current();
		PartySnapshot party = PartyState.snapshot();
		return new Request(command, category, cooldown, scope, epoch.connection(), epoch.visit(),
			epoch.run(), party.generation(), requireLeader, requireParty);
	}

	/** Sends immediately when every guard and shared cooldown permits it. */
	public static boolean trySend(Request request) {
		if (request == null || request.command().isBlank() || !HypixelConnection.active()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) return false;
		SafariEpoch current = SafariRuntimeState.current();
		if (!scopeMatches(request, current)) {
			diagnostic("expired", request, "scope");
			return false;
		}
		PartySnapshot party = PartyState.snapshot();
		if (request.partyGeneration() >= 0L
				&& request.partyGeneration() != party.generation()) {
			diagnostic("expired", request, "party-generation");
			return false;
		}
		if (request.requireLeader() && (!party.known() || !party.localLeader())) return false;
		if (request.requireParty() && !party.inParty()) return false;
		if (remaining(request.cooldown()) > 0L) {
			return false;
		}
		client.getConnection().sendCommand(stripSlash(request.command()));
		cooldowns.mark(request.cooldown());
		sequence++;
		remember(new SentCommand(sequence, System.nanoTime(), request.category(), root(request.command())));
		diagnostic("sent", request, "#" + sequence);
		return true;
	}

	/** Plain chat shares the same validated network boundary. */
	public static boolean trySendChat(String message, Category category, Scope scope) {
		if (message == null || message.isBlank() || !HypixelConnection.active()) return false;
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) return false;
		SafariEpoch epoch = SafariRuntimeState.current();
		Request guard = new Request(message, category, Cooldown.CHAT, scope, epoch.connection(),
			epoch.visit(), epoch.run(), -1L, false, false);
		if (!scopeMatches(guard, epoch)) return false;
		if (remaining(Cooldown.CHAT) > 0L) return false;
		client.getConnection().sendChat(message);
		cooldowns.mark(Cooldown.CHAT);
		sequence++;
		remember(new SentCommand(sequence, System.nanoTime(), category, "chat"));
		diagnostic("sent-chat", guard, "#" + sequence);
		return true;
	}

	public static long remaining(Cooldown cooldown) {
		if (cooldown == null || cooldown.millis <= 0L) return 0L;
		return cooldowns.remainingMillis(cooldown, cooldown.millis);
	}

	public static void resetConnection() {
		cooldowns.clear();
		recent.clear();
	}

	/** Correlates concise server failures with recent automatic commands for diagnostics. */
	public static void observeServerMessage(String line) {
		if (line == null || line.isBlank() || recent.isEmpty()) return;
		String lower = line.toLowerCase(Locale.ROOT);
		String result = null;
		if (lower.contains("command is on cooldown")) result = "cooldown";
		else if (lower.contains("you are not in a party")
				|| lower.contains("not currently in a party")) result = "not-in-party";
		else if (lower.contains("you are not the party leader")) result = "not-leader";
		else if (lower.contains("couldn't find a player")
				|| lower.contains("could not find a player")
				|| lower.contains("is not online") || lower.contains("is offline")) {
			result = "target-unavailable";
		}
		if (result == null) return;
		long now = System.nanoTime();
		while (!recent.isEmpty() && now - recent.getFirst().sentAt() > 10_000_000_000L) {
			recent.removeFirst();
		}
		SentCommand command = recent.peekLast();
		if (command == null) return;
		StructuredDiagnostics.decision("command", "server-" + result,
			"sequence=" + command.sequence() + ",category=" + command.category()
				+ ",command=" + command.root());
	}

	private static boolean scopeMatches(Request request, SafariEpoch current) {
		return switch (request.scope()) {
			case GLOBAL -> true;
			case CONNECTION -> request.connection() == current.connection();
			case VISIT -> request.connection() == current.connection()
				&& request.visit() > 0L && request.visit() == current.visit();
			case RUN -> request.connection() == current.connection()
				&& request.run() > 0L && request.run() == current.run();
		};
	}

	private static String stripSlash(String value) {
		return value.startsWith("/") ? value.substring(1) : value;
	}

	private static String root(String value) {
		String command = stripSlash(value == null ? "" : value).trim();
		int separator = command.indexOf(' ');
		return (separator < 0 ? command : command.substring(0, separator))
			.toLowerCase(Locale.ROOT);
	}

	private static void remember(SentCommand command) {
		recent.addLast(command);
		while (recent.size() > 8) recent.removeFirst();
	}

	private static void diagnostic(String action, Request request, String detail) {
		String root = root(request.command());
		OperationalLog.debug("COMMAND", action + " category=" + request.category()
			+ " command=" + root + " scope=" + request.scope() + " detail=" + detail);
		StructuredDiagnostics.decision("command", action, "category=" + request.category()
			+ ",command=" + root + ",scope=" + request.scope() + ",detail=" + detail);
	}
}
