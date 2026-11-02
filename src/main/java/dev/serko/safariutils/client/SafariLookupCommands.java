package dev.serko.safariutils.client;

import dev.serko.safariutils.api.*;
import dev.serko.safariutils.data.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

final class SafariLookupCommands {
    private SafariLookupCommands() { }
    static int lookup(String name,boolean ticketsOnly) { return lookup(name,ticketsOnly?"tickets":"lookup"); }
    static int lookup(String name,String view) {
        boolean ticketsOnly=view.equals("tickets"), missingOnly=view.equals("missing");
        var connection=Minecraft.getInstance().getConnection();
        ClientMessages.send("Looking up "+name+"…",ClientMessages.Tone.INFO);
        PlayerLookupCoordinator.lookup(name).whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            if(connection!=Minecraft.getInstance().getConnection())return;
            if(error!=null){ClientMessages.send(ClientMessages.playerLookupFailure(error),ClientMessages.Tone.ERROR);return;}
            List<Component> lines=new ArrayList<>();
            var heading=Component.literal(ticketsOnly?"Safari Tickets — ":missingOnly?"Missing Sparklings — ":"Safari Profile — ").withColor(StatColours.INFO)
                .append(PlayerNameStyle.component(result.username()));
            if(!ticketsOnly&&!missingOnly) heading.append(Component.literal(" (").withColor(0xFFFFFF))
                .append(Component.literal(LookupPresentation.commandHunting(result)).withColor(StatColours.TIME))
                .append(Component.literal(")").withColor(0xFFFFFF));
            lines.add(heading);
            if(missingOnly) lines.add(LookupPresentation.missingSummary(result));
            else if(!ticketsOnly) {
                lines.add(LookupPresentation.uniqueSummary(result).copy()
                    .append(Component.literal(LookupPresentation.DIVIDER).withColor(0xFFFFFF))
                    .append(LookupPresentation.missingSummary(result)));
                lines.add(LookupPresentation.catchSummary(result));
            }
            if(!missingOnly) lines.add(ticketsOnly?ticketSummary(result):LookupPresentation.ticketTotal(result));
            lines.add(Component.literal("API data • "+Math.max(0,(System.currentTimeMillis()-result.fetchedAt())/60000)+"m old • Saved in Lookup recents").withColor(StatColours.TIME));
            ClientMessages.sendReport(lines);
        })); return 1;
    }
    static Component ticketSummary(SparklingPlayerLookup result) {
        var tickets=Component.literal(String.format(Locale.US,"%,d",result.tickets().values().stream().mapToLong(Long::longValue).sum())).withColor(StatColours.COINS);
        for(int tier=0;tier<LookupPresentation.TICKET_NAMES.length;tier++) {
            tickets.append(Component.literal(LookupPresentation.DIVIDER).withColor(0xFFFFFF));
            tickets.append(Component.literal(String.format(Locale.US,"%,d",result.tickets().getOrDefault(LookupPresentation.TICKET_NAMES[tier],0L)))
                .withColor(LookupPresentation.TICKET_COLOURS[tier]));
        }
        return tickets;
    }
    static boolean sameMembers(List<String> first,List<String> second) {
        if(first.isEmpty()||second.isEmpty())return false;
        var names=new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);names.addAll(first);
        var expected=new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);expected.addAll(second);
        return names.equals(expected);
    }
    static int shared() {
        var party=dev.serko.safariutils.state.PartyState.snapshot();
        if(!party.known()||!party.inParty()) {ClientMessages.send("No current party shared collection",ClientMessages.Tone.INFO);return 1;}
        var snapshot=SharedSparklingProviders.partySnapshot();
        if(!snapshot.apiManaged()||!party.completeRoster()||!sameMembers(snapshot.members(),party.members().stream().map(dev.serko.safariutils.state.PartySnapshot.Member::name).toList())) {
            ClientMessages.send("Shared Sparklings are not loaded for your current party yet",ClientMessages.Tone.INFO);return 1;
        }
        var shared=SparklingMode.shared();
        List<Component> lines=new ArrayList<>();
        lines.add(Component.literal("Shared Sparklings ("+shared.size()+"/"+Critters.total()+")").withColor(StatColours.UNIQUE));
        lines.add(Component.literal("Party: "+String.join(", ",snapshot.members())).withColor(StatColours.INFO));
        if(!snapshot.apiManaged()||SharedSparklingProviders.partyRefreshStatus().error()!=null)
            lines.add(Component.literal("Known collection only — party API data may be incomplete").withColor(0xFFAA55));
        for(var biome:SafariBiome.values()) {
            var species=Critters.inBiome(biome).stream().filter(shared::contains).toList();
            var line=Component.literal(biome.displayName()+": ").withColor(biome.colour());
            for(int i=0;i<species.size();i++) { if(i>0)line.append(Component.literal(", ").withColor(0xFFFFFF));
                var critter=species.get(i); line.append(Component.literal(critter.name()).withColor(critter.rarity().colour())); }
            if(species.isEmpty())line.append(Component.literal("None known").withColor(StatColours.MISSING)); lines.add(line);
        }
        ClientMessages.sendBlock(lines);return 1;
    }
}
