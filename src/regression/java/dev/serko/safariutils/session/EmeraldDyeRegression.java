package dev.serko.safariutils.session;

import dev.serko.safariutils.parse.ChatParser;
import dev.serko.safariutils.data.Critters;
import com.google.gson.JsonParser;

public final class EmeraldDyeRegression {
    public static int run() {
        if (!ChatParser.ownEmeraldDye("\u00a76WOW! \u00a7b[MVP++] Serkosea found an Emerald Dye!", "serkosea")) throw new AssertionError("rank/color parsing");
        for (String line : new String[] {"WOW! [MVP++] SomeoneElse found an Emerald Dye!",
            "Party > Bob: WOW! Serkosea found an Emerald Dye!", "An unfathomably tiny 1/2.5k (0.04%) chance!", "WOW! Serkosea found an Emerald Dye! extra"})
            if (ChatParser.ownEmeraldDye(line,"Serkosea")) throw new AssertionError("false dye event");
        var session = new SafariSession("Serkosea", 1000);
        session.recordRareDrop("DYE_EMERALD");
        session.recordRareDropValue("DYE_EMERALD",100d,"sale",900,1100);
        session.recordNewSparkling(Critters.byName("Driftling"));
        var record = RunRecord.of(session);
        var decoded = RunRecord.fromJson(record.toJson()); decoded.normalize();
        if (decoded.emeraldDyes()!=1 || decoded.rareDropValues.get("DYE_EMERALD").estimatedTotal()!=100d
            || !decoded.newSparklingSpecies.contains("Driftling")) throw new AssertionError("run persistence");
        var legacy=RunRecord.fromJson(JsonParser.parseString("{\"started\":1,\"futureField\":true}").getAsJsonObject()); legacy.normalize();
        if (legacy.emeraldDyes()!=0 || legacy.newSparklingSpecies!=null || !legacy.toJson().get("futureField").getAsBoolean()) throw new AssertionError("legacy migration");
        var first = new RunRecord(); first.sparklings.add(new RunRecord.SparklingRecord("Driftling","Self",10));
        var duplicate = new RunRecord(); duplicate.newSparklingSpecies=java.util.Set.of();
        duplicate.sparklings.add(new RunRecord.SparklingRecord("Driftling","Self",20));
        var empty = new RunRecord();
        var history=java.util.List.of(first,duplicate,empty);
        if (!SparklingRunIntervals.calculate(history).equals(new SparklingRunIntervals.Intervals(1,2))) throw new AssertionError("duplicate resets unique interval");
        if (!SparklingRunIntervals.calculate(java.util.List.of()).equals(new SparklingRunIntervals.Intervals(-1,-1))) throw new AssertionError("empty history invented intervals");
        if (!SparklingRunIntervals.calculate(java.util.List.of(empty)).equals(new SparklingRunIntervals.Intervals(-1,-1))) throw new AssertionError("catchless history invented intervals");
        if (!SparklingRunIntervals.calculate(java.util.List.of(duplicate)).equals(new SparklingRunIntervals.Intervals(0,-1))) throw new AssertionError("explicit duplicate became unique in partial history");
        duplicate.newSparklingSpecies=java.util.Set.of("Driftling");
        if (SparklingRunIntervals.calculate(history).unique()!=1) throw new AssertionError("recorded unique ignored");
        var roundTrip=RunRecord.fromJson(first.toJson());roundTrip.normalize();
        if (!SparklingRunIntervals.calculate(java.util.List.of(roundTrip)).equals(new SparklingRunIntervals.Intervals(0,0))) throw new AssertionError("legacy intervals changed on restart");
        var multi=new SafariSession("Self",2000);
        multi.recordSparkling(Critters.byName("Driftling"),"Self",2100);
        multi.recordSparkling(Critters.byName("Driftling"),"Self",2200);
        var multiRecord=RunRecord.of(multi);
        var multiDecoded=RunRecord.fromJson(multiRecord.toJson());multiDecoded.normalize();
        if(multiDecoded.sparklings.size()!=2 || multi.sparklings().size()!=1
            || multiDecoded.sparklings.get(0).atMillis()==multiDecoded.sparklings.get(1).atMillis())
            throw new AssertionError("repeated Sparkling occurrences lost or split incorrectly");
        return 14;
    }
}
