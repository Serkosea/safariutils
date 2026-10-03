package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Responsive details for the launch-time GitHub update check. */
final class UpdateDetailsScreen extends Screen {
	private static final DateTimeFormatter RELEASE_DATE_FORMAT =
		DateTimeFormatter.ofPattern("MMM d, yyyy");

	private record Hit(int x, int y, int w, int h, Runnable action) {
		private boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		}
	}

	private final Screen parent;
	private final List<Hit> hits = new ArrayList<>();
	private int notesScroll;
	private String cachedNotesSource = "";
	private int cachedNotesWidth = -1;
	private List<String> cachedNotes = List.of();
	private boolean draggingScrollbar;
	private int scrollbarX, scrollbarY, scrollbarHeight;
	private int scrollbarThumbY, scrollbarThumbHeight;
	private int visibleNotes = 1;
	private int maximumNotesScroll;
	private int left, top, panelWidth, panelHeight, logicalWidth, logicalHeight;
	private float scale = 1f;
	private int background, surface, card, hover, border, primary, secondary;
	private int green, red, gold, text, muted;

	private UpdateDetailsScreen(Screen parent) {
		super(Component.literal("SafariUtils Update Details"));
		this.parent = parent;
	}

	static void open() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> ClientCompat.setScreen(new UpdateDetailsScreen(ClientCompat.screen())));
	}

	@Override
	protected void init() {
		applyTheme();
		int preferredWidth = 640;
		int preferredHeight = 350;
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
		if (parent != null) parent.extractRenderState(graphics, Integer.MIN_VALUE,
			Integer.MIN_VALUE, partialTick);
		else graphics.fill(0, 0, width, height, background);
		graphics.fill(0, 0, width, height, 0xA0000000);
		int mx = Math.round(mouseX / scale);
		int my = Math.round(mouseY / scale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		graphics.fill(left, top, left + panelWidth, top + panelHeight, surface);
		SpecialTheme.stars(graphics, left + 2, top + 2, panelWidth - 4, panelHeight - 4, 0.55f);
		if (SpecialTheme.rainbow()) SpecialTheme.border(graphics, left, top, panelWidth, panelHeight, 1);
		else UIDraw.outline(graphics, left, top, panelWidth, panelHeight, border);
		hits.clear();
		centered(graphics, "Update Details", top + 14, text);
		drawDetails(graphics, mx, my);
		button(graphics, left + panelWidth / 2 - 50, top + panelHeight - 31,
			100, 21, "Done", mx, my, this::onClose);
		graphics.pose().popMatrix();
	}

	private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = left + 20;
		int y = top + 40;
		int w = panelWidth - 40;
		int versionHeight = 70;
		panel(graphics, x, y, w, versionHeight, "Version Status");
		draw(graphics, "Installed", x + 12, y + 30, muted);
		draw(graphics, UpdateChecker.currentReleaseVersion(), x + 112, y + 30, text);
		draw(graphics, "Latest release", x + 12, y + 47, muted);
		String latest = UpdateChecker.latestVersion().isBlank() ? statusLabel() : UpdateChecker.latestVersion();
		draw(graphics, latest, x + 112, y + 47, updateColour());
		if (!UpdateChecker.publishedAt().isBlank()) {
			drawRight(graphics, formatDate(UpdateChecker.publishedAt()), x + w - 12, y + 47, muted);
		}
		button(graphics, x + w - 132, y + 12, 120, 22,
			UpdateChecker.releaseLinkLabel(), mouseX, mouseY,
			() -> Util.getPlatform().openUri(UpdateChecker.releasePage()));

		int notesY = y + versionHeight + 8;
		int doneY = top + panelHeight - 31;
		int notesHeight = doneY - 8 - notesY;
		panel(graphics, x, notesY, w, notesHeight, "Release Notes");
		int notesW = w - 42;
		List<String> lines = wrappedReleaseNotes(notesW);
		visibleNotes = Math.max(1, (notesHeight - 38) / 14);
		maximumNotesScroll = Math.max(0, lines.size() - visibleNotes);
		notesScroll = Math.min(notesScroll, maximumNotesScroll);
		int first = notesScroll;
		for (int index = first; index < Math.min(lines.size(), first + visibleNotes); index++) {
			draw(graphics, lines.get(index), x + 12,
				notesY + 29 + (index - first) * 14, muted);
		}
		drawScrollbar(graphics, mouseX, mouseY, x + w - 11, notesY + 27,
			notesHeight - 37, lines.size());
	}

	private void drawScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			int x, int y, int height, int totalLines) {
		scrollbarX = x;
		scrollbarY = y;
		scrollbarHeight = Math.max(1, height);
		graphics.fill(x, y, x + 6, y + scrollbarHeight, card);
		UIDraw.outline(graphics, x, y, 6, scrollbarHeight, border);
		if (maximumNotesScroll <= 0) {
			scrollbarThumbY = y;
			scrollbarThumbHeight = scrollbarHeight;
		} else {
			scrollbarThumbHeight = Math.max(18,
				Math.round(scrollbarHeight * (visibleNotes / (float) Math.max(1, totalLines))));
			int travel = scrollbarHeight - scrollbarThumbHeight;
			scrollbarThumbY = y + Math.round(travel * (notesScroll / (float) maximumNotesScroll));
		}
		boolean hovered = inside(mouseX, mouseY, x - 2, y, 8, scrollbarHeight);
		int thumbColour = hovered || draggingScrollbar ? secondary : primary;
		graphics.fill(x + 1, scrollbarThumbY + 1, x + 5,
			scrollbarThumbY + scrollbarThumbHeight - 1, thumbColour);
	}

	private List<String> wrappedReleaseNotes(int width) {
		String notes = UpdateChecker.releaseNotes();
		String source = UpdateChecker.status() + "\n" + UpdateChecker.releaseTitle() + "\n" + notes;
		if (width == cachedNotesWidth && source.equals(cachedNotesSource)) return cachedNotes;
		List<String> result = new ArrayList<>();
		if (notes.isBlank()) {
			result.add(switch (UpdateChecker.status()) {
				case CHECKING -> "Release details are still loading.";
				case UNAVAILABLE -> "GitHub could not be reached during this launch.";
				default -> "No release notes were provided for this release.";
			});
		} else for (String raw : notes.replace("\r", "").split("\n")) {
			String heading = raw.strip().replaceFirst("^#{1,6}\\s*", "").strip();
			if (raw.strip().matches("^#{1,6}\\s+.*")
					&& heading.equalsIgnoreCase("Downloads")) break;
			String line = raw.strip().replaceFirst("^#{1,6}\\s*", "")
				.replace("**", "").replace("`", "");
			if (line.isBlank()) {
				if (!result.isEmpty() && !result.getLast().isBlank()) result.add("");
			} else wrapLine(result, line, width);
		}
		cachedNotesWidth = width;
		cachedNotesSource = source;
		cachedNotes = List.copyOf(result);
		return cachedNotes;
	}

	private void wrapLine(List<String> output, String line, int width) {
		String prefix = line.startsWith("- ") ? "• " : "";
		if (!prefix.isEmpty()) line = line.substring(2);
		StringBuilder current = new StringBuilder(prefix);
		for (String word : line.split("\\s+")) {
			String candidate = current.length() == prefix.length() ? current + word : current + " " + word;
			if (font.width(candidate) <= width || current.length() == prefix.length()) {
				current.setLength(0);
				current.append(candidate);
			} else {
				output.add(current.toString());
				current.setLength(0);
				current.append(prefix.isEmpty() ? "" : "  ").append(word);
			}
		}
		if (!current.isEmpty()) output.add(current.toString());
	}

	private String statusLabel() {
		return switch (UpdateChecker.status()) {
			case NOT_STARTED, CHECKING -> "Checking…";
			case UNAVAILABLE -> "Unavailable";
			case CURRENT -> "Current";
			case AVAILABLE -> UpdateChecker.availableVersion();
		};
	}

	private int updateColour() {
		return switch (UpdateChecker.status()) {
			case AVAILABLE -> gold;
			case CURRENT -> green;
			case UNAVAILABLE -> red;
			default -> muted;
		};
	}

	private static String formatDate(String value) {
		try {
			return OffsetDateTime.parse(value).format(RELEASE_DATE_FORMAT);
		} catch (RuntimeException invalid) {
			return "";
		}
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

	private void centered(GuiGraphicsExtractor graphics, String value, int y, int colour) {
		centeredWithin(graphics, value, left, y, panelWidth, colour);
	}

	private void centeredWithin(GuiGraphicsExtractor graphics, String value,
			int x, int y, int w, int colour) {
		draw(graphics, value, x + (w - font.width(value)) / 2, y, colour);
	}

	private void drawRight(GuiGraphicsExtractor graphics, String value, int right, int y, int colour) {
		draw(graphics, value, right - font.width(value), y, colour);
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
		if (maximumNotesScroll > 0
				&& inside(mouseX, mouseY, scrollbarX - 2, scrollbarY, 8, scrollbarHeight)) {
			draggingScrollbar = true;
			updateScrollbar(mouseY);
			return true;
		}
		for (int index = hits.size() - 1; index >= 0; index--) {
			Hit hit = hits.get(index);
			if (!hit.contains(mouseX, mouseY)) continue;
			hit.action.run();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		List<String> lines = wrappedReleaseNotes(panelWidth - 82);
		int direction = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
		if (direction == 0) return false;
		notesScroll = Math.clamp(notesScroll + direction * 3, 0,
			Math.max(0, lines.size() - visibleNotes));
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (!draggingScrollbar) return super.mouseDragged(event, dragX, dragY);
		updateScrollbar(event.y() / scale);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (!draggingScrollbar) return super.mouseReleased(event);
		draggingScrollbar = false;
		return true;
	}

	private void updateScrollbar(double mouseY) {
		if (maximumNotesScroll <= 0) return;
		int travel = Math.max(1, scrollbarHeight - scrollbarThumbHeight);
		double thumbTop = mouseY - scrollbarThumbHeight / 2.0;
		double fraction = Math.clamp((thumbTop - scrollbarY) / travel, 0.0, 1.0);
		notesScroll = Math.clamp((int) Math.round(fraction * maximumNotesScroll),
			0, maximumNotesScroll);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == 256) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override public void onClose() { ClientCompat.setScreen(parent); }
	@Override public boolean isPauseScreen() { return false; }

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}
