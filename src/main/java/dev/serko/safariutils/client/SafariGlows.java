package dev.serko.safariutils.client;

import dev.serko.safariutils.data.SafariBiome;
import dev.serko.safariutils.session.SessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Read-only glow projection of the existing marker snapshot; never publishes capture identity. */
public final class SafariGlows {
    private static final String MOUND_TEXTURE = "5dbaab74d1acd0abe9d04abe9928725de5d4495fcb63b647228caf6944c20800";
    record ModelAnchor(String species, Vec3 position) { }
    private record Target(CritterEntities.Sighting sighting, BlockPos mound) { }
    private static MarkerRegistry.Snapshot cached;
    private static Map<UUID, Target> targets = Map.of();
    private static Map<UUID, String> diagnosticTargets = Map.of();
    private static final Map<ResolvableProfile, Boolean> profiles = new LinkedHashMap<>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ResolvableProfile, Boolean> eldest) { return size() > 128; }
    };
    private SafariGlows() { }
    static boolean species(String name) {
        return name.equals("Hideonwall") || name.equals("Hideonfloor") || name.equals("Duplico") || name.equals("Bloodbat");
    }
    public static void clear() { cached = null; targets = Map.of(); diagnosticTargets = Map.of(); profiles.clear(); }
    public static int colour(Entity entity) {
        if (entity == null) return 0;
        String type = EntityTypeIds.path(entity);
        if (!type.equals("item_display") && !type.equals("bat") && !type.equals("shulker")) return 0;
        Minecraft client = Minecraft.getInstance();
        if (entity.isRemoved() || !HypixelConnection.active() || !SafariLocation.inside()
            || client.player == null || ClientCompat.hudHidden() || entity.isCurrentlyGlowing()) return 0;
        if (entity.distanceToSqr(client.player) > 200 * 200) return 0;
        MarkerRegistry.Snapshot snapshot = MarkerRegistry.snapshot();
        if (cached != snapshot) rebuild(snapshot);
        Target target = targets.get(entity.getUUID());
        if (target == null) return 0;
        if (target.mound != null) {
            if (!moundsEnabled() || (SafeMode.mounds() && !MoundSpotter.isConfirmedVisible(target.mound))) return 0;
            return Colours.argb(ConfigManager.get().display.moundColour, 0xFFCC7744);
        }
        var sighting = target.sighting;
        if (sighting.mob().isRemoved() || !WaypointRenderer.modelGlowEligible(sighting, snapshot)) return 0;
        return WaypointRenderer.critterHitboxColour(sighting.critter(), SparklingWatch.presentsAsSparkling(sighting));
    }
    private static boolean moundsEnabled() {
        return SessionManager.current() != null && SafariLocation.biome() == SafariBiome.CAVERN
            && ConfigManager.get().display.highlightMounds && !SparklingMode.hideMounds(SessionManager.current());
    }
    private static void rebuild(MarkerRegistry.Snapshot snapshot) {
        cached = snapshot;
        Map<UUID, Target> fresh = new HashMap<>();
        var sightings = snapshot.sightings().stream().filter(s -> species(s.critter().name()) && s.mob() != null).toList();
        for (var sighting : sightings) {
            Entity body = sighting.mob();
            // Interaction boxes and invisible carriers are not the rendered critter model.
            if (EntityTypeIds.is(body, "bat") || EntityTypeIds.is(body, "shulker"))
                fresh.put(body.getUUID(), new Target(sighting, null));
        }
        var anchors = sightings.stream().map(s -> new ModelAnchor(s.critter().name(), s.mob().position())).toList();
        boolean mounds = moundsEnabled();
        if (mounds || !sightings.isEmpty()) for (Entity entity : WorldEntities.current()) {
            if (!(entity instanceof Display.ItemDisplay display) || display.itemRenderState() == null) continue;
            ItemStack item = display.itemRenderState().itemStack();
            if (item.isEmpty()) continue;
            if (mounds && display.getPosRotInterpolationDuration() == 0 && mound(item)) {
                BlockPos pos = MoundSpotter.liveMoundAt(entity.position());
                if (pos != null) fresh.put(entity.getUUID(), new Target(null, pos));
                continue;
            }
            if (!(item.getItem() instanceof net.minecraft.world.item.BlockItem)) continue;
            String itemId = BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
            int owner = uniqueModelOwner(itemId, entity.position(), anchors);
            if (owner >= 0) fresh.put(entity.getUUID(), new Target(sightings.get(owner), null));
        }
        targets = Map.copyOf(fresh);
        if (DebugLog.isEnabled()) {
            Map<UUID, String> described = new HashMap<>();
            fresh.forEach((id, target) -> described.put(id, target.mound != null ? "Mound" : target.sighting.critter().name()));
            if (!described.equals(diagnosticTargets)) {
                diagnosticTargets = Map.copyOf(described);
                StructuredDiagnostics.decision("RENDER/GLOW", "models", described.toString());
            }
        } else diagnosticTargets = Map.of();
    }
    static int uniqueModelOwner(String item, Vec3 position, List<ModelAnchor> anchors) {
        int owner = -1;
        for (int i = 0; i < anchors.size(); i++) {
            ModelAnchor anchor = anchors.get(i);
            if (!modelMatches(anchor.species, item) || !aligned(position, anchor.position)) continue;
            if (owner >= 0) return -1;
            owner = i;
        }
        return owner;
    }
    static boolean aligned(Vec3 visual, Vec3 body) {
        return Math.abs(visual.x - body.x) <= .5 && Math.abs(visual.z - body.z) <= .5
            && Math.abs(visual.y - body.y) <= .75;
    }
    static boolean modelMatches(String species, String item) {
        return switch (species) {
            case "Hideonfloor" -> item.equals("green_shulker_box");
            case "Hideonwall" -> item.equals("purple_shulker_box");
            case "Duplico" -> !item.equals("player_head") && !item.equals("purple_shulker_box") && !item.equals("green_shulker_box")
                && !item.equals("air");
            default -> false;
        };
    }
    private static boolean mound(ItemStack stack) {
        if (!stack.is(Items.PLAYER_HEAD)) return false;
        var profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return false;
        return profiles.computeIfAbsent(profile, key -> {
            for (var property : key.partialProfile().properties().get("textures")) {
                if (property == null || property.value().length() > 8192) continue;
                try {
                    String json = new String(Base64.getDecoder().decode(property.value()), java.nio.charset.StandardCharsets.UTF_8);
                    if (json.contains("/texture/" + MOUND_TEXTURE + "\"")) return true;
                } catch (IllegalArgumentException ignored) { }
            }
            return false;
        });
    }
}
