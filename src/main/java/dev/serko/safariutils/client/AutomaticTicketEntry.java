package dev.serko.safariutils.client;

import dev.serko.safariutils.BuildVersion;
import dev.serko.safariutils.session.SessionManager;
import dev.serko.safariutils.state.MonotonicTime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** One bounded, cancellable approach per visit. Never changes position, reach or physical keys. */
public final class AutomaticTicketEntry {
    private enum Phase { IDLE, APPROACH, MENU, FINISHED }
    private static final Vec3 MANAGER = SafariManagerTarget.POSITION;
    private static Phase phase = Phase.IDLE;
    private static Object world;
    private static long visit, started, menuStarted, candidateSince, lastProgress;
    private static Entity manager;
    private static String waitingReason = "";
    private static Vec3 progressPosition;
    private static float expectedYaw, expectedPitch, forward;
    private static final TicketTurnSmoother turn = new TicketTurnSmoother();
    private static long lastViewUpdate, walkingSince, lastInteraction;
    private static int tier, pendingTest, interactionAttempts;
    private static boolean testing, blockSelection, menuSeen;
    private static int appliedMovementTicks;
    private static long lastMovementInput;
    private static float appliedMovementEffort;
    private AutomaticTicketEntry() { }

    static boolean available() { return BuildVersion.has(BuildVersion.Capability.SAFE_MODE_CONTROLS); }

