package dev.serko.safariutils.backend;

import java.io.IOException;

public final class AuctionMarketRegression {
    public static int run() throws Exception {
        long now = 2_000_000_000_000L;
        String valid = "{\"schema\":1,\"coverage\":\"kuudra-base-items-v1\",\"status\":\"fresh\",\"observedAt\":"
            + now + ",\"prices\":{\"CRIMSON_HELMET\":{\"price\":12345,\"samples\":2}}}";
        var snapshot = AuctionMarketClient.parse(valid, now);
        if (snapshot.prices().get("CRIMSON_HELMET") != 12345) throw new AssertionError("Quote lost");
        try { snapshot.prices().clear(); throw new AssertionError("Mutable snapshot"); }
        catch (UnsupportedOperationException expected) { }
        int checks = 2;
        for (String invalid : new String[]{valid.replace("12345", "0"), valid.replace("12345", "-1"),
            valid.replace("12345", "1e999"), valid.replace("\"samples\":2", "\"samples\":0"),
            valid.replace("\"samples\":2", "\"samples\":1.5"), valid.replace("\"schema\":1", "\"schema\":1.5"),
            valid.replace("fresh", "unavailable"), valid.replace("CRIMSON_HELMET", "bad id"),
            valid.replace(Long.toString(now), Long.toString(now-31*60000)),
            valid.replace(Long.toString(now), Long.toString(now+60000)), "{}", valid + " true"}) {
            try { AuctionMarketClient.parse(invalid, now); throw new AssertionError("Invalid quote accepted"); }
            catch (IOException expected) { checks++; }
        }
        var empty = AuctionMarketClient.parse("{\"schema\":1,\"coverage\":\"kuudra-base-items-v1\",\"status\":\"unavailable\",\"observedAt\":0,\"prices\":{}}", now);
        if (!empty.prices().isEmpty()) throw new AssertionError("Missing prices became real quotes");
        String historical = "{\"schema\":2,\"coverage\":\"safari-kuudra-v2\",\"status\":\"fresh\",\"observedAt\":" + now
            + ",\"prices\":{\"DYE_EMERALD\":{\"price\":100,\"samples\":2,\"source\":\"sale\",\"confidence\":\"low\",\"observedAt\":"
            + (now-86400000) + ",\"expiresAt\":"+(now+86400000)+"}}}";
        var sale = AuctionMarketClient.parse(historical,now).quotes().get("DYE_EMERALD");
        if (sale==null || !sale.source().equals("sale") || sale.samples()!=2) throw new AssertionError("historical provenance lost");
        for (String invalid : new String[]{historical.replace("\"sale\"", "\"guess\""), historical.replace("\"low\"","\"guaranteed\""),
                historical.replace(Long.toString(now+86400000), Long.toString(now+40L*86400000))}) {
            try { AuctionMarketClient.parse(invalid,now); throw new AssertionError("invalid history accepted"); }
            catch (IOException expected) { checks++; }
        }
        return checks+2;
    }
}
