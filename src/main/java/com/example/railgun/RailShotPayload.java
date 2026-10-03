package com.example.railgun;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public record RailShotPayload(int shooter, Vec3d start, Vec3d end, boolean hit) implements CustomPayload {
    public static final Id<RailShotPayload> ID = new Id<>(Identifier.of(RailgunMod.MOD_ID, "rail_shot"));
    public static final PacketCodec<RegistryByteBuf, RailShotPayload> CODEC =
            PacketCodec.of(RailShotPayload::write, RailShotPayload::read);

    private void write(RegistryByteBuf b) {
        b.writeInt(shooter);
        b.writeDouble(start.x); b.writeDouble(start.y); b.writeDouble(start.z);
        b.writeDouble(end.x); b.writeDouble(end.y); b.writeDouble(end.z);
        b.writeBoolean(hit);
    }

    private static RailShotPayload read(RegistryByteBuf b) {
        int id = b.readInt();
        Vec3d s = new Vec3d(b.readDouble(), b.readDouble(), b.readDouble());
        Vec3d e = new Vec3d(b.readDouble(), b.readDouble(), b.readDouble());
        return new RailShotPayload(id, s, e, b.readBoolean());
    }

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
