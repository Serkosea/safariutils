package dev.serko.safariutils.client;

import dev.serko.safariutils.state.HoneyhiveState;
import dev.serko.safariutils.state.MonotonicTime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

/** Canyon-only observation adapter. One bounded scan per half-second; rendering uses immutable snapshots. */
public final class Honeyhives {
    public record Hive(BlockPos pos,String key) { }
    public record Visible(Hive hive,boolean ready) { }
    private static final List<Hive> CATALOG=loadCatalog();
    private static final HoneyhiveState STATE=new HoneyhiveState();
    private static List<Visible> visible=List.of();
    private static Object connection, level;
    private static UUID account;
    private static String profile="";
    private static long scanAt, profileRequestAt;
    private static int profileRequests;
    private static final Set<String> KNOWN=CATALOG.stream().map(Hive::key).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private Honeyhives() { }
    public static List<Visible> visible() { return active()?visible:List.of(); }
    private static boolean active() {
        return HypixelConnection.active() && ConfigManager.get().gameplay.honeyhiveBeacons && SafariLocation.inSkyblock()
            && !SafariLocation.inside() && ("Torrhus Canyon".equalsIgnoreCase(SafariLocation.tabListArea())
                || "Torrhus Springs".equalsIgnoreCase(SafariLocation.tabListArea())
                || "Torrhus Canyon".equalsIgnoreCase(SafariLocation.area()) || "Torrhus Springs".equalsIgnoreCase(SafariLocation.area()));
    }
    public static void tick() {
        HoneyhiveStore.tick();
        var client=Minecraft.getInstance();
        if(client.player==null||client.level==null||client.getConnection()==null) { visible=List.of(); return; }
        if(connection!=client.getConnection().getConnection()||!client.player.getUUID().equals(account)) {
            persist(); STATE.clear(); profile=""; profileRequests=0; profileRequestAt=0; connection=client.getConnection().getConnection(); account=client.player.getUUID(); scanAt=0;
        }
        if(level!=client.level) { level=client.level; STATE.resetInteractions(); visible=List.of(); scanAt=0; }
        if(!active()) { visible=List.of(); STATE.resetInteractions(); return; }
        long now=MonotonicTime.nowMillis();
        if(profile.isEmpty()&&profileRequests<2&&now>=profileRequestAt && CommandCoordinator.trySend(CommandCoordinator.request("profileid",CommandCoordinator.Category.OTHER,CommandCoordinator.Cooldown.SHARED,CommandCoordinator.Scope.CONNECTION,false,false))) {
            profileRequests++; profileRequestAt=now+10_000;
        }
        if(now<scanAt) return; scanAt=now+500;
        Map<String,String> statuses=new HashMap<>();
        for(var entity:client.level.entitiesForRendering()) {
            String raw=entity instanceof Display.TextDisplay text?text.getText().getString():entity.hasCustomName()?entity.getCustomName().getString():null;
            if(raw==null || entity.distanceToSqr(client.player)>80*80) continue;
            for(String line:SafariLocation.strip(raw).replace(' ',' ').split("\n")) {
                line=line.trim();
                if(!line.equalsIgnoreCase("Click to loot!") && HoneyhiveState.countdown(line)==null) continue;
                Hive nearest=null; double best=Double.MAX_VALUE; boolean ambiguous=false;
                for(Hive hive:CATALOG) {
                    double dx=entity.getX()-hive.pos.getX()-.5, dz=entity.getZ()-hive.pos.getZ()-.5;
                    double dy=entity.getY()-hive.pos.getY()-1.5;
                    if(Math.abs(dx)>1.5||Math.abs(dz)>1.5||dy< -2.5||dy>2.5) continue;
                    double distance=dx*dx+dy*dy+dz*dz;
                    if(Math.abs(distance-best)<.01) ambiguous=true;
                    else if(distance<best) { best=distance; nearest=hive; ambiguous=false; }
                }
                if(nearest!=null&&!ambiguous && (HoneyhiveState.countdown(line)!=null||!statuses.containsKey(nearest.key))) statuses.put(nearest.key,line);
            }
        }
        boolean changed=false;
        for(var entry:statuses.entrySet()) if(STATE.observe(entry.getKey(),entry.getValue(),now)) {
            changed=true;DebugLog.line("HONEYHIVE","observed pos="+entry.getKey()+" status="+entry.getValue());
        }
        if(changed)persist();
        List<Visible> next=new ArrayList<>();
        for(Hive hive:CATALOG) {
            if(client.level.hasChunkAt(hive.pos)&&!client.level.getBlockState(hive.pos).is(Blocks.BEE_NEST)) continue;
            var state=STATE.status(hive.key,now);
            if(state!=HoneyhiveState.Status.COOLDOWN) next.add(new Visible(hive,state==HoneyhiveState.Status.READY));
        }
        visible=List.copyOf(next);
    }
    public static void clicked(BlockPos pos) {
        if(!active()) return;
        for(Hive hive:CATALOG) if(hive.pos.equals(pos)) {
            STATE.click(hive.key,MonotonicTime.nowMillis());
            DebugLog.line("HONEYHIVE","click pos="+hive.key); return;
        }
    }
    public static void onChatMessage(String line) {
        if(line.startsWith("Profile ID: ")) {
            String next=line.substring(12).trim().replace("-","").toLowerCase(Locale.ROOT);
            if(!next.matches("[0-9a-f]{32}")||account==null)return;
            if(!profile.equals(next)) {
                boolean discovering=profile.isEmpty();
                var provisional=discovering?STATE.snapshot(MonotonicTime.nowMillis(),System.currentTimeMillis()):Map.<String,HoneyhiveState.Saved>of();
                persist(); profile=next;
                var saved=new HashMap<>(HoneyhiveStore.read(account+"/"+profile,KNOWN));saved.putAll(provisional);
                STATE.restore(saved,MonotonicTime.nowMillis(),System.currentTimeMillis(),discovering);persist();visible=List.of();scanAt=0;
                DebugLog.line("HONEYHIVE","profile scope loaded hives="+saved.size());
            }
            return;
        }
        if(line.startsWith("You are playing on profile:") || line.startsWith("You switched to profile")) {
            persist();STATE.clear(); visible=List.of(); profile=""; scanAt=0;profileRequests=0;profileRequestAt=0; return;
        }
        if(!active()) return;
        String key=STATE.chat(line,MonotonicTime.nowMillis());
        if(key!=null) { persist();scanAt=0; DebugLog.line("HONEYHIVE","chat pos="+key+" event="+line); }
        else if(line.startsWith("HIVE!")||line.startsWith("QUEEN BEE!")) DebugLog.line("HONEYHIVE","unmatched event="+line);
    }
    public static boolean hideProfileReply(String line) {
        return profileRequests>0 && MonotonicTime.nowMillis()<profileRequestAt && line.startsWith("Profile ID: ");
    }
    private static void persist() {
        if(account!=null&&!profile.isEmpty())HoneyhiveStore.put(account+"/"+profile,STATE.snapshot(MonotonicTime.nowMillis(),System.currentTimeMillis()));
    }
    public static void shutdown() { persist();HoneyhiveStore.flush(); }
    private static List<Hive> loadCatalog() {
        try(var stream=Honeyhives.class.getResourceAsStream("/assets/safariutils/honeyhives.txt")) {
            if(stream==null) throw new IllegalStateException("Missing Honeyhive catalog");
            List<Hive> result=new ArrayList<>();
            for(String line:new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                if(line.isBlank()||line.startsWith("#")) continue;
                String[] parts=line.trim().split("\\s+");
                BlockPos pos=new BlockPos(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
                result.add(new Hive(pos,pos.getX()+","+pos.getY()+","+pos.getZ()));
            }
            return List.copyOf(result);
        } catch(Exception error) { dev.serko.safariutils.SafariUtils.LOGGER.error("Cannot load Honeyhive catalog",error); return List.of(); }
    }
}
