package dev.apexpredator.effect;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A bounded server-to-client cue, containing no client-authoritative combat data. */
public record ApexEffectPacket(int entityId, EffectKind kind, Vec3 origin, Vec3 direction,
                               float radius, int duration) {
    public ApexEffectPacket {
        if (!finite(origin) || !finite(direction)) throw new IllegalArgumentException("Non-finite Apex effect coordinates");
        if (!Float.isFinite(radius)) throw new IllegalArgumentException("Non-finite Apex effect radius");
        direction = direction.lengthSqr() < .0001 ? new Vec3(0, 0, 1) : direction.normalize();
        radius = Mth.clamp(radius, .25F, 20F);
        duration = Mth.clamp(duration, 1, 100);
    }
    public static void encode(ApexEffectPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entityId); b.writeVarInt(p.kind.ordinal());
        b.writeDouble(p.origin.x); b.writeDouble(p.origin.y); b.writeDouble(p.origin.z);
        b.writeFloat((float)p.direction.x); b.writeFloat((float)p.direction.y); b.writeFloat((float)p.direction.z);
        b.writeFloat(p.radius); b.writeVarInt(p.duration);
    }
    public static ApexEffectPacket decode(FriendlyByteBuf b) {
        int entity = b.readVarInt(), id = b.readVarInt();
        if (id < 0 || id >= EffectKind.values().length) throw new IllegalArgumentException("Unknown Apex effect kind");
        return new ApexEffectPacket(entity, EffectKind.values()[id], new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readFloat(), b.readFloat(), b.readFloat()), b.readFloat(), b.readVarInt());
    }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
}
