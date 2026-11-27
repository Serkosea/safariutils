package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.state.*;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Developer-only view of existing sync decisions; no polling requests or world scans. */
public final class PartySyncDebugHud implements HudElement {
    private static volatile String serviceFailure = "";
    public static void serviceFailure(String reason) { serviceFailure = reason; }
    private static final int CYAN=0xFF65DDEC, GREEN=0xFF77E59E, GOLD=0xFFFFCE63, RED=0xFFFF7777;
    private static final int LABEL=0xFF9CCBD9, TEXT=0xFFF2F5FA;
    private static String displayedFailure="";
    private static String assignments = "Outside Safari";
    private static boolean connectedState;
    private static Set<String> connectedPlayers = Set.of();
    private static Map<String, String> rosterNames = Map.of();
    private static final SyncActivityFeed feed = new SyncActivityFeed();
    private static final Map<String,int[]> previous = new LinkedHashMap<>();
    private static int reporters;
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
        status(connected, engaged, members, Map.of());
    }
    public static void status(boolean connected, boolean engaged, int members, Map<String, String> names) {
        if (!active()) return;
        String nextLobby=Objects.toString(SafariLocation.lobbyId(), "Outside Safari");
        if (!nextLobby.equals(lobby)) { resetVisit(); lobby=nextLobby; }
        if (!rosterNames.equals(names)) { rosterNames = Map.copyOf(names); dirty = true; }
        boolean ready=connected && dev.serko.safariutils.api.PartySyncNetworks.replayReady();
        if (ready) serviceFailure="";
        String failure=engaged && !connected ? serviceFailure : "";
        String nextTransport=ready ? "Ready" : connected ? "Applying replay" : !engaged ? "Idle"
            : failure.isEmpty() ? "Connecting" : "Disconnected";
        var party=PartyState.snapshot();
        String nextAssignments=PartyHotspotSync.statusText();
        var nextPlayers=dev.serko.safariutils.api.PartySyncNetworks.connectedPlayers();
        int nextReporters=ready ? PartyCompletionSync.reporterCount() : 0;
        if (!nextTransport.equals(transport) || !failure.equals(displayedFailure) || members!=rosterCount
                || party.localLeader()!=localLeader || !assignments.equals(nextAssignments) || nextReporters!=reporters)
            dirty=true;
        if (!nextPlayers.equals(connectedPlayers)) {
            connectedPlayers=Set.copyOf(nextPlayers);
            dirty=true;
        }
        displayedFailure=failure;
        reporters=nextReporters;
        transport=nextTransport; connectedState=ready;
        rosterCount=members; localLeader=party.localLeader(); assignments=nextAssignments;

    }
    public static void event(String text, int colour) {
        if (!active()) return;
        feed.add(MonotonicTime.nowMillis()-started,text,colour); dirty=true;
    }
    public static void received(String player, String type, int[] values, boolean local) {
        received(player,type,values,local,false);
    }
    static void received(String player,String type,int[] values,boolean local,boolean restored) {
        if (!active()) return;
        String key=(local?"local:":"remote:")+player+":"+type;
        int[] old=previous.get(key);
        if (Arrays.equals(old,values)) return;
        if (old != null && old.length != values.length) {
            int oldLength=old.length;
            old = Arrays.copyOf(old, values.length);
            if (oldLength<=38 && values.length>38) old[38]=-1;
        }
        if (previous.size()>=32 && !previous.containsKey(key)) previous.remove(previous.keySet().iterator().next());
        int[] baseline=values.clone();
        if (restored) {
            // Replay omits stale inventories and substitutes the current feeder state.
            for (int i : new int[]{10,11,12,17,18,19,20,21,22,38})
                if(i<baseline.length) baseline[i]=old==null ? (i==17 || i==38 ? -1 : 0) : old[i];
            values=baseline;
        }
        previous.put(key,baseline);
        List<String> changes=new ArrayList<>();
        if (type.equals("A")) {
            for(int i=0;i<values.length;i++) {
                if(i==33 || i==37) continue;
                boolean changed=old==null?((i==17 || i==38 ? values[i]>0 : values[i]!=0)):old[i]!=values[i];
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
        else if(type.equals("U")) {
            for(int i=0;i<values.length;i++)
                if(old==null ? values[i]>0 : values[i]!=old[i])
                    changes.add(aggregateLabel(13+i)+": "+values[i]);
        }
        if(changes.isEmpty()) return;
        String prefix=(local?"You":player)+(restored?" · restored · ":" · ");
        for(String change:changes) event(prefix+change,local?GOLD:CYAN);
    }
    private static final Map<String,CompletionSnapshot> completions=new HashMap<>();
    static void completion(String reporter,CompletionSnapshot next,boolean local) {
        if(!active() || next==null) return;
        CompletionSnapshot prior=completions.put(reporter,next);
        var species=dev.serko.safariutils.data.Critters.selectionOrder();
        String player=local?"You":rosterNames.getOrDefault(reporter.replace("-",""),"Party member");
        for(int i=0;i<species.size();i++) {
            var changes=new ArrayList<String>();
            int flags=next.value(i,0), old=prior==null?0:prior.value(i,0);
            for(int bit=0;bit<3;bit++) if(((flags^old)&(1<<bit))!=0) {
                String mode=switch(bit){case 0->"Unique";case 1->"Sparkling";default->"Full Clear";};
                changes.add(mode+((flags&(1<<bit))!=0?" done":" open"));
            }
            for(int col=1;col<=2;col++) if(next.value(i,col)!=(prior==null?0:prior.value(i,col)))
                changes.add(next.value(i,col)+(col==1?" seen":" caught"));
            if(!changes.isEmpty()) event(player+" · "+species.get(i).name()+" · "+String.join(", ",changes),local?GOLD:CYAN);
        }
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
        feed.clear(); completions.clear(); previous.clear(); displayedFailure=""; serviceFailure=""; reporters=0; rosterNames = Map.of(); lobby=""; transport="Inactive";
        assignments="Outside Safari"; connectedPlayers=Set.of(); connectedState=false; rosterCount=0; localLeader=false; cached=null; dirty=true;
        started=MonotonicTime.nowMillis();
    }
    private static String connectedNames() {
        if (connectedPlayers.isEmpty()) return "Awaiting presence";
        var members=PartyState.snapshot().members();
        return connectedPlayers.stream().sorted().map(id -> members.stream()
            .filter(member -> member.uuid().equals(id.replace("-", "").toLowerCase(Locale.ROOT))).map(member -> member.name()).findFirst()
            .orElse(rosterNames.getOrDefault(id.replace("-", "").toLowerCase(Locale.ROOT), "Party member")))
            .collect(java.util.stream.Collectors.joining(", "));
    }
    private static long historyOpened;
    private static int displayedPage;
    static HudPanel buildPanel() {
        if(!active()) return null;
        // Keep five lines during play. Opening chat pages through this visit’s history.
        boolean browsing=ClientCompat.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
        long now=MonotonicTime.nowMillis();
        if(!browsing) historyOpened=0;
        else if(historyOpened==0) historyOpened=now;
        int pages=Math.max(1,(feed.size()+4)/5);
        int page=browsing ? (int)(((now-historyOpened)/3500)%pages) : 0;
        if(page!=displayedPage){displayedPage=page;dirty=true;}
        if(!dirty && cached!=null) return cached;
        HudPanel panel=new HudPanel(); panel.title("PARTY SYNC",CYAN);
        panel.pair("Status",transport,LABEL,connectedState?GREEN:displayedFailure.isEmpty()?GOLD:RED);
        if(!displayedFailure.isEmpty()) {
            String line="";
            for(String word:displayedFailure.split(" ")) {
                if(!line.isEmpty() && (line.length()+word.length()>42)) { panel.line(line,RED); line=""; }
                line=line.isEmpty()?word:line+" "+word;
            }
            if(!line.isEmpty())panel.line(line,RED);
        }
        if(SafariLocation.inside()) {
            panel.pair("Lobby",lobby+(localLeader?"  \u00b7  Leader":""),LABEL,TEXT);
            if(rosterCount>0) {
                String count=connectedPlayers.size()+"/"+rosterCount;
                panel.pair("Connected "+count,connectedNames(),LABEL,connectedPlayers.isEmpty()?LABEL:GREEN);
            }
            if(rosterCount>1)panel.pair("Hotspots",assignments,LABEL,PartyHotspotSync.conflicted()?RED:
                PartyHotspotSync.networkAuthority()?GREEN:GOLD);
            if(reporters>0)panel.pair("Completion reporters",Integer.toString(reporters),LABEL,TEXT);
        }
        var entries=feed.entries();
        if(!entries.isEmpty()) { panel.blank(); panel.line(page==0?"Recent activity":"Earlier activity · "+(page+1)+"/"+pages,LABEL); }
        int end=entries.size()-5*page;
        for(int i=end-1;i>=Math.max(0,end-5);i--) {
            var entry=entries.get(i);
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
