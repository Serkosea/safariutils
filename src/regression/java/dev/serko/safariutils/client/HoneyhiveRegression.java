package dev.serko.safariutils.client;
import dev.serko.safariutils.state.HoneyhiveState;
public final class HoneyhiveRegression {
    public static int run() {
        var state=new HoneyhiveState(); long now=10000;
        check(state.status("a",now)==HoneyhiveState.Status.UNKNOWN,"unknown is not confirmed ready");
        state.click("a",now); state.click("a",now+1);
        check("a".equals(state.chat("HIVE! Reward",now+10)),"confirmed harvest");
        check(state.status("a",now+11)==HoneyhiveState.Status.COOLDOWN,"one hour cooldown");
        state.observe("a","Click to loot!",now+50);
        check(state.status("a",now+51)==HoneyhiveState.Status.COOLDOWN,"stale hologram guard");
        check(state.chat("HIVE! Duplicate",now+20)==null,"spam clicks do not duplicate harvest");
        state.chat("QUEEN BEE! Refilled",now+30);
        check(state.status("a",now+31)==HoneyhiveState.Status.READY,"queen after reward");
        state.click("b",now+100); state.chat("QUEEN BEE! Refilled",now+110);state.chat("HIVE! Reward",now+120);
        check(state.status("b",now+121)==HoneyhiveState.Status.READY,"queen before reward");
        state.observe("a","Refill in: 59m 12s",now+3000);
        check(state.status("a",now+3001)==HoneyhiveState.Status.COOLDOWN,"observed countdown wins after settling");
        check(state.status("a",now+3000+3552000)==HoneyhiveState.Status.READY,"cooldown expiry");
        check(HoneyhiveState.countdown("Refill in: 5s")==5000,"seconds countdown");
        check(HoneyhiveState.countdown("Refill in: 61m")==null,"invalid countdown rejected");
        state.clear(); check(state.status("a",now)==HoneyhiveState.Status.UNKNOWN,"scope reset");
        state.click("c",now); check(state.chat("HIVE! Late",now+6000)==null,"expired clicks not attributed");
        state.click("c",now+7000); state.resetInteractions();check(state.chat("HIVE! Warp",now+7010)==null,"warp removes pending attribution");
        state.clear();state.click("a",1000);state.chat("HIVE! Loot",1010);
        var snapshot=state.snapshot(2000,1_000_000);
        var restored=new HoneyhiveState();restored.restore(snapshot,3000,1_100_000);
        check(restored.status("a",3001)==HoneyhiveState.Status.COOLDOWN,"saved cooldown survives restart with another monotonic origin");
        check(snapshot.get("a").collectedAt()==999010,"actual collection time persisted");
        restored.restore(snapshot,5000,5_000_000);
        check(restored.status("a",5000)==HoneyhiveState.Status.READY,"elapsed offline hour restores ready");
        state.clear();state.click("a",6000);
        state.restore(java.util.Map.of(),6001,5_000_000,true);
        check("a".equals(state.chat("HIVE! Loot",6002)),"profile discovery preserves pending harvest");
        state.restore(state.snapshot(6003,5_000_002),6003,5_000_002,true);
        state.observe("a","Click to loot!",6004);
        check(state.status("a",6005)==HoneyhiveState.Status.COOLDOWN,"profile discovery preserves confirmation guard");
        state.clear();state.click("old",1000);state.chat("HIVE! Reward",1010);
        state.click("next",1050);state.chat("QUEEN BEE! The Honeyhive instantly refilled with loot!",1060);
        state.chat("HIVE! Reward",1070);
        check(state.status("old",1071)==HoneyhiveState.Status.COOLDOWN,"refill does not select previous harvest");
        check(state.status("next",1071)==HoneyhiveState.Status.READY,"queued harvest stays ready without hologram confirmation");
        alphaPersistence();
        return 23;
    }
    private static void alphaPersistence() {
        try {
            var root=HoneyhiveStore.class.getDeclaredField("root");root.setAccessible(true);
            var dirty=HoneyhiveStore.class.getDeclaredField("dirty");dirty.setAccessible(true);
            var flag=Honeyhives.class.getDeclaredField("persistentConnection");flag.setAccessible(true);
            var account=Honeyhives.class.getDeclaredField("account");account.setAccessible(true);
            var profile=Honeyhives.class.getDeclaredField("profile");profile.setAccessible(true);
            var stateField=Honeyhives.class.getDeclaredField("STATE");stateField.setAccessible(true);
            var persist=Honeyhives.class.getDeclaredMethod("persist");persist.setAccessible(true);
            var state=(HoneyhiveState)stateField.get(null);
            var data=new com.google.gson.JsonObject();data.addProperty("schema",1);data.add("profiles",new com.google.gson.JsonObject());
            Object priorRoot=root.get(null),priorAccount=account.get(null),priorProfile=profile.get(null);
            boolean priorDirty=dirty.getBoolean(null),priorFlag=flag.getBoolean(null);
            try {
                root.set(null,data);dirty.setBoolean(null,false);account.set(null,java.util.UUID.randomUUID());profile.set(null,"test-profile");
                flag.setBoolean(null,true);state.clear();state.click("hive",1000);state.chat("HIVE! Reward",1010);persist.invoke(null);
                var main=data.deepCopy();dirty.setBoolean(null,false);
                flag.setBoolean(null,false);state.clear();state.click("hive",2000);state.chat("QUEEN BEE! Refilled",2010);persist.invoke(null);
                check(data.equals(main)&&!dirty.getBoolean(null),"Alpha hive observations never overwrite main ledger or schedule a save");
                persist.invoke(null);
                check(data.equals(main),"late Alpha disconnect/shutdown persists no hive data");
            } finally {
                state.clear();root.set(null,priorRoot);dirty.setBoolean(null,priorDirty);flag.setBoolean(null,priorFlag);
                account.set(null,priorAccount);profile.set(null,priorProfile);
            }
        } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}
