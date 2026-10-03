package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.client.SafariConfig.SparklingConfig.TicketTraderProfile;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Developer-only, command-free Ticket Trading timing simulator. */
final class TicketTradingTestScreen extends Screen {
	private static final long SIMULATION_STEP_MILLIS = 50L;
	private static final long SIMULATION_END_MILLIS = 31_000L;
	private static final long SIMULATED_RESPONSE_MILLIS = 500L;
	private static final long WARP_COMPLETE_MILLIS = 30_000L;
	private static final float MILLIS_PER_SECOND = 1_000f;
	private enum Outcome {
		JOINS("Joins"), OFFLINE("Offline"), NO_RESPONSE("No Response");
		private final String label;
		Outcome(String label) { this.label = label; }
		private Outcome next() { return values()[(ordinal() + 1) % values().length]; }
	}
	private record Player(String name, boolean sparklingOnly, long critters, boolean backup) { }
	private record Hit(int x, int y, int w, int h, Runnable action) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private static final float[] SPEEDS = {1f, 4f, 10f};
	private static final float[] DETECTION_SECONDS = {0f, 10f, 16f, 18f, 21f, 24f, 25f, 26f};
	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private final List<Player> active = new ArrayList<>();
	private final List<Player> backups = new ArrayList<>();
	private final List<Player> players = new ArrayList<>();
	private final Map<String, Outcome> outcomes = new LinkedHashMap<>();
	private final Set<String> invited = new LinkedHashSet<>();
	private final Set<String> joined = new LinkedHashSet<>();
	private final Set<String> failed = new LinkedHashSet<>();
	private final Set<String> handledResponses = new HashSet<>();
	private final Map<String, Long> invitedAt = new HashMap<>();
	private final List<String> eventLog = new ArrayList<>();
	private List<Critter> critters = List.of();
	private Critter selectedCritter;
	private int speedIndex = 1;
	private int detectionIndex = 3;
	private boolean sparklingScenario;
	private boolean running;
	private boolean detectionApplied;
	private boolean warpSent;
	private boolean completionLogged;
	private int backupReplacementsSent;
	private long realStartedAt;
	private long elapsedMillis;
	private long lastCommandAt = -1L;
	private long backupInviteAt;
	private int left, top, panelWidth, panelHeight, logicalWidth, logicalHeight;
	private float scale = 1f;
	private int background, surface, card, hover, border, primary, secondary, text, muted, green, red, gold;

	private TicketTradingTestScreen(Screen parent) {
		super(Component.literal("Ticket Trading Test"));
		this.parent = parent;
	}

	static void open(Screen parent) {
		if (!BuildVersion.DEVELOPER) return;
		Minecraft.getInstance().execute(() -> {
			TicketTrading.beginTestMode();
			ClientCompat.setScreen(new TicketTradingTestScreen(parent));
		});
	}

	@Override
	protected void init() {
		applyTheme();
		critters = Critters.selectionOrder();
		if (selectedCritter == null && !critters.isEmpty()) selectedCritter = critters.getFirst();
		loadPlayers();
		updateLayout();
	}

	private void loadPlayers() {
		active.clear();
		backups.clear();
		players.clear();
		outcomes.clear();
		SafariConfig.SparklingConfig config = ConfigManager.get().sparkling;
		TicketTradingProfiles.sanitize(config);
		for (int index = 0; index < TicketTradingProfiles.TOTAL_SLOTS; index++) {
			TicketTraderProfile profile = TicketTradingProfiles.slot(config, index);
			if (profile == null) continue;
			Player player = new Player(profile.username, profile.sparklingOnly,
				profile.sparklingCritters & Critters.allSelectionMask(),
				index >= TicketTradingProfiles.ACTIVE_SLOTS);
			(player.backup ? backups : active).add(player);
			players.add(player);
			outcomes.put(player.name, Outcome.JOINS);
		}
	}

