package com.example.railgun;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** type 0 = flame punch, type 1 = black flash. */
public record GlovePayload(int type, int owner, double x, double y, double z) implements CustomPayload {
    public static final Id<GlovePayload> ID = new Id<>(Identifier.of(RailgunMod.MOD_ID, "glove_hit"));
    public static final PacketCodec<RegistryByteBuf, GlovePayload> CODEC =
            PacketCodec.of(GlovePayload::write, GlovePayload::read);

    private void write(RegistryByteBuf b) {
        b.writeInt(type); b.writeInt(owner); b.writeDouble(x); b.writeDouble(y); b.writeDouble(z);
    }
    private static GlovePayload read(RegistryByteBuf b) {
        return new GlovePayload(b.readInt(), b.readInt(), b.readDouble(), b.readDouble(), b.readDouble());
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
