package com.example.railgun;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** type 0 = forming, 1 = sealed (flash frames), 2 = dissolving. x/y/z = pocket centre. */
public record VoidPayload(int type, double x, double y, double z) implements CustomPayload {
    public static final Id<VoidPayload> ID = new Id<>(Identifier.of(RailgunMod.MOD_ID, "void_event"));
    public static final PacketCodec<RegistryByteBuf, VoidPayload> CODEC =
            PacketCodec.of(VoidPayload::write, VoidPayload::read);

    private void write(RegistryByteBuf b) { b.writeInt(type); b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); }
    private static VoidPayload read(RegistryByteBuf b) {
        return new VoidPayload(b.readInt(), b.readDouble(), b.readDouble(), b.readDouble());
    }
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
