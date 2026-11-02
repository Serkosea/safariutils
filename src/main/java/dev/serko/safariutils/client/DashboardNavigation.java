package dev.serko.safariutils.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** One navigation model and renderer for all stats views, including collection editors. */
final class DashboardNavigation {
    enum Tab {
        STATS("Stats"), HISTORY("History"), PROFIT("Profit"),
        SPARKLINGS("Sparklings"), LOOKUP("Lookup"), PERSONAL_BESTS("Personal Bests");
        final String label;
        Tab(String label) { this.label = label; }
        void open() {
            Minecraft.getInstance().execute(() -> ClientCompat.setScreen(
                this == SPARKLINGS || this == LOOKUP ? new SparklingScreen(this == LOOKUP)
                    : new SafariDashboardScreen(this)));
        }
        int colour() {
            var config = ConfigManager.get().display;
            return switch(this) {
                case STATS -> Colours.argb(config.statsTabBorderColour, 0xFF55FFFF);
                case HISTORY -> Colours.argb(config.historyTabBorderColour, 0xFF55FF55);
                case PERSONAL_BESTS -> Colours.argb(config.personalBestsTabBorderColour, StatColours.PERSONAL_BEST);
                case PROFIT -> Colours.argb(config.profitTabBorderColour, StatColours.COINS);
                case SPARKLINGS, LOOKUP -> UIDraw.rainbow(0,12,0.55f);
            };
        }
    }
    private static final Tab[] TABS = Tab.values();
    record Item(Tab tab,int x,int y,int width) { }
    static int width(Font font) {
        int result=0; for(var tab:TABS) result+=Math.max(66,font.width(tab.label)+20); return result;
    }
    static List<Item> layout(Font font,int left,int panelWidth,int y) {
        int x=left+(panelWidth-width(font))/2;
        List<Item> items=new ArrayList<>();
        for(var tab:TABS) { int w=Math.max(66,font.width(tab.label)+20); items.add(new Item(tab,x,y,w)); x+=w; }
        return List.copyOf(items);
    }
    static void draw(GuiGraphicsExtractor g,Font font,List<Item> items,Tab active,int mx,int my) {
        if(items.isEmpty())return;
        Item first=items.getFirst(),last=items.getLast(); int right=last.x+last.width;
        g.fillGradient(first.x,first.y,right,first.y+21,0x9A18212C,0x9A111820);
        if(SpecialTheme.rainbow()) SpecialTheme.border(g,first.x,first.y,right-first.x,21,1);
        else UIDraw.outline(g,first.x,first.y,right-first.x,21,0x38FFFFFF);
        for(var item:items) {
            boolean selected=item.tab==active, hover=mx>=item.x&&mx<item.x+item.width&&my>=item.y&&my<item.y+21;
            if(item!=first)g.fill(item.x,item.y+5,item.x+1,item.y+16,0x38FFFFFF);
            if(selected||hover)g.fill(item.x+3,item.y+3,item.x+item.width-3,item.y+18,selected?0xB025303D:0x5825303D);
            if(selected) {
                if(SpecialTheme.rainbow()||active==Tab.SPARKLINGS||active==Tab.LOOKUP)SpecialTheme.bar(g,item.x+3,item.y+16,item.width-6,2);
                else g.fill(item.x+3,item.y+16,item.x+item.width-3,item.y+18,item.tab.colour());
            }
            SpecialTheme.text(g,font,Component.literal(item.tab.label),item.x+(item.width-font.width(item.tab.label))/2,item.y+6,
                selected||hover?0xFFFFFFFF:StatColours.INFO);
        }
    }
}
