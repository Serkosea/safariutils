package dev.serko.safariutils.session;

import dev.serko.safariutils.data.*;
import java.util.*;

public final class PersonalBestRegression {
    private static void check(boolean condition,String name) { if(!condition)throw new AssertionError(name); }
    public static int run() {
        var tracker = new PersonalBestTracker(true, "Self");
        tracker.enter(SafariBiome.FOREST,100);
        long elapsed=1000;
        for(var critter:Critters.all()) {
            tracker.catchSpecies(critter,"Self",elapsed++);
            check(tracker.catchSpecies(critter,"Other",elapsed).isEmpty(),"duplicate changed contributor/milestone");
        }
        check(tracker.splits().size()==9,"four biome clears, full clear and four personal mobs");
        check(tracker.splits().stream().allMatch(s -> PersonalBestTracker.eligible(s)&&s.group()==PersonalBestTracker.Group.SOLO),"solo contributor group");
        var forest=tracker.splits().stream().filter(s->s.target().equals("FOREST")).findFirst().orElseThrow();
        check(forest.sinceEntryMillis()==forest.elapsedMillis()-100,"entry timing");
        tracker.catchSpecies(Critters.MACAW,"LatePlayer",9000);
        check(forest.contributors().equals(List.of("self")),"late duplicate changed frozen group");
        var mixed=new PersonalBestTracker(true, "Alpha");
        int index=0;
        for(var critter:Critters.all()) {
            String name=critter.biome()==SafariBiome.FOREST ? (++index%2==0?"Alpha":"Beta") : "Gamma";
            mixed.catchSpecies(critter,name,2000);
        }
        check(mixed.splits().stream().filter(s->s.target().equals("FOREST")).findFirst().orElseThrow().group()==PersonalBestTracker.Group.DUO,"per-biome contributors");
        check(mixed.splits().stream().noneMatch(s->s.target().equals("CAVERN")),"non-participant awarded teammate clear");
        for(var c:Critters.inBiome(SafariBiome.CAVERN)) mixed.catchSpecies(c,"Alpha",9000);
        check(mixed.splits().stream().noneMatch(s->s.target().equals("CAVERN")),"late duplicate qualified non-participant");
        check(mixed.splits().stream().filter(s->s.target().equals("ALL")).findFirst().orElseThrow().group()==PersonalBestTracker.Group.TRIO,"full-clear contributor union");
        var casing=new PersonalBestTracker(true, "Self");
        index=0;
        for(var critter:Critters.inBiome(SafariBiome.ICY))casing.catchSpecies(critter,++index%2==0?"Self":"sELF",2000);
        check(casing.splits().getFirst().group()==PersonalBestTracker.Group.SOLO,"case variation added contributor");
        var noMacaw=new PersonalBestTracker(true, "Self");
        for(var critter:Critters.all())if(!critter.equals(Critters.MACAW))noMacaw.catchSpecies(critter,"Self",1000);
        check(noMacaw.splits().stream().noneMatch(s->s.target().equals("ALL")||s.target().equals("FOREST")),"unavailable RNG species counted");
        var late=new PersonalBestTracker(false, "Self");
        for(var critter:Critters.all())late.catchSpecies(critter,"Self",1000);
        check(late.splits().stream().noneMatch(PersonalBestTracker::eligible),"partial timing qualified");
        var record=new RunRecord();record.self="Self";record.personalBestSplits.addAll(tracker.splits());
        record.playerBiomeUniques=Map.of("Self",Map.of("FOREST",3));
        var decoded=RunRecord.fromJson(record.toJson());decoded.normalize();
        check(decoded.personalBestSplits.equals(record.personalBestSplits),"split roundtrip");
        check(decoded.playerBiomeUniques.equals(record.playerBiomeUniques),"per-player totals roundtrip");
        var legacy=RunRecord.fromJson(com.google.gson.JsonParser.parseString("{\"started\":1,\"personalBestSplits\":[{\"rules\":1,\"scope\":\"TEAM\",\"target\":\"FOREST\",\"group\":\"SOLO\",\"elapsedMillis\":50,\"timingKnown\":true}]}").getAsJsonObject());legacy.normalize();
        check(PersonalBests.calculate(List.of(legacy)).isEmpty(),"attendance-based legacy PB reclassified");
        check(legacy.playerBiomeUniques==null,"invented old teammate totals");
        var slower=new RunRecord();slower.self="Self";slower.personalBestSplits.add(new PersonalBestTracker.Split(2,forest.target(),forest.elapsedMillis()+500,forest.contributors(),true,-1,-1));
        var best=PersonalBests.calculate(List.of(record,slower)).get(new PersonalBests.Key(forest.target(),forest.group()));
        check(best.runNumber()==1&&best.clears()==2,"best aggregation");
        check(PersonalBestTracker.time(59999).equals("0:59.99")&&PersonalBestTracker.time(60000).equals("1:00.00"),"display rollover");
        check(PersonalBestTracker.comparisonColour(900,1000,true)==0x55FF88,"improvement color");
        check(PersonalBestTracker.comparisonColour(1400,1000,true)==0xC43C45,"very slow color");
        check(PersonalBestTracker.comparisonColour(1020,1000,true)!=PersonalBestTracker.comparisonColour(1200,1000,true),"slower severity color");
        var solo=new RunRecord();solo.self="SoloPlayer";solo.players.add("SoloPlayer");
        solo.own.put("Duplico",2);solo.shared.put("Hideonwall",1);
        var soloCounts=solo.displayPlayerBiomeUniques();
        check(soloCounts.get("SoloPlayer").get("HAUNTED")==2,"solo unique counts count species, not duplicates");
        check(soloCounts.values().stream().flatMap(m->m.values().stream()).mapToInt(Integer::intValue).sum()==2,"solo legacy catches inferred");
        check(solo.playerBiomeUniques==null,"display inference changed saved history");
        solo.players.add("OtherPlayer");
        check(solo.displayPlayerBiomeUniques().get("SoloPlayer").get("HAUNTED")==1
            && solo.displayPlayerBiomeUniques().get("OtherPlayer").get("HAUNTED")==1,"duo separate unique totals");
        solo.players.add("ThirdPlayer");
        check(solo.displayPlayerBiomeUniques().size()==1 && solo.missingIndividualTeammateTotals(),"trio own totals without invented teammates");
        solo.players.add("FourthPlayer");
        check(solo.displayPlayerBiomeUniques().size()==1 && solo.missingIndividualTeammateTotals(),"squad own totals without invented teammates");
        solo.players.clear();
        check(solo.displayPlayerBiomeUniques().size()==1 && solo.missingIndividualTeammateTotals(),"missing participants must retain only own totals");
        check(record.displayPlayerBiomeUniques()==record.playerBiomeUniques,"recorded attribution replaced");
        var session=new SafariSession("Self",1000);
        var critter=Critters.byName("Duplico");
        session.record(new dev.serko.safariutils.parse.CritterEvent(dev.serko.safariutils.parse.CritterEvent.Type.OWN_CATCH,critter,null,3,false),1100);
        for(String player:List.of("Other","oTHER"))
            session.record(new dev.serko.safariutils.parse.CritterEvent(dev.serko.safariutils.parse.CritterEvent.Type.SHARED_CATCH,critter,player,2,false),1200);
        var snapshot=RunRecord.of(session);
        check(snapshot.playerCatches.get("Self").get("Duplico")==1,"own counts are catches not shards");
        check(snapshot.playerCatches.get("Other").get("Duplico")==2 && snapshot.playerCatches.size()==2,"case-insensitive individual duplicates");
        check(session.uniquePerPlayer().get("Other").get(SafariBiome.HAUNTED)==1,"duplicate capitalization inflated uniques");
        var saved=RunRecord.fromJson(snapshot.toJson());saved.normalize();
        check(saved.playerCatches.equals(snapshot.playerCatches),"individual catch persistence");
        check(saved.displayPlayerBiomeUniques().get("Other").get("HAUNTED")==1,"derive uniques from individual catches");
        session.record(new dev.serko.safariutils.parse.CritterEvent(dev.serko.safariutils.parse.CritterEvent.Type.OWN_CATCH,critter,null,1,false),1300);
        check(snapshot.playerCatches.get("Self").get("Duplico")==1,"saved catch snapshot changed with session");
        check(legacy.playerCatches==null,"legacy missing attribution manufactured");
        var foreign=new RunRecord();foreign.self="Other";foreign.personalBestSplits.addAll(record.personalBestSplits);
        check(PersonalBests.calculate(List.of(foreign)).isEmpty(),"saved foreign clear qualified");
        check(PersonalBestTracker.time(9).equals("0:00.00") && PersonalBestTracker.time(10).equals("0:00.01")
            && PersonalBestTracker.time(-1).equals("0:00.00"),"hundredth precision and lower bound");
        var personalMob=new PersonalBestTracker(true,"Self");
        for(var biome:SafariBiome.values()) {
            String target=PersonalBestTracker.mobTarget(biome);
            var mob=Critters.byName(PersonalBestTracker.title(target));
            check(personalMob.catchSpecies(mob,"Teammate",100).isEmpty(),"shared mob catch qualified");
            var own=personalMob.catchSpecies(mob,"sELF",4567);
            check(own.size()==1 && own.getFirst().target().equals(target) && own.getFirst().elapsedMillis()==4567
                && own.getFirst().contributors().equals(List.of("self")),"own mob after shared catch missing");
            check(personalMob.catchSpecies(mob,"Self",5000).isEmpty(),"repeat mob catch overwrote first");
            check(PersonalBestTracker.biome(target)==biome,"mob biome mapping");
        }
        var mobRun=new RunRecord();mobRun.self="Self";mobRun.personalBestSplits.addAll(personalMob.splits());
        var mobDecoded=RunRecord.fromJson(mobRun.toJson());mobDecoded.normalize();
        check(mobDecoded.personalBestSplits.equals(mobRun.personalBestSplits),"mob splits round trip");
        check(PersonalBests.calculate(List.of(mobDecoded)).size()==4,"mob best projection");
        check(!PersonalBestTracker.valid(new PersonalBestTracker.Split(2,"MOB_WUMPA",100,List.of("self","other"),true,-1,-1)),"mob PB accepted multiple contributors");
        var partialMob=new PersonalBestTracker(false,"Self");
        var partial=partialMob.catchSpecies(Critters.byName("Wumpa"),"Self",100);
        check(partial.size()==1 && !PersonalBestTracker.eligible(partial.getFirst()),"partial mob timing qualified");
        check(PersonalBestTracker.comparison(1000,-1,true).equals("First Clear!"),"first clear wording");
        check(PersonalBestTracker.comparison(1000,2000,true).equals("PB! -0:01.00"),"PB wording");
        check(PersonalBestTracker.comparison(1000,1000,true).equals("Tied PB?!"),"tie wording");
        check(PersonalBestTracker.comparison(2000,1000,true).equals("+0:01.00"),"slower wording");
        check(PersonalBestTracker.comparison(1000,-1,false).equals("Timing Error"),"timing wording");
        return 63;
    }
}
