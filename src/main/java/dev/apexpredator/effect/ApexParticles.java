package dev.apexpredator.effect;
import dev.apexpredator.entity.ApexEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
public final class ApexParticles {
    private ApexParticles() { }
    public static void telegraph(ApexEntity e, double radius) {
        if (!(e.level() instanceof ServerLevel level)) return;
        for (int i=0; i<32; i++) {
            double a=i*Math.PI/16;
            level.sendParticles(ParticleTypes.FLAME,e.getX()+Math.cos(a)*radius,e.getY()+.15,e.getZ()+Math.sin(a)*radius,1,0,0,0,.01);
        }
    }
    public static void burst(ApexEntity e) {
        if (e.level() instanceof ServerLevel s) {
            s.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,e.getX(),e.getY()+1.8,e.getZ(),70,1.4,1.5,1.4,.1);
            s.sendParticles(ParticleTypes.EXPLOSION,e.getX(),e.getY()+.2,e.getZ(),4,.5,.2,.5,0);
        }
    }
    public static void warp(ApexEntity e) {
        if (e.level() instanceof ServerLevel s) s.sendParticles(ParticleTypes.PORTAL,e.getX(),e.getY()+1.6,e.getZ(),60,.65,1.3,.65,.4);
    }
    public static void trail(ApexEntity e) {
        if (e.level() instanceof ServerLevel s) s.sendParticles(ParticleTypes.SOUL,e.getX(),e.getY()+.2,e.getZ(),4,.6,.1,.6,.01);
    }
    public static void special(ApexEntity e, Vec3 direction, int elapsed) {
        if (!(e.level() instanceof ServerLevel s)) return;
        for (int i=1; i<=18; i++) {
            Vec3 p=e.position().add(direction.scale(i));
            s.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,p.x,p.y+.2,p.z,2,.12,.1,.12,0);
        }
    }
}
