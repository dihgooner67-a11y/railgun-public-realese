package com.example.railgun.client;

import com.example.railgun.GlovePayload;
import com.example.railgun.RailgunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Blue flame hits, Black Flash particles, red-lightning impact frames, ambient hand flames. */
public final class GloveFX {
    private static final Random RNG = new Random();
    private static final int BLACK = 0xFF000000, WHITE = 0xFFFFFFFF, RED = 0xFFFF1530;
    private static int hitAge = -1;
    private static boolean black;
    private static float shake;

    private GloveFX() {}

    // ---------- network ----------
    public static void onHit(GlovePayload p, MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null || mc.player == null) return;
        Vec3d at = new Vec3d(p.x(), p.y(), p.z());
        boolean bf = p.type() == 1;
        if (bf) blackBurst(w, at); else flameBurst(w, at);

        if (mc.player.getId() == p.owner()) {
            hitAge = 0; black = bf; shake = bf ? 16f : 3f;
        } else {
            double d = mc.player.getPos().distanceTo(at);
            shake = Math.max(shake, (float) ((bf ? 6 : 1.5) / (1 + d / 10)));
        }
    }

    private static void flameBurst(ClientWorld w, Vec3d a) {
        for (int i = 0; i < 30; i++)
            w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, a.x, a.y, a.z,
                    (RNG.nextDouble() - .5) * .5, (RNG.nextDouble() - .3) * .5, (RNG.nextDouble() - .5) * .5);
        for (int i = 0; i < 24; i++) {                    // expanding blue ring
            double ang = i / 24.0 * Math.PI * 2;
            w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, a.x, a.y, a.z, Math.cos(ang) * .35, 0.04, Math.sin(ang) * .35);
        }
        for (int i = 0; i < 8; i++)
            w.addParticle(ParticleTypes.SOUL, a.x, a.y, a.z, (RNG.nextDouble() - .5) * .2, .1, (RNG.nextDouble() - .5) * .2);
        w.addParticle(ParticleTypes.FLASH, a.x, a.y, a.z, 0, 0, 0);
    }

    private static void blackBurst(ClientWorld w, Vec3d a) {
        w.addParticle(ParticleTypes.FLASH, a.x, a.y, a.z, 0, 0, 0);
        w.addParticle(ParticleTypes.EXPLOSION, a.x, a.y, a.z, 0, 0, 0);
        for (int i = 0; i < 60; i++)
            w.addParticle(DustParticleEffect.DEFAULT, a.x, a.y, a.z,
                    (RNG.nextDouble() - .5) * 1.4, (RNG.nextDouble() - .5) * 1.4, (RNG.nextDouble() - .5) * 1.4);
        for (int i = 0; i < 40; i++)
            w.addParticle(ParticleTypes.SQUID_INK, a.x, a.y, a.z,
                    (RNG.nextDouble() - .5) * .9, (RNG.nextDouble() - .5) * .9, (RNG.nextDouble() - .5) * .9);
        // jagged black-lightning bolts (random walks in 3D)
        for (int b = 0; b < 10; b++) {
            Vec3d dir = new Vec3d(RNG.nextGaussian(), RNG.nextGaussian(), RNG.nextGaussian()).normalize();
            Vec3d pos = a;
            for (int s = 0; s < 12; s++) {
                dir = dir.add(new Vec3d(RNG.nextGaussian(), RNG.nextGaussian(), RNG.nextGaussian()).multiply(0.5)).normalize();
                pos = pos.add(dir.multiply(0.5));
                w.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 0, 0, 0);
                w.addParticle(DustParticleEffect.DEFAULT, pos.x, pos.y, pos.z, 0, 0, 0);
            }
        }
    }

    // ---------- tick ----------
    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { hitAge = -1; shake = 0; return; }
        if (mc.isPaused()) return;

        // ambient blue flames licking off the glove hand of every holder nearby
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p.squaredDistanceTo(mc.player) > 48 * 48) continue;
            boolean main = p.getMainHandStack().isOf(RailgunMod.FLAME_GLOVE);
            boolean off = p.getOffHandStack().isOf(RailgunMod.FLAME_GLOVE);
            if (!main && !off) continue;
            Arm arm = main ? p.getMainArm() : p.getMainArm().getOpposite();
            Vec3d fwd = Vec3d.fromPolar(0, p.getYaw());
            Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);
            Vec3d hand = p.getPos().add(0, 1.0, 0).add(fwd.multiply(0.5)).add(right.multiply(arm == Arm.RIGHT ? 0.4 : -0.4));
            for (int i = 0; i < 2; i++)
                mc.world.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        hand.x + (RNG.nextDouble() - .5) * .2, hand.y + (RNG.nextDouble() - .5) * .2, hand.z + (RNG.nextDouble() - .5) * .2,
                        0, 0.03, 0);
        }

        if (shake > 0) {
            mc.player.setYaw(mc.player.getYaw() + (RNG.nextFloat() - .5f) * shake * 0.5f);
            mc.player.setPitch(mc.player.getPitch() + (RNG.nextFloat() - .5f) * shake * 0.3f);
            shake *= 0.8f;
            if (shake < 0.05f) shake = 0;
        }
        if (hitAge >= 0 && ++hitAge > (black ? 14 : 6)) hitAge = -1;
    }

    // ---------- HUD ----------
    public static void render(DrawContext ctx, float delta) {
        if (hitAge < 0) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int sub = (int) ((hitAge + delta) * 2);
        if (!black) {
            if (sub < 8) ctx.fill(0, 0, w, h, ((int) ((1 - sub / 8f) * 95) << 24) | 0x2090FF);
            return;
        }
        long seed = sub * 104729L;
        switch (sub) {
            case 0 -> { ctx.fill(0, 0, w, h, BLACK); bolts(ctx, w, h, RED, 3, 16, seed); bolts(ctx, w, h, WHITE, 1, 16, seed); }
            case 1 -> { ctx.fill(0, 0, w, h, 0xFFFFE8E8); bolts(ctx, w, h, BLACK, 3, 16, seed); bolts(ctx, w, h, RED, 1, 16, seed); }
            case 2 -> { ctx.fill(0, 0, w, h, BLACK); bolts(ctx, w, h, RED, 4, 22, seed); bolts(ctx, w, h, WHITE, 1, 22, seed); }
            case 3 -> { ctx.fill(0, 0, w, h, 0xFF3A0008); bolts(ctx, w, h, WHITE, 2, 14, seed); }
            default -> {
                float t = Math.min((sub - 4) / 16f, 1f);
                int a = (int) ((1 - t) * (1 - t) * 190);
                if (a > 0) ctx.fill(0, 0, w, h, (a << 24) | 0xFF1030);
            }
        }
    }

    /** Jagged lightning bolts radiating from screen centre. Same seed = same paths, so layers line up. */
    private static void bolts(DrawContext ctx, int w, int h, int color, int thick, int count, long seed) {
        Random r = new Random(seed);
        MatrixStack m = ctx.getMatrices();
        for (int b = 0; b < count; b++) {
            float x = w / 2f, y = h / 2f, ang = r.nextFloat() * 360f;
            for (int s = 0; s < 14; s++) {
                float len = 18 + r.nextFloat() * 34;
                m.push();
                m.translate(x, y, 0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ang));
                ctx.fill(0, -thick, (int) len, thick, color);
                m.pop();
                double rad = Math.toRadians(ang);
                x += (float) Math.cos(rad) * len;
                y += (float) Math.sin(rad) * len;
                ang += (r.nextFloat() - .5f) * 80f;
                if (x < -50 || x > w + 50 || y < -50 || y > h + 50) break;
            }
        }
    }
}
