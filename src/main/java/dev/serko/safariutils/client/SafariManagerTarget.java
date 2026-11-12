package dev.serko.safariutils.client;

import dev.serko.safariutils.state.PartyState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Resolves the Manager's clickable body from its separate server-provided label. */
final class SafariManagerTarget {
    static final Vec3 POSITION = new Vec3(-51.5, 68, 21.5);
    record Candidate(int id, Vec3 position, String name, boolean player, boolean interaction, boolean participant) { }
    private SafariManagerTarget() { }

    // Hypixel briefly lists version-2 NPC profiles while delivering their skins.
    // A real roster member is still excluded regardless of its UUID version.
    static boolean participant(UUID id, boolean local, boolean partyMember, boolean listed) {
        return local || partyMember || listed && id.version() != 2;
    }

    static Integer choose(List<Candidate> candidates) {
        var labels = candidates.stream().filter(candidate ->
            TicketSelection.plain(candidate.name()).equalsIgnoreCase("Safari Manager")).toList();
        Integer player = null, interaction = null;
        int players = 0, interactions = 0;
        for (Candidate body : candidates) {
            if (body.participant() || (!body.player() && !body.interaction())) continue;
            boolean matched = labels.stream().anyMatch(label -> {
                Vec3 offset = label.position().subtract(body.position());
                return Math.hypot(offset.x, offset.z) < .6 && offset.y >= 0 && offset.y <= 3.5;
            });
            if (!matched) continue;
            if (body.player()) { player = body.id(); players++; }
            else { interaction = body.id(); interactions++; }
        }
        // Do not guess between two player bodies, even when an interaction proxy exists.
        return players > 0 ? players == 1 ? player : null : interactions == 1 ? interaction : null;
    }

    private static List<Candidate> nearby(Minecraft client) {
        if (client.level == null || client.player == null || client.getConnection() == null) return List.of();
        var participants = new HashSet<UUID>();
        participants.add(client.player.getUUID());
        for (var info : client.getConnection().getListedOnlinePlayers()) participants.add(info.getProfile().id());
        var party = PartyState.snapshot();
        var result = new ArrayList<Candidate>();
        for (Entity entity : client.level.getEntities(client.player, new AABB(POSITION, POSITION).inflate(2, 4, 2))) {
            if (entity.isRemoved()) continue;
            String name = entity.getName().getString();
            String uuid = entity.getUUID().toString().replace("-", "");
            boolean participant = participant(entity.getUUID(), entity.getUUID().equals(client.player.getUUID()),
                entity instanceof Player && (party.containsName(name)
                    || party.members().stream().anyMatch(member -> member.uuid().equals(uuid))),
                participants.contains(entity.getUUID()));
            result.add(new Candidate(entity.getId(), entity.position(), name, entity instanceof Player,
                EntityTypeIds.is(entity, "interaction"), participant));
        }
        return result;
    }
    static Entity find(Minecraft client) {
        Integer id = choose(nearby(client));
        return id == null || client.level == null ? null : client.level.getEntity(id);
    }
    static String describe(Minecraft client) {
        var candidates = nearby(client);
        return "nearby=" + candidates.size() + " labels=" + candidates.stream().filter(c ->
            TicketSelection.plain(c.name()).equalsIgnoreCase("Safari Manager")).count()
            + " playerBodies=" + candidates.stream().filter(c -> c.player() && !c.participant()).count()
            + " excludedPlayers=" + candidates.stream().filter(c -> c.player() && c.participant()).count()
            + " interactionBodies=" + candidates.stream().filter(Candidate::interaction).count();
    }
}