	private void updateLayout() {
		int preferredWidth = 700;
		int preferredHeight = 430;
		scale = ResponsiveUI.fitScale(width, height, preferredWidth, preferredHeight, 8);
		logicalWidth = ResponsiveUI.logicalWidth(width, scale);
		logicalHeight = ResponsiveUI.logicalHeight(height, scale);
		panelWidth = Math.min(preferredWidth, logicalWidth - 16);
		panelHeight = Math.min(preferredHeight, logicalHeight - 16);
		left = (logicalWidth - panelWidth) / 2;
		top = (logicalHeight - panelHeight) / 2;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick) {
		applyTheme();
		advanceSimulation();
		if (parent != null) parent.extractRenderState(graphics, Integer.MIN_VALUE,
			Integer.MIN_VALUE, partialTick);
		else graphics.fill(0, 0, width, height, background);
		graphics.fill(0, 0, width, height, 0xA0000000);
		int mx = Math.round(mouseX / scale);
		int my = Math.round(mouseY / scale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		graphics.fill(left, top, left + panelWidth, top + panelHeight, surface);
		SpecialTheme.stars(graphics, left + 2, top + 2, panelWidth - 4, panelHeight - 4, 0.7f);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, left, top, panelWidth, panelHeight, 1);
		else UIDraw.outline(graphics, left, top, panelWidth, panelHeight, border);
		hits.clear();
		centered(graphics, "Ticket Trading Test", top + 14, text);
		centered(graphics, "SIMULATION — NO COMMANDS SENT", top + 30, red);
		drawControls(graphics, mx, my);
		drawPlayers(graphics, mx, my);
		drawLog(graphics);
		button(graphics, left + panelWidth / 2 - 50, top + panelHeight - 32,
			100, 22, "Back", mx, my, this::onClose);
		graphics.pose().popMatrix();
	}

	private void drawControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int y = top + 50;
		button(graphics, left + 16, y, 108, 22,
			sparklingScenario ? "Sparkling Run" : "Normal Run", mouseX, mouseY,
			() -> { sparklingScenario = !sparklingScenario; resetSimulation(); });
		button(graphics, left + 132, y, 150, 22,
			sparklingScenario && selectedCritter != null ? selectedCritter.name() : "No Sparkling",
			mouseX, mouseY, this::nextCritter);
		button(graphics, left + 290, y, 104, 22,
			"Detect: " + decimal(DETECTION_SECONDS[detectionIndex]) + "s",
			mouseX, mouseY, () -> {
				detectionIndex = (detectionIndex + 1) % DETECTION_SECONDS.length;
				resetSimulation();
			});
		button(graphics, left + 402, y, 76, 22, "Speed: " + decimal(SPEEDS[speedIndex]) + "x",
			mouseX, mouseY, () -> speedIndex = (speedIndex + 1) % SPEEDS.length);
		button(graphics, left + panelWidth - 112, y, 96, 22,
			running ? "Restart" : "Start Host", mouseX, mouseY, this::startHost);

