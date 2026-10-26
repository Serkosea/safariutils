package dev.serko.safariutils.client;

public final class StatColoursRegression {
    public static int run() {
        String text="1,234 Coins | Unique 2/37 | 4:35.5 Played";
        var first=StatColours.formatted(text," | ");
        if(!first.getString().equals(text)) throw new AssertionError("Summary formatting changed text/layout");
        if(first!=StatColours.formatted(text," | ")) throw new AssertionError("Unchanged summary rebuilt");
        if(StatColours.value(StatColours.COINS)==StatColours.COINS) throw new AssertionError("Value and heading have identical color");
        for(int i=0;i<130;i++) StatColours.formatted(i+" Runs"," | ");
        if(first==StatColours.formatted(text," | ")) throw new AssertionError("Summary cache not bounded");
        if(StatColours.summary("3 Emerald Dyes")!=StatColours.DYE || StatColours.summary("9 Catches")!=StatColours.CATCHES)
            throw new AssertionError("Semantic colors diverged");
        if(first.getSiblings().size()!=5 || !first.getSiblings().get(4).getString().equals("4:35.5 Played"))
            throw new AssertionError("Inline stat split into number/label colors");
        String pair="Unique Sparklings  32/37";
        var collection=StatColours.collection(pair," | ");
        if(!collection.getString().equals(pair) || collection.getSiblings().size()!=2
            || collection.getSiblings().get(0).getStyle().getColor().equals(collection.getSiblings().get(1).getStyle().getColor()))
            throw new AssertionError("Collection label/value shades missing");
        if(collection==StatColours.formatted(pair," | ")) throw new AssertionError("Summary styles shared cache key");
        return 8;
    }
}
