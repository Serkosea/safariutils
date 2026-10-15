package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Deterministic, command-free randomized checks for Ticket Trading timing rules. */
final class TicketTradingSimulationAudit {
	private static final int HOST_SCENARIOS = 5_000;
	private static final int GUEST_SCENARIOS = 2_000;
	private static final long STEP_MILLIS = 50L;
	private static final long END_MILLIS = 31_000L;
	private static final long RESPONSE_MILLIS = 500L;

	enum Outcome { JOINS, OFFLINE, NO_RESPONSE }
	private enum CommandType { INVITE, WARP, DISBAND }
	private record SimPlayer(String name, boolean sparklingOnly, boolean qualifies, Outcome outcome) { }
	private record Command(CommandType type, long at, List<String> targets) { }
	record Result(long seed, int hostScenarios, int guestScenarios, int checks,
		List<String> failures) {
		boolean passed() { return failures.isEmpty(); }
	}

	private TicketTradingSimulationAudit() { }

	/** Allows the same audit used by the in-game screen to run during development. */
	public static void main(String[] arguments) {
		long seed = arguments.length == 0 ? System.nanoTime() : Long.parseLong(arguments[0]);
		Result result = run(seed);
		System.out.println((result.passed() ? "PASS" : "FAIL") + " host="
			+ result.hostScenarios() + " guest=" + result.guestScenarios()
			+ " checks=" + result.checks() + " seed=" + result.seed());
		result.failures().forEach(System.out::println);
		if (!result.passed()) throw new AssertionError("Ticket Trading randomized audit failed");
	}

	static Result run(long seed) {
		return run(seed, () -> false);
	}

	static Result run(long seed, java.util.function.BooleanSupplier cancelled) {
		Random random = new Random(seed);
		List<String> failures = new ArrayList<>();
		int checks = auditProtocolIds(failures);
		int hosts = 0, guests = 0;
		for (int scenario = 0; scenario < HOST_SCENARIOS; scenario++) {
			checkCancelled(cancelled);
			checks += runHostScenario(random, scenario, failures);
			hosts++;
			if (failures.size() >= 12) break;
		}
		if (failures.isEmpty()) {
			for (int scenario = 0; scenario < GUEST_SCENARIOS; scenario++) {
				checkCancelled(cancelled);
				checks += runGuestScenario(random, scenario, failures);
				guests++;
				if (failures.size() >= 12) break;
			}
		}
		return new Result(seed, hosts, guests, checks,
			List.copyOf(failures));
	}

	private static void checkCancelled(java.util.function.BooleanSupplier cancelled) {
		if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
			throw new java.util.concurrent.CancellationException("Ticket audit cancelled");
	}

	private static int runHostScenario(Random random, int scenario, List<String> failures) {
		List<SimPlayer> all = players(random, random.nextInt(25));
		int baseline = 1 + random.nextInt(4);
		long detectionAt = random.nextInt(5) == 0 ? -1L : random.nextInt(541) * STEP_MILLIS;
		long catchAt = detectionAt >= 0L && random.nextInt(4) == 0
			? detectionAt + (1 + random.nextInt(200)) * STEP_MILLIS : -1L;
		HostModel model = new HostModel(all, baseline, detectionAt, catchAt);
		model.run();

		int checks = 0;
		Set<String> invited = new HashSet<>();
		long priorCommand = -1L;
		boolean warped = false;
		for (Command command : model.commands) {
			checks++;
			if (priorCommand >= 0L
					&& command.at - priorCommand < TicketTrading.COMMAND_COOLDOWN_MILLIS) {
				fail(failures, scenario, "host command cooldown", model);
			}
			priorCommand = command.at;
			if (command.type == CommandType.INVITE) {
				checks++;
				if (command.at >= TicketTrading.SPARKLING_BATCH_FROM_MILLIS
						&& command.at < TicketTrading.INVITE_AT_MILLIS)
					fail(failures, scenario, "invite during reserved batching gap", model);
				checks += command.targets.size();
				for (String target : command.targets) {
					SimPlayer player = all.stream().filter(value -> value.name.equals(target))
						.findFirst().orElse(null);
					if (player == null || !invited.add(target)) {
						fail(failures, scenario, "unknown or duplicate invite " + target, model);
						continue;
					}
					if (player.sparklingOnly
							&& (detectionAt < 0L || !player.qualifies || command.at < detectionAt
								|| catchAt >= 0L && command.at >= catchAt)) {
						fail(failures, scenario, "unqualified Sparkling invite " + target, model);
					}
					if (!player.sparklingOnly
							&& command.at < TicketTrading.INVITE_AT_MILLIS) {
						fail(failures, scenario, "normal invite before 20s", model);
					}
					if (command.at > TicketTrading.SPARKLING_INVITE_DEADLINE_MILLIS) {
						fail(failures, scenario, "queue invite after safe deadline", model);
					}
				}
			} else if (command.type == CommandType.WARP) {
				checks += 3;
				if (warped || model.joined.isEmpty()
						|| command.at < TicketTrading.WARP_AT_MILLIS
						|| command.at >= TicketTrading.LAST_WARP_AT_MILLIS) {
					fail(failures, scenario, "invalid warp", model);
				}
				warped = true;
			} else {
				checks++;
				if (warped || baseline != 1 || invited.isEmpty() || command.at < TicketTrading.LAST_WARP_AT_MILLIS) {
					fail(failures, scenario, "invalid automatic disband", model);
				}
			}
		}
		checks++;
		if (model.joined.size() + baseline > 4 || baseline == 4 && !model.commands.isEmpty())
			fail(failures, scenario, "baseline capacity exceeded", model);
		checks += model.commands.stream().filter(command -> command.type == CommandType.INVITE).count() * 2;
		failures.addAll(model.violations.stream().map(value -> "host " + scenario + ": " + value).toList());
		return checks;
	}

