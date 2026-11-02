package dev.serko.safariutils.client;
final class ClientVersionCompat {
    static void rotate(com.mojang.blaze3d.vertex.PoseStack poses,org.joml.Quaternionf rotation) {poses.rotate(rotation);}
    static void openUri(java.net.URI uri) {com.mojang.blaze3d.Blaze3D.openUri(uri);}
    static ParticleData particle(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p) {
        return new ParticleData(p.particle(),p.count(),new net.minecraft.world.phys.Vec3(p.x(),p.y(),p.z()),p.xDist(),p.yDist(),p.zDist(),p.xMaxSpeed(),p.yMaxSpeed(),p.zMaxSpeed());
    }
}
