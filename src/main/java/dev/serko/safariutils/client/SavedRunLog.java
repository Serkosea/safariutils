package dev.serko.safariutils.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Bounded, streaming extraction from sealed logs; never duplicates live capture work. */
final class SavedRunLog {
    private static final dev.serko.safariutils.concurrent.BoundedRequests WORKER =
        new dev.serko.safariutils.concurrent.BoundedRequests("SafariUtils-SavedRun", 1, 4);

    static void submit(List<Path> segments, long visit, boolean interrupted) {
        WORKER.submit(() -> {
            try { return export(segments, visit, SafariPaths.savedRunLogs(), interrupted); }
            catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        }).whenComplete((path, failure) -> {
            if (failure != null) OperationalLog.error("SAVED_RUN", failure);
            var client = net.minecraft.client.Minecraft.getInstance();
            client.execute(() -> {
                if (client.player == null || NetworkRuntime.stopping()) return;
                ClientMessages.send(failure == null
                    ? "Safari run log saved: " + path.getFileName() + (interrupted ? " (logging ended early)" : "")
                    : "Could not save Safari run log; the original session logs are unchanged.",
                    failure == null ? ClientMessages.Tone.SUCCESS : ClientMessages.Tone.ERROR);
            });
        });
    }

    /** Pure file operation, also exercised by the headless regression suite. */
    static Path export(List<Path> segments, long visit, Path folder, boolean interrupted) throws IOException {
        Files.createDirectories(folder);
        String stem = "SafariRun_" + java.time.Instant.now().toString().replace(':', '-') + "_V" + visit;
        Path temporary = Files.createTempFile(folder, stem + "_", ".partial");
        Path result = temporary.resolveSibling(temporary.getFileName().toString().replace(".partial", "_events.jsonl.gz"));
        boolean active = false, started = false, ended = false;
        int records = 0;
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(temporary)), StandardCharsets.UTF_8))) {
            outer: for (Path segment : segments) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(segment)), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = DiagnosticReplay.readRecord(in)) != null) {
                        if (line.length() > DiagnosticReplay.MAX_RECORD_CHARACTERS)
                            throw new IOException("Diagnostic record exceeds export limit; original logs retained");
                        JsonObject json;
                        try { json = JsonParser.parseString(line).getAsJsonObject(); }
                        catch (RuntimeException malformed) { throw new IOException("Malformed diagnostic record", malformed); }
                        String kind = json.has("k") ? json.get("k").getAsString() : "";
                        long recordVisit = json.has("v") ? json.get("v").getAsLong() : -1;
                        boolean transition = kind.equals("t");
                        if (!active && recordVisit == visit) {
                            active = true;
                            started = transition && json.has("q") && json.get("q").getAsString().equals("visit-started");
                        }
                        if (active && transition && recordVisit != visit) {
                            // A new visit belongs to the next run, not this export.
                            ended = true;
                            if (recordVisit == 0) { out.write(line); out.newLine(); records++; }
                            break outer;
                        }
                        // Replay input (ci) has no epoch: preserve the complete ordered range.
                        if (active) { out.write(line); out.newLine(); records++; }
                        else if (kind.equals("l") && json.has("g") && json.get("g").getAsString().equals("LIFECYCLE")
                                && json.has("m") && json.get("m").getAsString().startsWith("SafariUtils initialized")) {
                            out.write(line); out.newLine();
                        }
                    }
                }
            }
            if (records == 0) throw new IOException("No records for selected Safari visit");
            JsonObject summary = new JsonObject();
            summary.addProperty("k", "saved-run");
            summary.addProperty("v", visit);
            summary.addProperty("records", records);
            summary.addProperty("completeStart", started);
            summary.addProperty("completeEnd", ended && !interrupted);
            out.write(summary.toString()); out.newLine();
        }
        Files.move(temporary, result);
        return result;
    }
    private SavedRunLog() { }
}