	private static int runGuestScenario(Random random, int scenario, List<String> failures) {
		boolean enabled = random.nextBoolean(), trusted = random.nextBoolean();
		boolean blocked = random.nextBoolean(), duplicate = random.nextBoolean();
		boolean switching = random.nextBoolean(), inParty = random.nextBoolean();
		var actual = TicketGuestPolicy.invite(enabled, trusted, blocked, duplicate, switching, inParty);
		boolean permitted = enabled && trusted && !blocked && !duplicate;
		if ((actual != TicketGuestPolicy.Action.IGNORE) != permitted
				|| actual == TicketGuestPolicy.Action.LEAVE_THEN_ACCEPT && (!switching || !inParty))
			failures.add("guest " + scenario + ": permission or leave policy mismatch");
		// Settings can be disabled while the leave-settle timer is outstanding.
		if (TicketGuestPolicy.pending(false, trusted, blocked)
				|| TicketGuestPolicy.pending(enabled, false, blocked)
				|| TicketGuestPolicy.pending(enabled, trusted, true))
			failures.add("guest " + scenario + ": revoked pending permission accepted");
		if (TicketTrading.GUEST_LEAVE_SETTLE_MILLIS < 1_000L)
			failures.add("guest " + scenario + ": party leave settled for less than 1s");
		return 5;
	}

	private static int auditProtocolIds(List<String> failures) {
		List<Critter> order = Critters.selectionOrder();
		Set<String> ids = new HashSet<>();
		for (int index = 0; index < order.size(); index++) {
			String id = "%02X".formatted(index);
			if (!ids.add(id) || id.length() != 2 || Critters.selectionMask(order.get(index)) != 1L << index) {
				failures.add("protocol id mismatch for " + order.get(index).name());
			}
		}
		return order.size() * 3;
	}

