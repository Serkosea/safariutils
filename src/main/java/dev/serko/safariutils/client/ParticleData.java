package dev.serko.safariutils.client;
record ParticleData(net.minecraft.core.particles.ParticleOptions particle,int count,net.minecraft.world.phys.Vec3 position,
    float xDist,float yDist,float zDist,float xSpeed,float ySpeed,float zSpeed) { }