    static boolean inWindow(long remaining) { return remaining > 1500; }
    static float approachSpeed(double remainingDistance, long elapsed) {
        if (remainingDistance <= 0) return 0;
        double progress = Math.min(1.0, Math.max(0.0, elapsed / 200.0));
        double ramp = progress * progress * (3 - 2 * progress);
        return (float) (Math.min(1, Math.max(.25, remainingDistance * 1.2)) * ramp);
    }
    static float headingSpeed(double yawError) {
        double alignment = Math.clamp((Math.cos(Math.toRadians(yawError)) - .5) * 2, 0, 1);
        return (float) (alignment * alignment * (3 - 2 * alignment));
    }
    static boolean retryDue(int attempts, long elapsed, boolean screenOpen) {
        return !screenOpen && attempts > 0 && attempts < 3 && elapsed >= 450;
    }
    private static boolean sparkling() {
        return PartySparklingDiscoveries.activeMask() != 0 || SparklingWatch.hasOutstanding();
    }
    private static boolean eligible(Minecraft client) {
        return available() && HypixelConnection.active() && SafariLocation.inside() && client.player != null
            && client.level != null && client.gameMode != null && SessionManager.current() == null
            && SparklingWatch.discoveryAllowed(dev.serko.safariutils.state.SafariRuntimeState.current().phase())
            && inWindow(SessionManager.ticketWindowRemainingMillis());
    }
    public static int test(int requested) {
        Minecraft client = Minecraft.getInstance();
        if (!BuildVersion.has(BuildVersion.Capability.DEVELOPER_TOOLS) || !eligible(client)
            || requested < 1 || requested > 4 || client.player.position().distanceTo(MANAGER) > 8) {
            ClientMessages.send("Enter Safari spawn without using a ticket to test automatic entry", ClientMessages.Tone.ERROR);
            return 0;
        }
        stop("new test");
        world = client.level; visit = SafariLocation.insideSinceMillis();
        blockSelection = false; pendingTest = requested; phase = Phase.IDLE; candidateSince = 0; waitingReason = "";
        DebugLog.line("TICKET/AUTO", "debug armed preferred=" + requested + " remaining=" + SessionManager.ticketWindowRemainingMillis());
        ClientMessages.send("Automatic entry test armed; this consumes a real ticket. Release movement/action keys", ClientMessages.Tone.INFO);
        return 1;
    }
    static boolean blocksSelection() { return available() && blockSelection; }
    static boolean choosingTicket() { return available() && phase == Phase.MENU; }
    /** Only suppress automatic focus-loss menus; never change the user's pause setting or Escape handling. */
    public static boolean keepsBackgroundEntryActive() {
        if (!available() || phase == Phase.FINISHED) return false;
        if (phase == Phase.IDLE && pendingTest == 0
            && (!ConfigManager.get().gameplay.autoSparklingTicket || !sparkling())) return false;
        Minecraft client = Minecraft.getInstance();
        return client != null && eligible(client);
    }
    static int entryPreference(int modePreference) { return modePreference >= 1 && modePreference <= 4 ? modePreference : 1; }
    static int requestedTier(int normal) { return choosingTicket() ? testing ? tier : entryPreference(normal) : normal; }
    static void unavailable() { if (choosingTicket()) stop("no tickets available"); }
    static void submitting() { if (phase == Phase.MENU) stop("ticket submitted"); }
    public static void manualOverride() { stop("manual input"); }
    private static void stop(String reason) {
        Minecraft client = Minecraft.getInstance();
        if (phase == Phase.APPROACH && client != null && client.player != null)
            logMovement(client, MonotonicTime.nowMillis(), "approach ended: " + reason);
        forward = 0;
        if (phase == Phase.MENU && !reason.equals("ticket submitted")) blockSelection = true;
        if (phase == Phase.APPROACH || phase == Phase.MENU || pendingTest != 0) {
            DebugLog.line("TICKET/AUTO", "stopped: " + reason);
            if (!reason.equals("ticket submitted") && !reason.equals("ticket activated"))
                ClientMessages.send("Automatic ticket entry stopped: " + reason + ". Use the Manager manually", ClientMessages.Tone.INFO);
        }
        phase = Phase.FINISHED; manager = null; pendingTest = 0; waitingReason = "";
    }
    private static void waitFor(String reason) {
        if (!reason.equals(waitingReason)) {
            waitingReason = reason;
            DebugLog.line("TICKET/AUTO", "waiting for " + reason + " test=" + (pendingTest != 0));
        }
    }
    public static void tick() {
        if (!available()) return;
        try { tickEntry(); }
        catch (RuntimeException failure) { stop("interaction unavailable"); throw failure; }
    }
    private static void tickEntry() {
        Minecraft client = Minecraft.getInstance();
        long currentVisit = SafariLocation.insideSinceMillis(), now = MonotonicTime.nowMillis();
        if (world != client.level || visit != currentVisit) {
            if (phase == Phase.APPROACH || phase == Phase.MENU || pendingTest != 0) stop("Safari visit changed");
            forward = 0; blockSelection = false; phase = Phase.IDLE; manager = null; pendingTest = 0; candidateSince = 0;
            world = client.level; visit = currentVisit;
        }
        if (phase == Phase.FINISHED) return;
        if (!eligible(client)) {
            if (phase != Phase.IDLE || pendingTest != 0) stop(SessionManager.current() != null ? "ticket activated" : "entry window ended");
            return;
        }
        if (phase == Phase.IDLE) {
            boolean test = pendingTest != 0;
            if (!test && (!ConfigManager.get().gameplay.autoSparklingTicket || !sparkling())) { candidateSince = 0; return; }
            if (ClientCompat.screen() != null && !TicketProtection.isEntryScreen(ClientCompat.screen())) {
                // Close through the screen's own lifecycle so server containers are notified.
                // Never close a container while an item is attached to the cursor.
                if (!client.player.containerMenu.getCarried().isEmpty()) { waitFor("held cursor item to be placed"); return; }
                ClientCompat.screen().onClose();
                candidateSince = 0; return;
            }
            if (manualKeys(client)) {
                waitFor("movement/action keys to be released");
                candidateSince = 0; return;
            }
            if (candidateSince == 0) { candidateSince = now; return; }
            // Briefly settle a newly loaded world/snapshot, without waiting for another player's alert.
            if (now - candidateSince < 350) return;
            testing = test; tier = test ? pendingTest : entryPreference(TicketSelection.preference(ConfigManager.get().gameplay, RunMode.current()));
            if (!safePlayer(client) || client.player.position().distanceTo(MANAGER) > 8) { stop("outside the safe spawn approach"); return; }
            manager = SafariManagerTarget.find(client);
            if (manager == null) {
                // Preserve a debug request until both the NPC body and its separate label arrive.
                waitFor("unambiguous Manager body and label");
                if (now - candidateSince > 2500) {
                    DebugLog.line("TICKET/AUTO", "Manager lookup timed out: " + SafariManagerTarget.describe(client));
                    stop("Manager not identified");
                }
                return;
            }
            pendingTest = 0; waitingReason = "";
            progressPosition = client.player.position();
            started = lastProgress = now; expectedYaw = client.player.getYRot(); expectedPitch = client.player.getXRot();
            turn.reset(expectedYaw, expectedPitch); lastViewUpdate = System.nanoTime(); walkingSince = 0;
            interactionAttempts = 0; lastInteraction = 0;
            appliedMovementTicks = 0; appliedMovementEffort = 0; lastMovementInput = 0;
            phase = Phase.APPROACH;
            if (TicketProtection.isEntryScreen(ClientCompat.screen())) {
                phase = Phase.MENU; menuSeen = true; menuStarted = now;
            }
            DebugLog.line("TICKET/AUTO", "approach tier=" + tier + " test=" + testing);
        }
        if (!testing && (!ConfigManager.get().gameplay.autoSparklingTicket || !sparkling())) { stop("Sparkling entry changed"); return; }
        if (manualKeys(client)) { stop("manual input"); return; }
        client.player.setSprinting(false);
        if (phase == Phase.APPROACH && now - started >= 3000) { stop("Manager approach timed out after 3 seconds"); return; }
        if (phase == Phase.APPROACH && ClientCompat.screen() != null) {
            forward = 0;
            walkingSince = 0; appliedMovementEffort = 0; lastProgress = now;
            if (!client.player.containerMenu.getCarried().isEmpty()) { waitFor("held cursor item to be placed"); return; }
            ClientCompat.screen().onClose();
            return;
        }
        if (phase == Phase.APPROACH) updateView();
        if (phase == Phase.MENU) {
            forward = 0;
            if (TicketProtection.isEntryScreen(ClientCompat.screen())) menuSeen = true;
            else if (menuSeen) { stop("ticket menu closed"); return; }
            if (now - menuStarted > 5500) { stop("ticket menu timed out"); return; }
            if (ClientCompat.screen() != null && !TicketProtection.isEntryScreen(ClientCompat.screen())) { stop("another screen opened"); return; }
            if (ClientCompat.screen() == null && (Math.abs(net.minecraft.util.Mth.wrapDegrees(client.player.getYRot() - expectedYaw)) > 1.5
                || Math.abs(client.player.getXRot() - expectedPitch) > 1.5)) { stop("camera moved"); return; }
            if (retryDue(interactionAttempts, now - lastInteraction, ClientCompat.screen() != null)) interactWithManager(client, now);
            return;
        }
        if (manager == null || manager.isRemoved()) { stop("Manager no longer loaded"); return; }
        if (!validPlayerState(client)) { stop("player is dead, riding, flying or in liquid"); return; }
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(client.player.getYRot() - expectedYaw)) > 1.5
            || Math.abs(client.player.getXRot() - expectedPitch) > 1.5) { stop("camera moved"); return; }
        Vec3 eye = client.player.getEyePosition(), aim = manager.getBoundingBox().getCenter(), delta = aim.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        double distance = eye.distanceTo(aim), reach = Math.max(0, client.player.entityInteractionRange() - .2);
        var obstruction = client.level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        if (obstruction.getType() != HitResult.Type.MISS) { stop("Manager is obstructed"); return; }
        // A valid view ray is enough: do not wait for the camera spring to stop moving.
        if (interactWithManager(client, now) || phase == Phase.FINISHED) return;
        float heading = headingSpeed(net.minecraft.util.Mth.wrapDegrees(yaw - expectedYaw));
        if (heading > 0 && distance > reach) {
            if (walkingSince == 0) walkingSince = now;
            forward = approachSpeed(distance - reach, now - walkingSince) * heading;
        } else { walkingSince = 0; forward = 0; }
        if (forward > 0) {
            // Walking overlaps turning, so validate where the player is actually facing.
            double radians = Math.toRadians(client.player.getYRot());
            Vec3 direction = new Vec3(-Math.sin(radians), 0, Math.cos(radians));
            var sweptBox = client.player.getBoundingBox().expandTowards(direction.scale(.45));
            if (!client.level.noCollision(client.player, sweptBox)) { stop("walking path is obstructed"); return; }
            if (client.player.position().distanceTo(progressPosition) > .04) {
                progressPosition = client.player.position(); lastProgress = now; appliedMovementEffort = 0;
            } else if (movementStalled(appliedMovementEffort)) {
                stop("no movement progress"); return;
            }
        } else { lastProgress = now; appliedMovementEffort = 0; }
    }
    /** Bounded retries target only the NPC; TicketSelection still submits at most once. */
    private static boolean interactWithManager(Minecraft client, long now) {
        if (manager == null || manager.isRemoved() || !safePlayer(client)) { stop("Manager interaction interrupted"); return false; }
        Vec3 eye = client.player.getEyePosition();
        double reach = Math.max(0, client.player.entityInteractionRange() - .2);
        Vec3 endpoint = eye.add(client.player.getViewVector(1).scale(reach));
        Vec3 hit = manager.getBoundingBox().clip(eye, endpoint).orElse(null);
        if (hit == null) return false;
        var obstruction = client.level.clip(new ClipContext(eye, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player));
        if (obstruction.getType() != HitResult.Type.MISS) { stop("Manager is obstructed"); return false; }
        if (TicketProtection.shouldBlock()) { stop("waiting for party"); return false; }
        for (var entity : client.level.getEntities(client.player, new AABB(eye, hit).inflate(.05))) {
            if (entity == manager || entity instanceof net.minecraft.world.entity.player.Player || !entity.isPickable()) continue;
            var blockingHit = entity.getBoundingBox().clip(eye, hit);
            if (blockingHit.isPresent() && eye.distanceToSqr(blockingHit.get()) < eye.distanceToSqr(hit)) {
                waitFor("entity in front of the Manager to move"); return false;
            }
        }
        forward = 0;
        if (phase != Phase.MENU) { phase = Phase.MENU; menuStarted = now; menuSeen = false; }
        lastInteraction = now; interactionAttempts++;
        // Vanilla reach and interaction path; menu arrival or ticket activation ends retries.
        client.gameMode.interact(client.player, manager, new EntityHitResult(manager, hit), InteractionHand.MAIN_HAND);
        if (interactionAttempts == 1) StartingItemsWatch.onTicketSubmitted("automatic Manager interaction");
        DebugLog.line("TICKET/AUTO", "Manager interacted attempt=" + interactionAttempts + " approachMs=" + (now - started) + "; awaiting verified ticket menu");
        return true;
    }
    /** Smooth view motion after vanilla processes the mouse, independent of the 20 Hz action tick. */
    public static void updateView() {
        if (!available() || phase != Phase.APPROACH || ClientCompat.screen() != null) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level != world || !eligible(client)
            || ClientCompat.screen() != null || manager == null || manager.isRemoved() || manualKeys(client)) {
            stop("view input or context changed"); return;
        }
        if (Math.abs(net.minecraft.util.Mth.wrapDegrees(client.player.getYRot() - expectedYaw)) > 1.5
            || Math.abs(client.player.getXRot() - expectedPitch) > 1.5) { stop("camera moved"); return; }
        long now = System.nanoTime();
        double seconds = Math.max(0, now - lastViewUpdate) / 1_000_000_000.0;
        lastViewUpdate = now;
        Vec3 delta = manager.getBoundingBox().getCenter().subtract(client.player.getEyePosition());
        double yaw = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        double pitch = -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));
        turn.update(yaw, pitch, seconds);
        expectedYaw = turn.yaw(); expectedPitch = turn.pitch();
        client.player.setYRot(expectedYaw); client.player.setXRot(expectedPitch);
    }
    private static boolean safePlayer(Minecraft client) {
        return validPlayerState(client);
    }
    private static boolean validPlayerState(Minecraft client) {
        var player = client.player;
        return player != null && player.isAlive() && !player.isPassenger()
            && !player.isInWater() && !player.isInLava() && !player.getAbilities().flying;
    }
    private static boolean manualKeys(Minecraft client) {
        var options = client.options;
        return options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown() || options.keyRight.isDown()
            || options.keyJump.isDown() || options.keyShift.isDown()
            || options.keyAttack.isDown() || options.keyUse.isDown();
    }
    /** Called after vanilla reads physical keys; never overwrites a player's own input. */
    static boolean idleInput(Input physical, Vec2 movement) {
        return physical != null && !physical.forward() && !physical.backward() && !physical.left() && !physical.right()
            && !physical.jump() && !physical.shift() && movement != null && movement.x == 0 && movement.y == 0;
    }
    // Count actual movement ticks, not background frame delays or barely-aligned turning.
    static boolean movementStalled(float effort) { return effort >= 14; }
    private static void logMovement(Minecraft client, long now, String reason) {
        var input = client.player.input;
        var movement = input.getMoveVector();
        DebugLog.line("TICKET/AUTO", reason + " focused=" + client.isWindowActive()
            + " input=" + input.getClass().getSimpleName() + " requested=" + forward
            + " actual=" + movement.x + "," + movement.y + " appliedTicks=" + appliedMovementTicks
            + " effort=" + appliedMovementEffort + " inputAgeMs=" + (lastMovementInput == 0 ? -1 : now - lastMovementInput)
            + " progressAgeMs=" + (now - lastProgress));
    }
    public static float forwardInput(Input physical, Vec2 movement) {
        if (!available()) return 0;
        Minecraft client = Minecraft.getInstance();
        if (phase != Phase.APPROACH || forward <= 0 || ClientCompat.screen() != null) return 0;
        if (!idleInput(physical, movement) || client == null || ClientCompat.screen() != null || !eligible(client)) {
            stop("manual input or context changed"); return 0;
        }
        lastMovementInput = MonotonicTime.nowMillis();
        appliedMovementEffort += forward;
        if (appliedMovementTicks++ == 0) logMovement(client, lastMovementInput, "movement input started");
        return forward;
    }
}
