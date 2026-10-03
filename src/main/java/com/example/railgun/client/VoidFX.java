package com.example.railgun.client;

import com.example.railgun.VoidItem;
import com.example.railgun.VoidPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Screen FX for the pocket dimension: black/white strobe when it seals, soft white fade when it dissolves. */
public final class VoidFX {
    private static final Random RNG = new Random();
    private static final int BLACK = 0xFF000000, WHITE = 0xFFFFFFFF;
    private static int flashAge = -1, softAge = -1;
    private static float shake;

    private VoidFX() {}

    public static void onPacket(VoidPayload p, MinecraftClient mc) {
        if (mc.player == null) return;
        double d = mc.player.getPos().distanceTo(new Vec3d(p.x(), p.y(), p.z()));
        if (d > VoidItem.RADIUS + 8) return;
        switch (p.type()) {
            case 0 -> shake = Math.max(shake, 3f);
            case 1 -> { flashAge = 0; shake = 14f; }
            default -> { softAge = 0; shake = Math.max(shake, 5f); }
        }
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) { flashAge = softAge = -1; shake = 0; return; }
        if (mc.isPaused()) return;
        if (shake > 0) {
            mc.player.setYaw(mc.player.getYaw() + (RNG.nextFloat() - .5f) * shake * 0.5f);
            mc.player.setPitch(mc.player.getPitch() + (RNG.nextFloat() - .5f) * shake * 0.3f);
            shake *= 0.85f;
            if (shake < 0.05f) shake = 0;
        }
        if (flashAge >= 0 && ++flashAge > 16) flashAge = -1;
        if (softAge >= 0 && ++softAge > 10) softAge = -1;
    }

    public static void render(DrawContext ctx, float delta) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        if (flashAge >= 0) {
            int sub = (int) ((flashAge + delta) * 2);
            if (sub < 8) {
                boolean black = sub % 2 == 0;
                ctx.fill(0, 0, w, h, black ? BLACK : WHITE);
                RailgunFX.speedLines(ctx, w, h, black ? WHITE : BLACK, 60, sub * 7919L, 0.05f);
            } else {
                float t = Math.min((sub - 8) / 22f, 1f);
                int a = (int) ((1 - t) * (1 - t) * 255);
                if (a > 0) ctx.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
            }
        } else if (softAge >= 0) {
            int a = (int) ((1 - (softAge + delta) / 10f) * 170);
            if (a > 0) ctx.fill(0, 0, w, h, (a << 24) | 0xFFFFFF);
        }
    }
}
