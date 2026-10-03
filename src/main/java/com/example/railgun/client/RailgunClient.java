package com.example.railgun.client;

import com.example.railgun.GlovePayload;
import com.example.railgun.RailShotPayload;
import com.example.railgun.RailgunMod;
import com.example.railgun.VoidPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;

public class RailgunClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(RailShotPayload.ID,
                (payload, ctx) -> RailgunFX.onShot(payload, ctx.client()));
        ClientPlayNetworking.registerGlobalReceiver(VoidPayload.ID,
                (payload, ctx) -> VoidFX.onPacket(payload, ctx.client()));
        ClientPlayNetworking.registerGlobalReceiver(GlovePayload.ID,
                (payload, ctx) -> GloveFX.onHit(payload, ctx.client()));

        ClientTickEvents.END_CLIENT_TICK.register(RailgunFX::tick);
        ClientTickEvents.END_CLIENT_TICK.register(VoidFX::tick);
        ClientTickEvents.END_CLIENT_TICK.register(GloveFX::tick);
        HudRenderCallback.EVENT.register((ctx, tc) -> {
            float d = tc.getTickDelta(false);
            RailgunFX.render(ctx, d);
            VoidFX.render(ctx, d);
            GloveFX.render(ctx, d);
        });

        // rail gun: 4-stage charge model
        ModelPredicateProviderRegistry.register(RailgunMod.RAILGUN, Identifier.of(RailgunMod.MOD_ID, "charge"),
                (stack, world, entity, seed) -> {
                    if (entity == null || !entity.isUsingItem() || !entity.getActiveItem().isOf(RailgunMod.RAILGUN))
                        return 0f;
                    return Math.min(entity.getItemUseTime() / 20f, 1f);
                });
        // glove: cycles 3 flame shapes ~11x/second so the fire flickers
        ModelPredicateProviderRegistry.register(RailgunMod.FLAME_GLOVE, Identifier.of(RailgunMod.MOD_ID, "flicker"),
                (stack, world, entity, seed) -> ((System.currentTimeMillis() / 90L) % 3) / 3f);
    }
}
