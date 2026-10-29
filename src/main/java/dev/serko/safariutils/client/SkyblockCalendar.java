package dev.serko.safariutils.client;

/** Fixed SkyBlock calendar, shared by contest scheduling and integrated calendar tooltips. */
public final class SkyblockCalendar {
    public static final long EPOCH = 1_560_275_700_000L;
    public static final long DAY = 1_200_000L;
    public static final long YEAR = DAY * 31 * 12;
    private static final java.util.List<String> MONTHS = java.util.List.of(
        "Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
        "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter");
    private SkyblockCalendar() { }
    public static long yearStart(long now) {
        return EPOCH + Math.floorDiv(now - EPOCH, YEAR) * YEAR;
    }
    public static int month(String name) { return MONTHS.indexOf(name); }
    public static long dateMillis(int year, int month, int day) {
        if (year < 1 || year > 100_000 || month < 0 || month >= 12 || day < 1 || day > 31)
            throw new IllegalArgumentException("Invalid SkyBlock date");
        return EPOCH + (year - 1L) * YEAR + (month * 31L + day - 1) * DAY;
    }
}
