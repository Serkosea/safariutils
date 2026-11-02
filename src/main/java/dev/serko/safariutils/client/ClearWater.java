package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;

/** Tick-owned render flags; chunk workers read only immutable primitive state. */
public final class ClearWater {
    private static volatile boolean active;
    private static volatile int opacity=60;
    private static boolean compiledActive;
    private static int compiledOpacity=60;
    private static Object level;
    private static long changeAt;
    public static boolean active() { return active; }
    public static int surface(int colour,boolean water) {
        return active&&water?(Math.min(colour>>>24,(opacity*255+50)/100)<<24)|(colour&0xFFFFFF):colour;
    }
    public static void tick() {
        var client=Minecraft.getInstance();
        boolean next=client.level!=null&&HypixelConnection.active()&&SafariLocation.inside()&&ConfigManager.get().display.clearWater;
        int nextOpacity=Math.clamp(Math.round(ConfigManager.get().display.waterOpacity),0,100);
        if(active!=next||opacity!=nextOpacity) {active=next;opacity=nextOpacity;changeAt=System.nanoTime();}
        if(level!=client.level) {level=client.level;compiledActive=false;compiledOpacity=60;}
        if(client.level==null || compiledActive==active&&(!active||compiledOpacity==opacity) || System.nanoTime()-changeAt<250_000_000L)return;
        ClientCompat.rebuildWorldGeometry();compiledActive=active;compiledOpacity=opacity;
        DebugLog.line("CLEAR_WATER","active="+active+" opacity="+opacity);
    }
}
