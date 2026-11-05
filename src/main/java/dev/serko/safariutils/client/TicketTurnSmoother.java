package dev.serko.safariutils.client;

/** Frame-rate independent, critically damped aiming; no random offsets or fixed-speed plateau. */
final class TicketTurnSmoother {
    private static final double RESPONSE = 14.0;
    private double yaw, pitch, yawVelocity, pitchVelocity;

    void reset(double yaw, double pitch) {
        this.yaw = yaw; this.pitch = pitch; yawVelocity = pitchVelocity = 0;
    }
    void update(double targetYaw, double targetPitch, double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0 || !Double.isFinite(targetYaw) || !Double.isFinite(targetPitch)) return;
        double dt = Math.min(seconds, 1.0 / 30); // A stalled frame cannot cause a large catch-up turn.
        double decay = Math.exp(-RESPONSE * dt);
        double yawTarget = yaw + wrap(targetYaw - yaw);
        double yawChange = yaw - yawTarget, yawTerm = yawVelocity + RESPONSE * yawChange;
        double nextYaw = yawTarget + (yawChange + yawTerm * dt) * decay;
        yawVelocity = (yawVelocity - RESPONSE * yawTerm * dt) * decay;
        if ((yawTarget - yaw) * (yawTarget - nextYaw) < 0) { nextYaw = yawTarget; yawVelocity = 0; }
        yaw = nextYaw;

        double pitchTarget = Math.clamp(targetPitch, -90, 90);
        double pitchChange = pitch - pitchTarget, pitchTerm = pitchVelocity + RESPONSE * pitchChange;
        double nextPitch = pitchTarget + (pitchChange + pitchTerm * dt) * decay;
        pitchVelocity = (pitchVelocity - RESPONSE * pitchTerm * dt) * decay;
        if ((pitchTarget - pitch) * (pitchTarget - nextPitch) < 0) { nextPitch = pitchTarget; pitchVelocity = 0; }
        pitch = nextPitch;
    }
    float yaw() { return (float) yaw; }
    float pitch() { return (float) pitch; }
    boolean settled(double targetYaw, double targetPitch) {
        return Math.abs(wrap(targetYaw - yaw)) < .75 && Math.abs(targetPitch - pitch) < .75
            && Math.abs(yawVelocity) < 8 && Math.abs(pitchVelocity) < 8;
    }
    private static double wrap(double angle) { return angle - Math.floor((angle + 180) / 360) * 360; }
}
