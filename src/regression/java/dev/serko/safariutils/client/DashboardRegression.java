package dev.serko.safariutils.client;

import dev.serko.safariutils.session.RunRecord;
import java.util.List;

public final class DashboardRegression {
    public static int run() {
        if(!java.util.Arrays.stream(DashboardNavigation.Tab.values()).map(t->t.label).toList()
            .equals(List.of("Stats","History","Profit","Sparklings","Lookup","Personal Bests")))throw new AssertionError("dashboard tab order");
        RunRecord old=new RunRecord();old.started=99;
        RunRecord archived=new RunRecord();archived.started=101;archived.totalShards=5;
        RunRecord live=new RunRecord();live.started=101;live.totalShards=8;
        var runs=SessionProfitSummary.sessionRuns(List.of(old,archived),live,100);
        if(runs.size()!=1||runs.getFirst()!=live)throw new AssertionError("live run must replace archived copy, not double count");
        if(SessionProfitSummary.sessionRuns(List.of(old,archived),null,100).getFirst()!=archived)throw new AssertionError("completed session run retained");
        if(!SessionProfitSummary.sessionRuns(List.of(old),null,100).isEmpty())throw new AssertionError("previous launch excluded");
        var profile=new dev.serko.safariutils.api.SparklingPlayerLookup("Test",java.util.Set.of(),0,
            java.util.Map.of("Basic",1234L,"Economy",2L,"Premium",3L,"First Class",4L),1);
        String tickets=SafariLookupCommands.ticketSummary(profile).getString();
        if(!tickets.equals("1,243  │  1,234  │  2  │  3  │  4"))
            throw new AssertionError("command report must use canonical ticket keys and readable totals");
        if(!SafariLookupCommands.sameMembers(List.of("Alice","BOB"),List.of("bob","ALICE")))throw new AssertionError("party display order and casing irrelevant");
        if(SafariLookupCommands.sameMembers(List.of("Alice","Bob"),List.of("Alice","Charlie")))throw new AssertionError("old party list must not appear as current");
        if(SafariLookupCommands.sameMembers(List.of(),List.of()))throw new AssertionError("empty roster is unknown");
        var owned=new dev.serko.safariutils.api.SparklingPlayerLookup("Test",java.util.Set.of("MANTIS_SHRIMP","WUMPA"),3,java.util.Map.of(),1,42.5);
        if(!owned.owns("Mantis Shrimp")||!owned.owns("Wumpa")||owned.owns("Gemzie"))throw new AssertionError("API species IDs match display names");
        if(!LookupPresentation.commandHunting(owned).endsWith("42.50"))throw new AssertionError("hunting level formatting");
        if(!LookupPresentation.commandHunting(profile).endsWith("Unknown"))throw new AssertionError("older profiles must not invent level zero");
        var day1=new RunRecord(); day1.started=java.time.Instant.parse("2026-11-01T01:00:00Z").toEpochMilli(); day1.ended=day1.started+60000;day1.totalShards=10;
        var day2=new RunRecord(); day2.started=day1.started+3600000;day2.ended=day2.started+60000;day2.totalShards=20;
        var days=SavedProfitSummary.group(List.of(day1,day2),java.time.ZoneOffset.UTC,run->run.totalShards);
        if(days.size()!=1||days.getFirst().runs()!=2||days.getFirst().value()!=30||days.getFirst().best()!=20||days.getFirst().perHour()!=900)
            throw new AssertionError("saved daily totals and rates");
        java.util.List<Integer> colours=new java.util.ArrayList<>();
        LookupPresentation.ticket(profile,0).visit((style,text)->{if(!text.isEmpty())colours.add(style.getColor().getValue());return java.util.Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        if(!colours.equals(List.of(0x55FF55,0x55FF55)))throw new AssertionError("ticket label and number tier colored");
        var unique=LookupPresentation.uniqueSummary(owned);
        if(!unique.getString().equals("Unique Sparklings  2/37") || unique.getStyle().getHoverEvent()==null)
            throw new AssertionError("unique count has biome hover rather than inline listing");
        if(!LookupPresentation.catchSummary(owned).getString().equals("Sparklings  5  │  Duplicates  3"))
            throw new AssertionError("catch summary is separate from unique count");
        SafariLookupCommands.ticketSummary(profile).visit((style,text)->{
            if(text.contains("│") && (style.getColor()==null || style.getColor().getValue()!=0xFFFFFF))
                throw new AssertionError("ticket dividers must be white");
            return java.util.Optional.empty();
        },net.minecraft.network.chat.Style.EMPTY);
        if(!LookupPresentation.commandHunting(owned).equals("Hunting 42.50"))throw new AssertionError("compact command hunting format");
        var ticketTotal=LookupPresentation.ticketTotal(profile);
        if(!ticketTotal.getString().equals("Tickets 1,243") || ticketTotal.getStyle().getColor().getValue()!=(StatColours.COINS&0xFFFFFF)
            || !(ticketTotal.getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText hover)
            || !hover.value().getString().equals("Basic  1,234\nEconomy  2\nPremium  3\nFirst Class  4"))
            throw new AssertionError("lookup ticket total has named tier breakdown on hover");
        var eye=new net.minecraft.world.phys.Vec3(0,10,0);
        var aim=new net.minecraft.world.phys.Vec3(1,0,0);
        var point=dev.serko.safariutils.state.CapsulePath.point(eye,aim,2);
        if(Math.abs(point.x-1.6964964499885896)>1e-12||Math.abs(point.y-9.97991444668882)>1e-12)
            throw new AssertionError("measured capsule origin and gravity");
        if(dev.serko.safariutils.state.CapsulePath.point(eye,net.minecraft.world.phys.Vec3.ZERO,2)!=null
            ||dev.serko.safariutils.state.CapsulePath.point(eye,aim,Double.NaN)!=null)throw new AssertionError("invalid trajectory inputs rejected");
        var body=new net.minecraft.world.phys.AABB(2,0,0,3,1,1);
        if(dev.serko.safariutils.state.CapsulePath.contact(body,new net.minecraft.world.phys.Vec3(0,1.2,.5),new net.minecraft.world.phys.Vec3(4,1.2,.5))==null
            ||dev.serko.safariutils.state.CapsulePath.contact(body,new net.minecraft.world.phys.Vec3(0,1.3,.5),new net.minecraft.world.phys.Vec3(4,1.3,.5))!=null)
            throw new AssertionError("capsule volume catches grazing targets without unlimited expansion");
        for(var normal:java.util.List.of(new net.minecraft.world.phys.Vec3(0,1,0),new net.minecraft.world.phys.Vec3(1,0,0))) {
            var ring=dev.serko.safariutils.state.CapsulePath.ring(eye,normal);
            if(!ring.getFirst().equals(ring.getLast()))throw new AssertionError("closed landing ring");
            for(var vertex:ring)if(Math.abs(vertex.subtract(eye).dot(normal)-.015)>1e-9)
                throw new AssertionError("landing ring follows terrain face");
        }
        var unlit=net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
            .filter(block -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath().equals("red_candle"))
            .findFirst().orElseThrow().defaultBlockState();
        if(!CandleTarget.unlit(unlit) || CandleTarget.unlit(unlit.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,true)))
            throw new AssertionError("lit candle disables both outline and expanded interaction");
        var camera=net.minecraft.world.phys.Vec3.ZERO;
        var far=new net.minecraft.world.phys.Vec3(1000,100,100);
        double projection=dev.serko.safariutils.state.WorldOverlayProjection.scale(far,camera);
        var projected=dev.serko.safariutils.state.WorldOverlayProjection.project(new net.minecraft.world.phys.AABB(far,far.add(1,1000,1)),camera,projection);
        if(Math.abs(projected.minX/projected.minZ-10)>1e-9||projection>=1)throw new AssertionError("far overlay retains viewing direction without distance cutoff");
        var missing=LookupPresentation.missingSummary(owned);
        if(!missing.getString().equals("Missing Sparklings  35")
            || !(missing.getStyle().getHoverEvent() instanceof net.minecraft.network.chat.HoverEvent.ShowText missingHover)
            || !missingHover.value().getString().contains("Gemzie") || missingHover.value().getString().contains("Wumpa"))
            throw new AssertionError("missing species hover is the complement of collected species");
        var farPoint=dev.serko.safariutils.state.CapsulePath.point(eye,aim,60);
        if(farPoint==null || farPoint.x<50 || !Double.isFinite(farPoint.y))throw new AssertionError("extended trajectory beyond short calibration range");
        for(int n=1;n<100;n++) {
            var previous=dev.serko.safariutils.state.CapsulePath.point(eye,aim,n-1);
            var middle=dev.serko.safariutils.state.CapsulePath.point(eye,aim,n);
            var next=dev.serko.safariutils.state.CapsulePath.point(eye,aim,n+1);
            if(Math.abs(next.y-2*middle.y+previous.y+.020085553311180723)>1e-10)
                throw new AssertionError("continuous parabola curvature");
        }
        var report=ClientMessages.reportLines(List.of(net.minecraft.network.chat.Component.literal("Title"),LookupPresentation.missingSummary(owned)));
        if(report.size()!=4 || !report.get(1).getString().equals("Title") || !report.get(2).getString().startsWith("•  Missing")
            || report.getFirst().getStyle().getColor().getValue()!=0xFFD45A || !report.getFirst().getString().equals(report.getLast().getString()))
            throw new AssertionError("API report has unprefixed heading, gold rules and body bullets");
        var framed = ClientMessages.reportMessage(List.of(net.minecraft.network.chat.Component.literal("Title"), LookupPresentation.missingSummary(owned)));
        if (!report.getFirst().getStyle().isStrikethrough() || report.getFirst().getString().length() != 28
            || !framed.getString().equals(report.stream().map(net.minecraft.network.chat.Component::getString)
                .collect(java.util.stream.Collectors.joining("\n"))))
            throw new AssertionError("short continuous rules remain in one atomic report message");
        return 26;
    }
}
