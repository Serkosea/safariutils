package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.state.*;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Developer-only view of existing sync decisions; no polling requests or world scans. */
public final class PartySyncDebugHud implements HudElement {
    private static final int CYAN=0xFF65DDEC, GREEN=0xFF77E59E, GOLD=0xFFFFCE63, RED=0xFFFF7777;
    private static final int LABEL=0xFF9CCBD9, TEXT=0xFFF2F5FA;
    private static final Set<String> receivedPeers = new LinkedHashSet<>();
    private static String assignments = "Outside Safari";
    private static boolean connectedState;
    private static Set<String> connectedPlayers = Set.of();
    private static final SyncActivityFeed feed = new SyncActivityFeed();
    private static final Map<String,int[]> previous = new LinkedHashMap<>();
    private static String transport="Inactive", lobby="";
    private static long started;
    private static int rosterCount;
    private static boolean localLeader;
    private static boolean collecting;
    private static HudPanel cached;
    private static boolean dirty=true;
    public static boolean enabled() {
        return BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS) && ConfigManager.partySyncHudEnabled();
    }
    private static boolean active() {
        if (!enabled()) { if (collecting) { resetVisit(); collecting=false; } return false; }
        if (!collecting) { collecting=true; started=MonotonicTime.nowMillis(); dirty=true; }
        return true;
    }
    public static void status(boolean connected, boolean engaged, int members) {
        if (!active()) return;
        String nextLobby=Objects.toString(SafariLocation.lobbyId(), "Outside Safari");
        if (!nextLobby.equals(lobby)) { resetVisit(); lobby=nextLobby; }
        String nextTransport=connected ? "Connected" : engaged ? "Connecting / retrying" : "Inactive";
        var party=PartyState.snapshot();
        String nextAssignments=PartyHotspotSync.statusText();
        if (!nextTransport.equals(transport)) event("Connection: " + nextTransport, connected?GREEN:GOLD);
        if (members != rosterCount || party.localLeader() != localLeader)
            event("Party: " + members + " on roster · You are " + (party.localLeader()?"leader":"member"), CYAN);
        if (!nextAssignments.equals(assignments)) event("Hotspots: " + nextAssignments,
            PartyHotspotSync.conflicted()?RED:CYAN);
        var nextPlayers=dev.serko.safariutils.api.PartySyncNetworks.connectedPlayers();
        if (!nextPlayers.equals(connectedPlayers)) {
            connectedPlayers=Set.copyOf(nextPlayers);
            event("Connected players: " + connectedNames(), connectedPlayers.isEmpty()?GOLD:GREEN);
        }
        transport=nextTransport; connectedState=connected;
        rosterCount=members; localLeader=party.localLeader(); assignments=nextAssignments;

    }
    public static void event(String text, int colour) {
        if (!active()) return;
        feed.add(MonotonicTime.nowMillis()-started,text,colour); dirty=true;
    }
    public static void received(String player, String type, int[] values, boolean local) {
        if (!active()) return;
        if (!local && receivedPeers.size() < 4 && receivedPeers.add(player)) dirty=true;
        String key=(local?"local:":"remote:")+player+":"+type;
        int[] old=previous.get(key);
        if (Arrays.equals(old,values)) return;
        if (old != null && old.length != values.length) {
            int oldLength=old.length;
            old = Arrays.copyOf(old, values.length);
            if (oldLength<=38 && values.length>38) old[38]=-1;
        }
        if (previous.size()>=32 && !previous.containsKey(key)) previous.remove(previous.keySet().iterator().next());
        previous.put(key,values.clone());
        List<String> changes=new ArrayList<>();
        if (type.equals("A")) {
            for(int i=0;i<values.length;i++) {
                if(i==33 || i==37) continue;
                boolean changed=old==null?(values[i]!=0 || i==38):old[i]!=values[i];
                if(i==36) changed=old==null?(values[36]|values[37])!=0:old[36]!=values[36] || old[37]!=values[37];
                if(!changed) continue;
                String label=i<9?StartingItems.ORDERED.get(i).singular()+" found":aggregateLabel(i);
                changes.add(label+": "+(i>=25 && i<=32 ? (values[i]!=0?"Yes":"No") : String.valueOf(i==36?Integer.bitCount(values[36])+Integer.bitCount(values[37]):(i==23 || i==34 || i==35)?Integer.bitCount(values[i]):values[i])));
            }
        } else if(type.equals("N")) changes.add("Nests checked: "+Integer.bitCount(values[0]));
        else if(type.equals("S")) {
            long found=values[0]|((long)values[1]<<30), caught=values[2]|((long)values[3]<<30);
            long previousFound=old==null?0:old[0]|((long)old[1]<<30), previousCaught=old==null?0:old[2]|((long)old[3]<<30);
            if ((found&~previousFound)!=0) changes.add("Found "+PartySparklingDiscoveries.names(found&~previousFound));
            if ((caught&~previousCaught)!=0) changes.add("Caught "+PartySparklingDiscoveries.names(caught&~previousCaught));
        }
        else if(type.equals("U")) changes.add("Bird feeder progress updated");
        if(changes.isEmpty()) changes.add("Snapshot accepted (no new objectives)");
        String detail=String.join(", ",changes.subList(0,Math.min(3,changes.size())));
        if(changes.size()>3) detail+=" (+"+(changes.size()-3)+" fields)";
        event((local?"You":player)+": "+detail,local?GOLD:CYAN);
    }
    private static String aggregateLabel(int index) {
        return switch(index) {
            case 9 -> "forest drops"; case 10 -> "seeds held"; case 11 -> "worms held"; case 12 -> "berries held";
            case 13 -> "feed used"; case 14 -> "Bluebirds"; case 15 -> "Parakeets"; case 16 -> "Macaws";
            case 17 -> "feeder type"; case 18 -> "feeder stack"; case 19 -> "lime held"; case 20 -> "orange held";
            case 21 -> "purple held"; case 22 -> "incense held"; case 23 -> "gems placed"; case 24 -> "candles lit";
            case 25 -> "Gemzie door open"; case 26 -> "Doomspiral spawned"; case 27 -> "Doomspiral caught";
            case 28 -> "Wumpa awake"; case 29 -> "Wumpa caught"; case 30 -> "Doomspiral retreated";
            case 31 -> "Gemzie caught"; case 32 -> "Wumpa retreated"; case 34 -> "Snoozle walls cleared";
            case 35 -> "Troodon walls cleared"; case 38 -> "Shining Coins held"; default -> "mounds cleared";
        };
    }
    public static void resetVisit() {
        feed.clear(); previous.clear(); receivedPeers.clear(); lobby=""; transport="Inactive";
        assignments="Outside Safari"; connectedPlayers=Set.of(); connectedState=false; rosterCount=0; localLeader=false; cached=null; dirty=true;
        started=MonotonicTime.nowMillis();
    }
    private static String connectedNames() {
        if (connectedPlayers.isEmpty()) return connectedState?"Awaiting confirmation":"None";
        var members=PartyState.snapshot().members();
        return connectedPlayers.stream().sorted().map(id -> members.stream()
            .filter(member -> member.uuid().equals(id)).map(member -> member.name()).findFirst().orElse("Party member"))
            .collect(java.util.stream.Collectors.joining(", "));
    }
    static HudPanel buildPanel() {
        if(!active()) return null;
        if(!dirty && cached!=null) return cached;
        HudPanel panel=new HudPanel(); panel.title("PARTY SYNC",CYAN);
        panel.pair("Connection",transport,LABEL,connectedState?GREEN:GOLD);
        panel.pair("Safari lobby",lobby.isBlank()?"Outside Safari":lobby,LABEL,TEXT);
        panel.pair("Party roster",rosterCount==0?"No active room":rosterCount+" player"+(rosterCount==1?"":"s")
            +" · You are "+(localLeader?"leader":"member"),LABEL,TEXT);
        panel.pair("Connected players",connectedNames(),LABEL,connectedPlayers.isEmpty()?GOLD:GREEN);
        panel.pair("Received from",receivedPeers.isEmpty()?"No other players yet":String.join(", ",receivedPeers),LABEL,
            receivedPeers.isEmpty()?GOLD:CYAN);
        panel.pair("Hotspots",assignments,LABEL,PartyHotspotSync.conflicted()?RED:
            SafariLocation.inside() && PartyHotspotSync.networkAuthority()?GREEN:GOLD);
        panel.blank();
        panel.line("ACTIVITY · oldest → newest",CYAN);
        panel.line("Gold: you · Cyan: shared · Green: success",LABEL);
        var entries=feed.entries();
        if(entries.isEmpty()) panel.line("Waiting for sync activity",TEXT);
        for(var entry:entries) {
            long seconds=Math.max(0,entry.time()/1000);
            panel.line(net.minecraft.network.chat.Component.literal(String.format(Locale.ROOT,"%02d:%02d  ",seconds/60,seconds%60))
                .withColor(LABEL).append(net.minecraft.network.chat.Component.literal(entry.text()).withColor(entry.colour())));
        }
        dirty=false; return cached=panel;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,DeltaTracker delta) {
        if(!active()) return;
        var client=Minecraft.getInstance();
        if(client.player==null || ClientCompat.hudHidden() || ClientCompat.screen() instanceof HudEditorScreen) return;
        HudPanel panel=buildPanel(); var box=HudBox.PARTY_SYNC_DEBUG;
        float scale=box.scale()*ResponsiveUI.scale(graphics.guiWidth(),graphics.guiHeight());
        panel.render(graphics,client.font,box.pixelX(graphics.guiWidth(),panel,client.font,scale),box.pixelY(graphics.guiHeight(),panel,scale),scale,CYAN);
    }
}
