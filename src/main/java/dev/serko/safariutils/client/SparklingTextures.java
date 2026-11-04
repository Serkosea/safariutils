package dev.serko.safariutils.client;

import com.google.gson.JsonParser;
import dev.serko.safariutils.data.Critter;
import dev.serko.safariutils.data.Critters;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Local appearance evidence only: never resolves profiles or downloads skins. */
final class SparklingTextures {
    // Texture identifiers from Sparkling Mutuals 4.0.0 (CC0-1.0); see third-party notices.
    private static final Map<String, String> SPECIES = Map.ofEntries(
        Map.entry("bd22408eaeec1a63165074827d29f5e81d370930875a0e4bd1ec532f524b41c0", "Areita"),
        Map.entry("d410c03e8ad671b282c9934723ac889051bad3779d2523e2d6f4a077ab14ae0", "Billygoat"),
        Map.entry("a1865d354533f7083c0e599c303e85c2f6cb4da52d42acfa763a41cc937e3ffc", "Bloodbat"),
        Map.entry("7200177b6df354ed51e0ce60632ababcfcbf77d64f787902cdc4e5d811c10ca1", "Bluebird"),
        Map.entry("141250732d192fe38494d05e0fc30e61fd740568d96d9c702cbeca1bc34e9df6", "Cavernfish"),
        Map.entry("7abe66bc685731ad2d764bee6d2f3b6ff7c0bb041670f64c21620f014204f385", "Chuckwalla"),
        Map.entry("94e547d7e6e49fdb30e6902441b3233a851df28b0ade5d1b2bb07b638484efb7", "Doomspiral"),
        Map.entry("bede15b78c9a066ccef64a90ff670f95fdfca52d40d5879b1d17929bb632cc31", "Driftling"),
        Map.entry("b6739f6e042359625362fe8f91c1f4ea7ecb5c1e4b1cde5c647d91700946d625", "Duplico"),
        Map.entry("9fc70cceb1e084e6e89c49a6076721158bfbba29cf7fd94da6986d46771f1d11", "Flitter"),
        Map.entry("a11d555d393d73c6caec0eee3eb7af47d418538c0d3357a241730855c1ce8f81", "Fluffling"),
        Map.entry("5a2656ec0fd3b43cc3a98993aa1ddc31f6b1dc2d353b42ad2433dbe3d650abe", "Foxtrot"),
        Map.entry("d60388d1be46adf18a244555e0dc3c21052288ae86134ddd813cabf7510b716a", "Gazer"),
        Map.entry("86bd86d90a52c1cf2a1e3e63fbf007fe1f5d5cf2f411f9aa0010843dfdb95790", "Gemzie"),
        Map.entry("6c4536287a96b1fe19453d4e6b0e466a16fb2a0601eb15472f490d2f9b5834fc", "Gimmiegold"),
        Map.entry("92ef500d2b6e140c8c939dc9b9b84af7f85b98794a3615e3b63a088d52f7278e", "Hideonfloor"),
        Map.entry("225fa519252c9893e467e00511edc16d1bca3e4d132d6d8e4ea14d229943e309", "Hideonwall"),
        Map.entry("4afc2c7bbf0b9b132c330acb3a922b1bd52e9bea56b912f3bd247d3025245fa0", "Hideyho"),
        Map.entry("2c8b43e5d07e18a2ed722cf14d2775dc0abe204895435b2ef815bc8c0858a4a2", "Honeybug"),
        Map.entry("57be7316f9c5bd58ea445e19542477a2e66b0627b889794749819392ac68182c", "Litterbug"),
        Map.entry("3b73f011f64203ae05db5e9ee049d630aee3afd3583b91db0e322796bd7b9700", "Macaw"),
        Map.entry("500813039d216532c456fd6b8478b8dcb503831d337f308506a63d1e645c51f9", "Mantis Shrimp"),
        Map.entry("a0ac505ebe7a7842a052454eb6076a34b7c4208512d0c2dc816608aa49aa990", "Nozzlenose"),
        Map.entry("71fa514543f2a39e3933607807d047a4376d013dc1279f7060d041fd2bd88ce0", "Parakeet"),
        Map.entry("d94e62686aee9f8ef75db317308e711df0a385d5b91af6789c51c158e5fce97d", "Polaris"),
        Map.entry("ca2a103d108246de1935dbdf1a284898c5ffdffee18b65ec97eb01ecdc12fad9", "Rockmite"),
        Map.entry("b36d8122078aff4fda8dc51fb29ae4336d64673edfee651d55c7de9291d98136", "Scrappy"),
        Map.entry("d7d1811c43896ba1c358e59347744694287168c6e962eef51bd06b9f443aabfd", "Shuddersquid"),
        Map.entry("de4c266f3964518e6ba754dc1dc88d9d1e47d8a9c139febfbf22310597898a12", "Shyworm"),
        Map.entry("2144fd153b02c872817eedd5e4cd0a31dba17d9dde0c27eb1a4adeab9c5dbcf7", "Snoozle"),
        Map.entry("7039762f11f3207b1e0a0fe56c2ee8a45733bf01c76151ccd425803403bfc80d", "Solsnatcher"),
        Map.entry("861752317bd4b266a3953f83a2e1b6f4050af9328b871a497a9078cf2df44516", "Strongarm"),
        Map.entry("be16b204a423546e7f7127494a7d76f8cb8b270000f0c54f2caf4253fb7ee357", "Treefrog"),
        Map.entry("5f0ccf4a99ac797806ce0982945f3f35414332f9ef082e08638bf11ed2dbad38", "Troodon"),
        Map.entry("f18086a7f9676353040ee3946019b7c68aaa1137b7d03aa3d185466cef6593d6", "Woodchucker"),
        Map.entry("14ee5220dfeb845e495883241908ff8dea380f23a74522b673db3ae2523367ff", "Wumpa")
    );
    private static final Map<String, Optional<Critter>> cache = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Optional<Critter>> entry) { return size() > 512; }
    };
    private SparklingTextures() { }
    static void clear() { cache.clear(); }
    static Critter fromProperty(String encoded) {
        if (encoded == null || encoded.length() > 8192) return null;
        return cache.computeIfAbsent(encoded, key -> Optional.ofNullable(decode(key))).orElse(null);
    }
    private static Critter decode(String encoded) {
        try {
            var root = JsonParser.parseString(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)).getAsJsonObject();
            String url = root.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            var uri = java.net.URI.create(url);
            if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost()) || uri.getQuery() != null || uri.getFragment() != null) return null;
            String path = uri.getPath();
            if (path == null || !path.startsWith("/texture/")) return null;
            return Critters.byName(SPECIES.getOrDefault(path.substring(9), ""));
        } catch (RuntimeException malformed) { return null; }
    }
    static Critter identify(Entity entity) {
        Critter species = null;
        if (entity instanceof Player player) {
            var client = Minecraft.getInstance();
            if (player == client.player || client.getConnection() != null && client.getConnection().getPlayerInfo(player.getUUID()) != null
                || dev.serko.safariutils.state.PartyState.snapshot().containsName(player.getGameProfile().name())) return null;
            for (var property : player.getGameProfile().properties().get("textures")) {
                if (property != null && (species = fromProperty(property.value())) != null) break;
            }
        } else {
            ItemStack stack;
            if (entity instanceof ArmorStand stand) stack = stand.getItemBySlot(EquipmentSlot.HEAD);
            else if (entity instanceof Display.ItemDisplay display) stack = display.getItemStack();
            else return null;
            var profile = stack.get(DataComponents.PROFILE);
            if (profile == null) return null;
            for (var property : profile.partialProfile().properties().get("textures")) {
                if (property != null && (species = fromProperty(property.value())) != null) break;
            }
        }
        return species;
    }
}
