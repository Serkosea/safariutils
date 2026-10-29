package dev.serko.safariutils.client;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.*;
import java.io.*;

public final class SavedRunLogRegression {
    public static int run() throws Exception {
        Path root = Files.createTempDirectory("safari-saved-run-test");
        try {
            Path first = segment(root, "one.gz", """
                {"k":"l","v":0,"g":"LIFECYCLE","m":"SafariUtils initialized test"}
                {"k":"t","v":1,"q":"visit-started"}
                {"k":"ci","op":"prior-run"}
                {"k":"t","v":0,"q":"visit-ended"}
                {"k":"t","v":2,"q":"visit-started"}
                {"k":"ci","op":"reset"}
                {"k":"l","v":2,"m":"before save command"}
                """);
            Path second = segment(root, "two.gz", """
                {"k":"ci","op":"catch"}
                {"k":"t","v":2,"q":"run-closed"}
                {"k":"l","v":2,"m":"after run timer"}
                {"k":"t","v":0,"q":"visit-ended"}
                {"k":"ci","op":"outside"}
                {"k":"t","v":3,"q":"visit-started"}
                {"k":"ci","op":"next-run"}
                """);
            String result = read(SavedRunLog.export(List.of(first, second), 2, root.resolve("saved"), false));
            require(result.lines().noneMatch(String::isBlank), "export duplicated Windows line separators");
            require(result.contains("before save command"), "lost retrospective evidence");
            require(result.contains("\"op\":\"reset\"") && result.contains("\"op\":\"catch\""), "lost contextless replay input");
            require(result.contains("after run timer"), "ended before leaving Safari");
            require(!result.contains("prior-run") && !result.contains("outside") && !result.contains("next-run"), "included another visit");
            require(result.contains("\"completeStart\":true") && result.contains("\"completeEnd\":true"), "complete range flagged incomplete");
            require(result.contains("SafariUtils initialized test"), "missing build provenance");
            String partial = read(SavedRunLog.export(List.of(first), 2, root.resolve("saved"), true));
            require(partial.contains("\"completeEnd\":false"), "interruption not reported");
            Path late = segment(root, "late.gz", "{\"k\":\"l\",\"v\":2,\"m\":\"late logging\"}\n");
            require(read(SavedRunLog.export(List.of(late), 2, root.resolve("saved"), true)).contains("\"completeStart\":false"),
                "late logging not reported");
            boolean rejected = false;
            try { SavedRunLog.export(List.of(first), 99, root.resolve("saved"), false); }
            catch (IOException expected) { rejected = true; }
            require(rejected, "absent visit silently exported");
            return 10;
        } finally {
            try (var files = Files.walk(root)) {
                for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static Path segment(Path root, String name, String content) throws IOException {
        Path path = root.resolve(name);
        try (var out = new GZIPOutputStream(Files.newOutputStream(path))) { out.write(content.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8)); }
        return path;
    }
    private static String read(Path path) throws IOException {
        try (var in = new GZIPInputStream(Files.newInputStream(path))) { return new String(in.readAllBytes(), StandardCharsets.UTF_8); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private SavedRunLogRegression() { }
}

