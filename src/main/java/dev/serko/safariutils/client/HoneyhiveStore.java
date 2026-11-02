package dev.serko.safariutils.client;

import com.google.gson.*;
import dev.serko.safariutils.io.AtomicFiles;
import dev.serko.safariutils.io.PersistentJson;
import dev.serko.safariutils.state.HoneyhiveState;
import java.nio.file.Files;
import java.util.*;

/** Small bounded profile ledger, atomically saved only after observations change. */
final class HoneyhiveStore {
    private static JsonObject root;
    private static boolean blocked,dirty;
    private static long saveAt;
    private static void load() {
        if(root!=null)return;
        root=new JsonObject(); root.addProperty("schema",1);root.add("profiles",new JsonObject());
        var path=SafariPaths.honeyhives(); if(!Files.isRegularFile(path))return;
        try {
            if(Files.size(path)>1_000_000)throw new IllegalArgumentException("Oversized hive store");
            var decoded=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            PersistentJson.requireSupportedSchema(decoded,"schema",1);
            if(!decoded.has("profiles")||!decoded.get("profiles").isJsonObject())throw new IllegalArgumentException("Invalid hive profiles");
            root=decoded;
        } catch(Exception error) { blocked=true; dev.serko.safariutils.SafariUtils.LOGGER.warn("Honeyhive store preserved, cannot load",error); }
    }
    static Map<String,HoneyhiveState.Saved> read(String scope,Set<String> known) {
        load(); Map<String,HoneyhiveState.Saved> result=new HashMap<>();
        var value=root.getAsJsonObject("profiles").get(scope); if(value==null)return result;
        if(!value.isJsonObject()) { blocked=true;return result; }
        for(var entry:value.getAsJsonObject().entrySet()) {
            if(!known.contains(entry.getKey()))continue;
            if(!entry.getValue().isJsonObject()) { blocked=true;continue; }
            try { var item=entry.getValue().getAsJsonObject();result.put(entry.getKey(),new HoneyhiveState.Saved(item.get("collectedAt").getAsLong(),item.get("refillAt").getAsLong())); }
            catch(RuntimeException invalid) { blocked=true; }
        }
        return result;
    }
    static void put(String scope,Map<String,HoneyhiveState.Saved> values) {
        load(); if(blocked)return;
        var profiles=root.getAsJsonObject("profiles"); var old=profiles.get(scope);
        JsonObject data=old!=null&&old.isJsonObject()?old.getAsJsonObject().deepCopy():new JsonObject();
        for(var entry:values.entrySet()) {
            var item=data.has(entry.getKey())&&data.get(entry.getKey()).isJsonObject()?data.getAsJsonObject(entry.getKey()).deepCopy():new JsonObject();
            item.addProperty("collectedAt",entry.getValue().collectedAt());item.addProperty("refillAt",entry.getValue().refillAt());data.add(entry.getKey(),item);
        }
        if(data.equals(old))return;
        if(!profiles.has(scope)&&profiles.size()>=100) { dev.serko.safariutils.SafariUtils.LOGGER.warn("Honeyhive profile store full; preserving existing profiles");return; }
        profiles.add(scope,data); if(!dirty)saveAt=System.currentTimeMillis()+2000;dirty=true;
    }
    static void tick() { if(dirty&&System.currentTimeMillis()>=saveAt)flush(); }
    static void flush() {
        if(!dirty||blocked)return;
        try { AtomicFiles.writeString(SafariPaths.honeyhives(),new Gson().toJson(root));dirty=false; }
        catch(Exception error) { saveAt=System.currentTimeMillis()+30_000;dev.serko.safariutils.SafariUtils.LOGGER.warn("Could not save hive state",error); }
    }
}