	private static List<SimPlayer> players(Random random, int count) {
		List<SimPlayer> result = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			boolean sparkling = random.nextBoolean();
			result.add(new SimPlayer("Player" + index, sparkling,
				!sparkling || random.nextBoolean(),
				Outcome.values()[random.nextInt(Outcome.values().length)]));
		}
		return result;
	}

	private static void fail(List<String> failures, int scenario, String reason, HostModel model) {
		if (failures.size() >= 12) return;
		failures.add("host " + scenario + ": " + reason + " [detect=" + model.detectionAt
			+ ", catch=" + model.catchAt
			+ ", commands=" + model.commands + "]");
	}

	private static final class HostModel {
		private final List<SimPlayer> all;
		private final int baseline;
		private final long detectionAt;
		private final long catchAt;
		private final Set<String> invited = new LinkedHashSet<>();
		private final Set<String> joined = new LinkedHashSet<>();
		private final Set<String> failed = new LinkedHashSet<>();
		private final Set<String> handled = new HashSet<>();
		private final Set<String> pending = new LinkedHashSet<>();
		private final List<String> violations = new ArrayList<>();
		private final Map<String, Long> invitedAt = new HashMap<>();
		private final List<Command> commands = new ArrayList<>();
		private long elapsed;
		private long lastCommand = -1L;
		private long nextInviteAt;
		private long responseUntil;
		private boolean warped;

		private HostModel(List<SimPlayer> all, int baseline, long detectionAt, long catchAt) {
			this.all = all;
			this.baseline = baseline;
			this.detectionAt = detectionAt;
			this.catchAt = catchAt;
		}

		private void run() {
			if (baseline >= 4) return;
			for (elapsed = 0L; elapsed <= END_MILLIS && !warped; elapsed += STEP_MILLIS) step();
		}

		private void step() {
			processResponses();
			if (!pending.isEmpty() && elapsed >= responseUntil) {
				failed.addAll(pending);
				pending.clear();
				nextInviteAt = Math.max(nextInviteAt, elapsed);
			}
			boolean early = elapsed < TicketTrading.SPARKLING_BATCH_FROM_MILLIS;
			if (ready() && pending.isEmpty() && elapsed >= nextInviteAt
					&& elapsed <= TicketTrading.SPARKLING_INVITE_DEADLINE_MILLIS
					&& (early || elapsed >= TicketTrading.INVITE_AT_MILLIS)) {
				List<SimPlayer> wave = TicketInvitePolicy.select(all,
					TicketInvitePolicy.capacity(baseline, joined.size()), Set.of(), invited,
					early, SimPlayer::name, SimPlayer::sparklingOnly, this::qualifies);
				if (!wave.isEmpty()) invite(wave);
			}
			if (!joined.isEmpty() && elapsed >= TicketTrading.WARP_AT_MILLIS
					&& elapsed < TicketTrading.LAST_WARP_AT_MILLIS && ready()) {
				command(CommandType.WARP, List.of());
				warped = true;
			} else if (!warped && elapsed >= TicketTrading.LAST_WARP_AT_MILLIS) {
				if (baseline == 1 && !invited.isEmpty() && joined.isEmpty()) command(CommandType.DISBAND, List.of());
				elapsed = END_MILLIS;
			}
		}

		private void processResponses() {
			for (SimPlayer player : all) {
				Long sent = invitedAt.get(player.name);
				if (sent == null || elapsed < sent + RESPONSE_MILLIS || !handled.add(player.name)) continue;
				if (player.outcome == Outcome.JOINS) {
					joined.add(player.name);
					pending.remove(player.name);
				} else if (player.outcome == Outcome.OFFLINE && failed.add(player.name)) {
					pending.remove(player.name);
					nextInviteAt = Math.max(nextInviteAt, elapsed + TicketTrading.INVITE_RETRY_DELAY_MILLIS);
				}
			}
		}

		private boolean qualifies(SimPlayer player) {
			return detectionAt >= 0L && elapsed >= detectionAt && player.qualifies
				&& (catchAt < 0L || elapsed < catchAt);
		}

		private void invite(List<SimPlayer> players) {
			if (players.size() > 4 - baseline - joined.size() || !pending.isEmpty())
				violations.add("wave exceeds available capacity or overlaps pending responses");
			List<String> targets = players.stream().map(SimPlayer::name).toList();
			// Independent oracle: every eligible preceding entry must be selected before a later one.
			List<String> expected = all.stream().filter(player -> !invited.contains(player.name)
				&& (!player.sparklingOnly || qualifies(player))
				&& (elapsed >= TicketTrading.SPARKLING_BATCH_FROM_MILLIS || player.sparklingOnly))
				.limit(Math.max(0, 4 - baseline - joined.size())).map(SimPlayer::name).toList();
			if (!targets.equals(expected)) violations.add("wave differs from eligible queue order");
			command(CommandType.INVITE, targets);
			pending.addAll(targets);
			responseUntil = elapsed + TicketTrading.INVITE_RESPONSE_WINDOW_MILLIS;
			nextInviteAt = elapsed + TicketTrading.INVITE_RETRY_DELAY_MILLIS;
			for (SimPlayer player : players) {
				invited.add(player.name);
				invitedAt.put(player.name, elapsed);
			}
		}

		private void command(CommandType type, List<String> targets) {
			commands.add(new Command(type, elapsed, targets));
			lastCommand = elapsed;
		}

		private boolean ready() {
			return lastCommand < 0L || elapsed - lastCommand >= TicketTrading.COMMAND_COOLDOWN_MILLIS;
		}
	}
}
