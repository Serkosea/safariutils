package dev.serko.safariutils.client;

import dev.serko.safariutils.api.*;
import dev.serko.safariutils.data.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

final class SafariLookupCommands {
    private SafariLookupCommands() { }
    static int lookupSelf(String view) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            ClientMessages.send("Join a server before looking up your profile", ClientMessages.Tone.INFO);
            return 0;
        }
        return lookup(player.getGameProfile().name(), view);
    }
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
    static Component sharedBiomeLine(SafariBiome biome, Set<Critter> shared) {
        var species = Critters.inBiome(biome).stream().filter(shared::contains).toList();
        String heading = biome.displayName() + " (" + species.size() + "/" + Critters.totalIn(biome) + ")";
        var hover = Component.literal(heading).withColor(biome.colour());
        if (species.isEmpty()) hover.append(Component.literal("\nNone shared").withColor(StatColours.MISSING));
        for (var critter : species)
            hover.append(Component.literal("\n" + critter.name()).withColor(critter.rarity().colour()));
        return Component.literal(heading).withColor(biome.colour())
            .withStyle(style -> style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(hover)));
    }

    static int shared() {
        var party=dev.serko.safariutils.state.PartyState.snapshot();
        if(!party.known()) {ClientMessages.send("Party membership is not known yet",ClientMessages.Tone.INFO);return 1;}
        boolean solo = !party.inParty();
        var snapshot=SharedSparklingProviders.partySnapshot();
        if(!solo && (!snapshot.apiManaged()||!party.completeRoster()||!sameMembers(snapshot.members(),party.members().stream().map(dev.serko.safariutils.state.PartySnapshot.Member::name).toList()))) {
            ClientMessages.send("Shared Sparklings are not loaded for your current party yet",ClientMessages.Tone.INFO);return 1;
        }
        var shared = solo ? Critters.all().stream()
            .filter(critter -> dev.serko.safariutils.session.SparklingStats.count(critter) > 0)
            .collect(java.util.stream.Collectors.toSet()) : SparklingMode.shared();
        var names = solo ? List.of(Minecraft.getInstance().getUser().getName()) : snapshot.members();
        List<Component> lines=new ArrayList<>();
        lines.add(Component.literal("Shared Sparklings ("+shared.size()+"/"+Critters.total()+")").withColor(StatColours.UNIQUE));
        var members = Component.literal(solo ? "Player: " : "Party: ").withColor(StatColours.INFO);
        for (int index = 0; index < names.size(); index++) {
            if (index > 0) members.append(Component.literal(", ").withColor(0xFFFFFF));
            members.append(PlayerNameStyle.component(names.get(index)));
        }
        lines.add(members);
        if(!solo && (!snapshot.apiManaged()||SharedSparklingProviders.partyRefreshStatus().error()!=null))
            lines.add(Component.literal("Known collection only — party API data may be incomplete").withColor(0xFFAA55));
        var biomes = Component.empty();
        for (var biome : List.of(SafariBiome.FOREST, SafariBiome.CAVERN, SafariBiome.ICY, SafariBiome.HAUNTED)) {
            if (!biomes.getSiblings().isEmpty())
                biomes.append(Component.literal(LookupPresentation.DIVIDER).withColor(0xFFFFFF));
            biomes.append(sharedBiomeLine(biome, shared));
        }
        lines.add(biomes);
        lines.add(Component.literal("Hover a biome for shared Sparklings").withColor(StatColours.INFO));
        ClientMessages.sendReport(lines);return 1;
    }
}