		int guestY = y + 30;
		button(graphics, left + 16, guestY, 112, 20, "Guest: Solo", mouseX, mouseY,
			() -> guestTest(false));
		button(graphics, left + 136, guestY, 126, 20, "Guest: In Party", mouseX, mouseY,
			() -> guestTest(true));
		button(graphics, left + 270, guestY, 92, 20, "Host Exit", mouseX, mouseY,
			this::hostExit);
		String state = running ? "Time " + decimal(elapsedMillis / 1000f) + "s"
			: warpSent ? "Warped — waiting for Host Exit" : "Ready";
		draw(graphics, state, left + panelWidth - font.width(state) - 16, guestY + 6,
			running ? gold : muted);
	}

	private void drawPlayers(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left + 16;
		int y = top + 110;
		int w = (panelWidth - 48) / 2;
		int h = panelHeight - 154;
		panel(graphics, x, y, w, h, "Simulated Players — click to change response");
		if (players.isEmpty()) {
			centeredWithin(graphics, "Configure Ticket Trading profiles first", x, y + h / 2, w, muted);
			return;
		}
		int rowY = y + 28;
		for (Player player : players) {
			boolean over = inside(mouseX, mouseY, x + 8, rowY, w - 16, 30);
			graphics.fill(x + 8, rowY, x + w - 8, rowY + 30, over ? hover : card);
			UIDraw.outline(graphics, x + 8, rowY, w - 16, 30,
				player.backup ? secondary : border);
			String name = (player.backup ? "Backup  " : "Active  ") + player.name;
			draw(graphics, name, x + 16, rowY + 6, player.sparklingOnly ? primary : text);
			String detail = player.sparklingOnly ? "Sparkling • " : "All runs • ";
			Outcome outcome = outcomes.getOrDefault(player.name, Outcome.JOINS);
			draw(graphics, detail + outcome.label, x + 16, rowY + 18, outcomeColour(outcome));
			hits.add(new Hit(x + 8, rowY, w - 16, 30,
				() -> outcomes.put(player.name, outcome.next())));
			rowY += 36;
		}
	}

	private void drawLog(GuiGraphicsExtractor graphics) {
		int x = left + 32 + (panelWidth - 48) / 2;
		int y = top + 110;
		int w = panelWidth - (x - left) - 16;
		int h = panelHeight - 154;
		panel(graphics, x, y, w, h, "Command Timeline");
		int visible = Math.max(1, (h - 36) / 13);
		int first = Math.max(0, eventLog.size() - visible);
		int lineY = y + 28;
		for (int index = first; index < eventLog.size(); index++) {
			draw(graphics, trim(eventLog.get(index), w - 24), x + 12, lineY, muted);
			lineY += 13;
		}
		if (eventLog.isEmpty()) centeredWithin(graphics,
			"Choose a scenario and start the simulation", x, y + h / 2, w, muted);
	}

	private void startHost() {
		resetSimulation();
		running = true;
		realStartedAt = System.currentTimeMillis();
		logAt(0L, "Host authority confirmed; ticket used");
	}

	private void guestTest(boolean inParty) {
		resetSimulation();
		String inviter = active.isEmpty() ? "TrustedPlayer" : active.getFirst().name;
		if (inParty) {
			logAt(0L, "/party leave");
			logAt(TicketTrading.GUEST_LEAVE_SETTLE_MILLIS, "/party accept " + inviter);
			logAt(TicketTrading.GUEST_LEAVE_SETTLE_MILLIS * 2L,
				"automatic /party list refresh allowed");
		} else logAt(0L, "/party accept " + inviter);
	}

	private void hostExit() {
		if (!warpSent) {
			logAt(elapsedMillis, "Host Exit ignored: no successful warp");
			return;
		}
		logAt(elapsedMillis, "/party disband");
		warpSent = false;
		running = false;
	}

	private void resetSimulation() {
		running = false;
		detectionApplied = false;
		warpSent = false;
		completionLogged = false;
		backupReplacementsSent = 0;
		realStartedAt = 0L;
		elapsedMillis = 0L;
		lastCommandAt = -1L;
		backupInviteAt = 0L;
		invited.clear();
		joined.clear();
		failed.clear();
		handledResponses.clear();
		invitedAt.clear();
		eventLog.clear();
	}

	private void advanceSimulation() {
		if (!running) return;
		long target = Math.min(SIMULATION_END_MILLIS, Math.round(
			(System.currentTimeMillis() - realStartedAt) * SPEEDS[speedIndex]));
		while (elapsedMillis < target) {
			elapsedMillis = Math.min(target, elapsedMillis + SIMULATION_STEP_MILLIS);
			step();
		}
	}

	private void step() {
		if (sparklingScenario && !detectionApplied
				&& elapsedMillis >= Math.round(DETECTION_SECONDS[detectionIndex]
					* MILLIS_PER_SECOND)) {
			detectionApplied = true;
			logAt(elapsedMillis, "Detected Sparkling " + selectedCritter.name());
		}
		processResponses();
		boolean ready = commandReady();
		if (!warpSent && ready && elapsedMillis <= TicketTrading.SPARKLING_INVITE_DEADLINE_MILLIS) {
			List<Player> pending = elapsedMillis < TicketTrading.SPARKLING_BATCH_FROM_MILLIS
				? pendingSparkling() : elapsedMillis >= TicketTrading.INVITE_AT_MILLIS
					? pendingPrimary() : List.of();
			if (!pending.isEmpty()) invite(pending, true);
		}
		int replacements = failed.size() - backupReplacementsSent;
		if (!warpSent && replacements > 0 && backupInviteAt > 0L
				&& elapsedMillis >= TicketTrading.INVITE_AT_MILLIS
				&& elapsedMillis >= backupInviteAt
				&& elapsedMillis <= TicketTrading.BACKUP_INVITE_DEADLINE_MILLIS
				&& commandReady()) {
			List<Player> pending = pendingBackups(replacements);
			if (!pending.isEmpty()) {
				invite(pending, false);
				backupReplacementsSent += pending.size();
			}
			backupInviteAt = 0L;
		}
		if (!warpSent && !joined.isEmpty()
				&& elapsedMillis >= TicketTrading.WARP_AT_MILLIS
				&& elapsedMillis < TicketTrading.LAST_WARP_AT_MILLIS && commandReady()) {
			command("party warp");
			warpSent = true;
		}
		if (!warpSent && elapsedMillis >= TicketTrading.LAST_WARP_AT_MILLIS
				&& !completionLogged) {
			if (!invited.isEmpty()) command("party disband");
			logAt(elapsedMillis, "Invite window closed without a warp");
			completionLogged = true;
			running = false;
		}
		if (warpSent && elapsedMillis >= WARP_COMPLETE_MILLIS && !completionLogged) {
			logAt(elapsedMillis, "Warp complete; party remains until Host Exit");
			completionLogged = true;
			running = false;
		}
	}

	private void processResponses() {
		for (Player player : players) {
			Long sentAt = invitedAt.get(player.name);
			if (sentAt == null || elapsedMillis < sentAt + SIMULATED_RESPONSE_MILLIS
					|| !handledResponses.add(player.name)) continue;
			Outcome outcome = outcomes.getOrDefault(player.name, Outcome.JOINS);
			if (outcome == Outcome.JOINS) {
				joined.add(player.name);
				logAt(elapsedMillis, player.name + " joined the party");
			} else if (outcome == Outcome.OFFLINE) {
				logAt(elapsedMillis, player.name + " is offline");
				if (!player.backup && failed.add(player.name) && backupInviteAt == 0L) {
					backupInviteAt = elapsedMillis + TicketTrading.BACKUP_INVITE_DELAY_MILLIS;
				}
			} else logAt(elapsedMillis, player.name + " did not respond");
		}
	}

	private List<Player> pendingPrimary() {
		List<Player> result = new ArrayList<>();
		for (Player player : active) {
			if (!invited.contains(player.name)
					&& (!player.sparklingOnly || qualifies(player))) result.add(player);
		}
		return result;
	}

	private List<Player> pendingSparkling() {
		if (!detectionApplied) return List.of();
		return active.stream().filter(player -> player.sparklingOnly
			&& qualifies(player) && !invited.contains(player.name)).toList();
	}

	private List<Player> pendingBackups(int limit) {
		List<Player> result = new ArrayList<>();
		for (Player player : backups) {
			if (result.size() >= limit) break;
			if (invited.contains(player.name) || player.sparklingOnly && !qualifies(player)) continue;
			result.add(player);
		}
		return result;
	}

	private boolean qualifies(Player player) {
		return detectionApplied && selectedCritter != null
			&& (player.critters & Critters.selectionMask(selectedCritter)) != 0L;
	}

	private void invite(List<Player> players, boolean primary) {
		command("party invite " + String.join(" ", players.stream().map(Player::name).toList()));
		for (Player player : players) {
			invited.add(player.name);
			invitedAt.put(player.name, elapsedMillis);
		}
		if (!primary) logAt(elapsedMillis, "Backup replacements invited");
	}

	private void command(String command) {
		logAt(elapsedMillis, "/" + command);
		lastCommandAt = elapsedMillis;
	}

	private boolean commandReady() {
		return lastCommandAt < 0L
			|| elapsedMillis - lastCommandAt >= TicketTrading.COMMAND_COOLDOWN_MILLIS;
	}

	private void nextCritter() {
		if (!sparklingScenario) {
			sparklingScenario = true;
			resetSimulation();
			return;
		}
		int index = Math.max(0, critters.indexOf(selectedCritter));
		selectedCritter = critters.get((index + 1) % critters.size());
		resetSimulation();
	}

	private void logAt(long millis, String message) {
		eventLog.add(String.format("%05.1fs  %s", millis / MILLIS_PER_SECOND, message));
	}

	private void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String title) {
		graphics.fill(x, y, x + w, y + h, 0x66000000);
		UIDraw.outline(graphics, x, y, w, h, border);
		draw(graphics, title, x + 9, y + 9, secondary);
	}

	private void button(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			String value, int mouseX, int mouseY, Runnable action) {
		boolean over = inside(mouseX, mouseY, x, y, w, h);
		graphics.fill(x, y, x + w, y + h, over ? hover : card);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, x, y, w, h, 1);
		else UIDraw.outline(graphics, x, y, w, h, border);
		centeredWithin(graphics, value, x, y + (h - 8) / 2, w, text);
		hits.add(new Hit(x, y, w, h, action));
	}

	private int outcomeColour(Outcome outcome) {
		return switch (outcome) {
			case JOINS -> green;
			case OFFLINE -> red;
			case NO_RESPONSE -> gold;
		};
	}

	private static String decimal(float value) {
		return value == Math.round(value) ? Integer.toString(Math.round(value))
			: String.format("%.1f", value);
	}

	private String trim(String value, int maxWidth) {
		if (font.width(value) <= maxWidth) return value;
		String suffix = "…";
		int end = value.length();
		while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
		return value.substring(0, end) + suffix;
	}

	private void centered(GuiGraphicsExtractor graphics, String value, int y, int colour) {
		centeredWithin(graphics, value, left, y, panelWidth, colour);
	}

	private void centeredWithin(GuiGraphicsExtractor graphics, String value,
			int x, int y, int w, int colour) {
		draw(graphics, value, x + (w - font.width(value)) / 2, y, colour);
	}

	private void draw(GuiGraphicsExtractor graphics, String value, int x, int y, int colour) {
		SpecialTheme.text(graphics, font, Component.literal(value), x, y, colour);
	}

	private void applyTheme() {
		int[] palette = SafariSettingsScreen.activeThemePalette();
		background = palette[0]; surface = palette[1]; card = palette[2]; hover = palette[3];
		border = palette[4]; primary = palette[5]; secondary = palette[6]; green = palette[7];
		red = palette[8]; gold = palette[9]; text = palette[10]; muted = palette[11];
		if (SpecialTheme.rainbow()) {
			primary = SpecialTheme.accent(0); secondary = SpecialTheme.accent(18);
			green = SpecialTheme.accent(36); red = SpecialTheme.accent(54);
			gold = SpecialTheme.accent(72); border = SpecialTheme.accent(90);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		double mouseX = event.x() / scale;
		double mouseY = event.y() / scale;
		for (int index = hits.size() - 1; index >= 0; index--) {
			Hit hit = hits.get(index);
			if (!hit.contains(mouseX, mouseY)) continue;
			hit.action.run();
			return true;
		}
		return false;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == 256) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		TicketTrading.endTestMode();
		ClientCompat.setScreen(parent);
	}

	@Override
	public void removed() {
		TicketTrading.endTestMode();
		super.removed();
	}

	@Override public boolean isPauseScreen() { return false; }

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}
