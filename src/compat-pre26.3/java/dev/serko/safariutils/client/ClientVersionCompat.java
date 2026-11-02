package dev.serko.safariutils.client;
final class ClientVersionCompat {
    static void rotate(com.mojang.blaze3d.vertex.PoseStack poses,org.joml.Quaternionf rotation) {poses.mulPose(rotation);}
    static void openUri(java.net.URI uri) {net.minecraft.util.Util.getPlatform().openUri(uri);}
    static ParticleData particle(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p) {
        return new ParticleData(p.getParticle(),p.getCount(),new net.minecraft.world.phys.Vec3(p.getX(),p.getY(),p.getZ()),p.getXDist(),p.getYDist(),p.getZDist(),p.getMaxSpeed(),p.getMaxSpeed(),p.getMaxSpeed());
    }
}
