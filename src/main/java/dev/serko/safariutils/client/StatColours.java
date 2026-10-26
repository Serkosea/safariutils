package dev.serko.safariutils.client;

/** Shared semantic colors for statistical labels and values; biome/rank/status colors remain distinct. */
public final class StatColours {
    public static final int DATE = 0xFF9FADFF;
    public static final int TIME = 0xFF75D9F1;
    public static final int COINS = 0xFFFFDA70;
    public static final int CATCHES = 0xFF80E5A0;
    public static final int PERSONAL_BEST = 0xFFB58AFF;
    public static final int INFO = 0xFF9CCBFF;
    public static final int SECONDARY = 0xFFC49DE8;
    public static final int MISSING = 0xFF5F594E;
    public static final int UNIQUE = 0xFF66DECE;
    public static final int DUPLICATES = 0xFFF198D0;
    public static final int FEATHERS = 0xFFD5A0FF;
    public static final int ESSENCE = 0xFF89D7FF;
    public static final int DYE = 0xFF65DF96;

    /** For the small, explicitly formatted summary lines in stats/collection screens. */
    public static int summary(String segment) {
        String value=segment.toLowerCase(java.util.Locale.ROOT);
        if(value.contains("emerald")) return DYE;
        if(value.contains("feather")) return FEATHERS;
        if(value.contains("duplicate")) return DUPLICATES;
        if(value.contains("unique")) return UNIQUE;
        if(value.contains("sparkling")) return PERSONAL_BEST;
        if(value.contains("coin") || value.contains("profit") || value.contains("per run") && !value.contains("catch")) return COINS;
        if(value.contains("essence")) return ESSENCE;
        if(value.contains("shard")) return UNIQUE;
        if(value.contains("played") || value.contains("length") || value.contains("attempt")) return TIME;
        if(value.contains("catch") || value.contains("caught")) return CATCHES;
        return INFO;
    }

    /** Related, softer value tint; leaves status and user-selected colors independent. */
    public static int value(int heading) {
        int r=heading>>>16&255,g=heading>>>8&255,b=heading&255;
        int softness=heading==COINS?5:3;
        return 0xFF000000 | (r+(255-r)/softness)<<16 | (g+(255-g)/softness)<<8 | b+(255-b)/softness;
    }

    private record LineKey(String text,String separator,boolean collection) { }
    private static final int CACHE_LIMIT=128;
    private static final java.util.Map<LineKey,net.minecraft.network.chat.Component> LINES=new java.util.LinkedHashMap<>();

    /** Client-render-thread cache stores formatting only, never font widths or theme-dependent output. */
    static net.minecraft.network.chat.Component formatted(String line,String separator) {
        return formatted(line,separator,false);
    }

    static net.minecraft.network.chat.Component collection(String line,String separator) {
        return formatted(line,separator,true);
    }

    private static net.minecraft.network.chat.Component formatted(String line,String separator,boolean collection) {
        if(separator.isEmpty()) throw new IllegalArgumentException("Empty summary separator");
        LineKey key=new LineKey(line,separator,collection);
        var cached=LINES.get(key);
        if(cached!=null) return cached;
        var result=net.minecraft.network.chat.Component.empty();
        int start=0;
        while(start<=line.length()) {
            int end=line.indexOf(separator,start);
            if(end<0) end=line.length();
            String segment=line.substring(start,end);
            int colour=summary(segment);
            int boundary=collection?segment.indexOf("  "):-1;
            if(boundary>=0) {
                result.append(net.minecraft.network.chat.Component.literal(segment.substring(0,boundary)).withColor(colour));
                result.append(net.minecraft.network.chat.Component.literal(segment.substring(boundary)).withColor(value(colour)));
            } else {
                // Run summaries keep number, unit and label together; collection stats have explicit label/value pairs.
                result.append(net.minecraft.network.chat.Component.literal(segment).withColor(colour));
            }
            if(end==line.length()) break;
            result.append(net.minecraft.network.chat.Component.literal(separator).withColor(SECONDARY));
            start=end+separator.length();
        }
        if(LINES.size()>=CACHE_LIMIT) LINES.remove(LINES.keySet().iterator().next());
        LINES.put(key,result);
        return result;
    }

    public static void summaryLine(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
            net.minecraft.client.gui.Font font, String line, String separator, int x, int y) {
        SpecialTheme.text(graphics,font,formatted(line,separator),x,y,INFO);
    }

    private StatColours() { }
}
