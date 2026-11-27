package dev.serko.safariutils.client;

import dev.serko.safariutils.data.Critters;
import dev.serko.safariutils.data.SafariBiome;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.session.SparklingStats;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;

/** Explicit, bounded, local-only lower-bound recovery; never creates synthetic runs. */
public final class SparklingLogImport {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final Pattern DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-\\d+\\.log(?:\\.gz)?$");
    private static final Pattern TIME = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})] ");
    private static final Pattern OWNER = Pattern.compile(".*Setting user: (\\w{1,16})$");
    private static final Pattern SERVER = Pattern.compile(".*Connecting to ([A-Za-z0-9.-]+), \\d+$");
    public record Result(Map<String,Integer> counts, int files, int skipped) { }
    static boolean busy() { return BUSY.get(); }
    public static int start() { return start((String)null); }
    static int start(String directory) { return start(directory, (message, tone) -> { }); }
    static int start(java.util.function.BiConsumer<String, ClientMessages.Tone> feedback) {
        return start(null, feedback);
    }
    private static int start(String directory, java.util.function.BiConsumer<String, ClientMessages.Tone> feedback) {
        java.util.function.BiConsumer<String, ClientMessages.Tone> notify = (message, tone) -> {
            ClientMessages.send(message, tone);
            feedback.accept(message, tone);
        };
        var client = Minecraft.getInstance();
        if (SessionManager.persistenceSuppressed()) {
            notify.accept("Sparkling import is unavailable while stats saving is disabled or on Alpha.", ClientMessages.Tone.WARNING);
            return 0;
        }
        if (!BUSY.compareAndSet(false, true)) {
            notify.accept("A Sparkling import is already running.", ClientMessages.Tone.INFO); return 0;
        }
        var user = client.getUser();
        var connection = client.getConnection();
        Path logs = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs");
        notify.accept("Checking archived logs for confirmed personal Sparkling catches...", ClientMessages.Tone.INFO);
        NetworkRuntime.submit(() -> {
            try { return directory == null ? scan(logs, user.getName()) : scan(List.of(logs, archiveFolder(directory)), user.getName()); }
            catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        }).whenComplete((result, error) -> client.execute(() -> {
            BUSY.set(false);
            if (client.getUser() != user || client.getConnection() != connection || SessionManager.persistenceSuppressed()) {
                notify.accept("Sparkling import cancelled because your session changed; counts were not changed.", ClientMessages.Tone.WARNING);
                return;
            }
            if (error != null) {
                OperationalLog.error("SPARKLING/LOG_IMPORT", error);
                notify.accept(directory == null ? "Sparkling import failed; collection counts were not changed." : "Could not scan both folders. Check the instance path, read access, and archive limits. No counts changed.", ClientMessages.Tone.ERROR); return;
            }
            int added = SparklingStats.mergeLogCounts(result.counts());
            int total = result.counts().values().stream().mapToInt(Integer::intValue).sum();
            String summary;
            if (result.files() == 0) summary = result.skipped() == 0 ? "No archived Minecraft logs found." : "No archives could be read.";
            else if (total == 0) summary = "No personal Main-server Sparkling catches found in the scanned archives.";
            else if (SparklingStats.logImportBlocked()) summary = "Collection protected. No counts changed.";
            else if (added == 0) summary = "All catches found in these logs are already counted.";
            else summary = "Added " + added + (added == 1 ? " catch" : " catches") + " to your collection.";
            feedback.accept(summary, result.skipped() > 0 || SparklingStats.logImportBlocked() ? ClientMessages.Tone.WARNING : ClientMessages.Tone.INFO);
            var lines = new ArrayList<Component>();
            lines.add(Component.literal("Sparkling Import").withColor(0xFFD45A));
            lines.add(Component.literal(summary).withColor(added > 0 ? 0x55FF88 : 0xF2F5FA));
            lines.add(Component.literal("Archives scanned " + result.files() + "  │  Catches found " + total).withColor(0x9DA7B8));
            if (result.skipped() > 0) lines.add(Component.literal("Skipped " + result.skipped() + " unreadable or invalid "
                + (result.skipped() == 1 ? "archive" : "archives") + ".").withColor(0xFFC857));
            for (var biome : SafariBiome.values()) {
                var details = Component.empty();
                int subtotal = 0;
                for (var critter : Critters.all()) {
                    int count = result.counts().getOrDefault(critter.name(), 0);
                    if (critter.biome() != biome || count == 0) continue;
                    if (subtotal > 0) details.append("\n");
                    details.append(Component.literal(critter.name() + " × " + count));
                    subtotal += count;
                }
                if (subtotal > 0) lines.add(Component.literal(biome.displayName() + "  " + subtotal)
                    .withColor(biome.colour()).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(details))));
            }
            if (total > 0) lines.add(Component.literal("Hover a biome for its catches.").withColor(0x9DA7B8));
            ClientMessages.sendReport(lines);
        }));
        return 1;
    }
    public static Result scan(Path logs, String self) throws IOException {
        return scan(List.of(logs), self);
    }
    static Path archiveFolder(String argument) throws IOException {
        String path = argument.strip();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) path = path.substring(1, path.length()-1);
        if (path.isBlank()) throw new IOException("Empty instance path");
        Path root = Path.of(path);
        if (root.getFileName() != null && root.getFileName().toString().equalsIgnoreCase("logs") && Files.isDirectory(root)) return root;
        for (String child : List.of("minecraft/logs", ".minecraft/logs", "logs")) {
            Path candidate = root.resolve(child);
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IOException("No Minecraft logs directory in " + root);
    }
    static Result scan(List<Path> folders, String self) throws IOException {
        Map<String,Integer> counts = new HashMap<>();
        Set<String> events = new HashSet<>();
        int files = 0, skipped = 0;
        List<Path> paths = new ArrayList<>();
        Set<Path> scanned = new HashSet<>();
        for (Path folder : folders) {
            if (!scanned.add(folder.toRealPath())) continue;
            try (var entries = Files.list(folder)) {
                entries.filter(p -> !Files.isSymbolicLink(p) && Files.isRegularFile(p)
                    && DATE.matcher(p.getFileName().toString()).matches()).limit(10001 - paths.size()).forEach(paths::add);
            }
            if (paths.size() > 10000) throw new IOException("Too many log archives");
        }
        paths.sort(Comparator.naturalOrder());
        long budget = 512L * 1024 * 1024;
        for (Path path : paths) {
            Map<String,String> found = new LinkedHashMap<>();
            var name = DATE.matcher(path.getFileName().toString()); name.matches();
            try {
                LocalDate day = LocalDate.parse(name.group(1));
                int previous = -1;
                boolean owner = false, main = false;
                try (InputStream raw = Files.newInputStream(path);
                     InputStream stream = path.toString().endsWith(".gz") ? new GZIPInputStream(raw) : raw;
                     Reader reader = new BufferedReader(new InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder line = new StringBuilder(); int c; long fileChars = 0;
                    while ((c = reader.read()) >= 0) {
                        if (--budget < 0) throw new IOException("Log scan size limit reached");
                        if (++fileChars > 32L * 1024 * 1024 || line.length() > 65536) throw new IOException("Oversized log archive");
                        if (c != '\n') { if (c != '\r') line.append((char)c); continue; }
                        String text = line.toString().replaceAll("§.", ""); line.setLength(0);
                        var time = TIME.matcher(text); if (!time.find()) continue;
                        int seconds = LocalTime.parse(time.group(1)).toSecondOfDay();
                        if (previous - seconds > 43200) day = day.plusDays(1);
                        previous = seconds;
                        var who = OWNER.matcher(text); if (!text.contains("[CHAT]") && who.matches()) { owner = who.group(1).equalsIgnoreCase(self); main = false; }
                        var server = SERVER.matcher(text); if (!text.contains("[CHAT]") && server.matches()) {
                            String host = server.group(1).toLowerCase(Locale.ROOT);
                            main = (host.equals("hypixel.net") || host.endsWith(".hypixel.net")) && !host.equals("alpha.hypixel.net");
                        }
                        int chat = text.indexOf("[CHAT] ");
                        if (!owner || !main || chat < 0) continue;
                        var caught = ChatParser.sparklingCatch(text.substring(chat + 7).trim());
                        if (caught != null && caught.catcher().equalsIgnoreCase(self))
                            found.put(day + "/" + seconds + "/" + caught.critter().name(), caught.critter().name());
                    }
                }
                files++;
                for (var event : found.entrySet()) if (events.add(event.getKey())) counts.merge(event.getValue(), 1, Integer::sum);
            } catch (IOException | RuntimeException unreadable) { skipped++; }
            if (budget < 0) throw new IOException("Log scan size limit reached");
        }
        return new Result(Map.copyOf(counts), files, skipped);
    }
}
