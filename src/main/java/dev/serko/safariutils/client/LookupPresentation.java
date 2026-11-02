package dev.serko.safariutils.client;

import dev.serko.safariutils.api.SparklingPlayerLookup;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Shared semantic formatting for command reports and dashboard profile views. */
final class LookupPresentation {
    static final String DIVIDER="  │  ";
    static final String[] TICKET_NAMES={"Basic","Economy","Premium","First Class"};
    static final int[] TICKET_COLOURS={0xFF55FF55,0xFF5599FF,0xFFAA55FF,0xFFFFAA00};
    static Component ticket(SparklingPlayerLookup result,int tier) {
        return Component.literal(TICKET_NAMES[tier]+"  ").withColor(TICKET_COLOURS[tier])
            .append(Component.literal(String.format(Locale.US,"%,d",result.tickets().getOrDefault(TICKET_NAMES[tier],0L))).withColor(TICKET_COLOURS[tier]));
    }
    static String commandHunting(SparklingPlayerLookup result) {
        return "Hunting "+(result.huntingLevel()==null?"Unknown":String.format(Locale.US,"%.2f",result.huntingLevel()));
    }
    static Component ticketTotal(SparklingPlayerLookup result) {
        var breakdown=Component.empty();
        for(int tier=0;tier<TICKET_NAMES.length;tier++) {
            if(tier>0) breakdown.append("\n");
            breakdown.append(ticket(result,tier));
        }
        long total=result.tickets().values().stream().mapToLong(Long::longValue).sum();
        return Component.literal("Tickets "+String.format(Locale.US,"%,d",total)).withColor(StatColours.COINS)
            .withStyle(style->style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(breakdown)));
    }
    static Component uniqueSummary(SparklingPlayerLookup result) { return speciesSummary(result,false); }
    static Component missingSummary(SparklingPlayerLookup result) { return speciesSummary(result,true); }
    private static Component speciesSummary(SparklingPlayerLookup result,boolean missing) {
        var hover=Component.empty();
        for(var biome:dev.serko.safariutils.data.SafariBiome.values()) {
            if(!hover.getSiblings().isEmpty()) hover.append("\n\n");
            var species=dev.serko.safariutils.data.Critters.inBiome(biome).stream().filter(c->missing!=result.owns(c.name())).toList();
            hover.append(Component.literal(biome.displayName()+" ("+species.size()+"/"+dev.serko.safariutils.data.Critters.totalIn(biome)+")").withColor(biome.colour()));
            if(species.isEmpty()) hover.append(Component.literal(missing?"\nNone missing":"\nNone collected").withColor(StatColours.MISSING));
            if(!species.isEmpty()) hover.append("\n");
            for(int index=0;index<species.size();index++) {
                if(index>0) hover.append(Component.literal(", ").withColor(0xFFFFFF));
                var critter=species.get(index);
                hover.append(Component.literal(critter.name()).withColor(critter.rarity().colour()));
            }
        }
        int count=dev.serko.safariutils.data.Critters.all().stream().mapToInt(c->missing!=result.owns(c.name())?1:0).sum();
        return (missing?Component.literal("Missing Sparklings  "+count).withColor(StatColours.PERSONAL_BEST)
            :StatColours.collection("Unique Sparklings  "+count+"/"+dev.serko.safariutils.data.Critters.total(),DIVIDER).copy())
            .withStyle(style->style.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(hover)));
    }
    static Component catchSummary(SparklingPlayerLookup result) {
        String duplicates=result.duplicates()<0?"Unknown":String.valueOf(result.duplicates());
        String total=result.duplicates()<0?"Unknown":String.valueOf(result.species().size()+result.duplicates());
        return Component.empty().append(StatColours.collection("Sparklings  "+total,DIVIDER))
            .append(Component.literal(DIVIDER).withColor(0xFFFFFF))
            .append(StatColours.collection("Duplicates  "+duplicates,DIVIDER));
    }
    static Component summary(SparklingPlayerLookup result) {
        String duplicates=result.duplicates()<0?"Unknown":String.valueOf(result.duplicates());
        String total=result.duplicates()<0?"Unknown":String.valueOf(result.species().size()+result.duplicates());
        return StatColours.collection("Unique Sparklings  "+result.species().size()+"/"+dev.serko.safariutils.data.Critters.total()+"  │  Sparklings  "+total+"  │  Duplicates  "+duplicates,"  │  ");
    }
}
