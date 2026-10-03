package com.example.railgun.client;

import com.example.railgun.RailShotPayload;
import com.example.railgun.RailgunItem;
import com.example.railgun.RailgunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** All client-side juice: beam particles, screen shake, charge HUD and impact frames. */
public final class RailgunFX {
    private static final Random RNG = new Random();
    private static final int BLACK = 0xFF000000, WHITE = 0xFFFFFFFF, RED = 0xFFFF1530;

    private static final class Beam { Vec3d s, e; int age; Beam(Vec3d s, Vec3d e) { this.s = s; this.e = e; } }
    private static final List<Beam> BEAMS = new ArrayList<>();

    private static int impactAge = -1;
    private static boolean impactHit;
    private static float shake, charge;
    private static long ticks;

    private RailgunFX() {}

    // ---------- network ----------
    public static void onShot(RailShotPayload p, MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null) return;
        burst(w, p.start(), p.end());
        BEAMS.add(new Beam(p.start(), p.end()));

        ClientPlayerEntity me = mc.player;
        if (me == null) return;
        if (me.getId() == p.shooter()) {
            impactAge = 0;
            impactHit = p.hit();
            shake = p.hit() ? 12f : 8f;
            me.setPitch(me.getPitch() - 7f);          // recoil snap
        } else {
            double d = me.getPos().distanceTo(p.start());
            shake = Math.max(shake, (float) (4 / (1 + d / 12)));
        }
    }

    // ---------- particles ----------
    private static void burst(ClientWorld w, Vec3d s, Vec3d e) {
        Vec3d d = e.subtract(s);
        double len = d.length();
        Vec3d dir = d.normalize();
        Vec3d up = Math.abs(dir.y) > 0.99 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d u = dir.crossProduct(up).normalize();
        Vec3d v = dir.crossProduct(u);

        for (double t = 0; t < len; t += 0.4) {
            Vec3d c = s.add(dir.multiply(t));
            w.addParticle(ParticleTypes.END_ROD, c.x, c.y, c.z, dir.x * 0.05, dir.y * 0.05, dir.z * 0.05);
            for (int k = 0; k < 2; k++) {                    // double helix
                double a = t * 2.5 + k * Math.PI;
                Vec3d q = c.add(u.multiply(Math.cos(a) * 0.5)).add(v.multiply(Math.sin(a) * 0.5));
                w.addParticle(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, dir.x * 0.3, dir.y * 0.3, dir.z * 0.3);
            }
        }
        // muzzle
        w.addParticle(ParticleTypes.FLASH, s.x, s.y, s.z, 0, 0, 0);
        for (int i = 0; i < 12; i++)
            w.addParticle(ParticleTypes.CLOUD, s.x, s.y, s.z,
                    dir.x * 0.6 + (RNG.nextDouble() - .5) * .3, dir.y * 0.6 + (RNG.nextDouble() - .5) * .3,
                    dir.z * 0.6 + (RNG.nextDouble() - .5) * .3);
        // impact
        w.addParticle(ParticleTypes.SONIC_BOOM, e.x, e.y, e.z, 0, 0, 0);
        w.addParticle(ParticleTypes.EXPLOSION_EMITTER, e.x, e.y, e.z, 0, 0, 0);
        w.addParticle(ParticleTypes.FLASH, e.x, e.y, e.z, 0, 0, 0);
        for (int i = 0; i < 40; i++)
            w.addParticle(ParticleTypes.ELECTRIC_SPARK, e.x, e.y, e.z,
                    (RNG.nextDouble() - .5) * 1.5, (RNG.nextDouble() - .5) * 1.5, (RNG.nextDouble() - .5) * 1.5);
    }

    // ---------- tick ----------
    public static void tick(MinecraftClient mc) {
        ticks++;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) { BEAMS.clear(); impactAge = -1; shake = charge = 0; return; }
        if (mc.isPaused()) return;

        charge = 0;
        if (p.isUsingItem() && p.getActiveItem().isOf(RailgunMod.RAILGUN))
            charge = Math.min(p.getItemUseTime() / (float) RailgunItem.CHARGE_TICKS, 1f);

        // camera shake (charging rumble + post-shot kick)
        float mag = charge * charge * 1.5f + shake * 0.5f;
        if (mag > 0.01f) {
            p.setYaw(p.getYaw() + (RNG.nextFloat() - .5f) * mag);
            p.setPitch(p.getPitch() + (RNG.nextFloat() - .5f) * mag * 0.6f);
        }
        shake *= 0.78f;
        if (shake < 0.05f) shake = 0;

        // sparks sucked into the muzzle while charging
        if (charge > 0) {
            Vec3d m = p.getEyePos().add(p.getRotationVec(1f).multiply(1.2));
            int n = 2 + (int) (charge * 5);
            double radius = 1.5 * (1 - charge) + 0.25;
            for (int i = 0; i < n; i++) {
                Vec3d o = new Vec3d(RNG.nextGaussian(), RNG.nextGaussian(), RNG.nextGaussian()).normalize().multiply(radius);
                mc.world.addParticle(ParticleTypes.ELECTRIC_SPARK, m.x + o.x, m.y + o.y, m.z + o.z,
                        -o.x * 0.25, -o.y * 0.25, -o.z * 0.25);
            }
        }

        // lingering beam shimmer
        for (Iterator<Beam> it = BEAMS.iterator(); it.hasNext(); ) {
            Beam b = it.next();
            if (++b.age > 8) { it.remove(); continue; }
            Vec3d d = b.e.subtract(b.s);
            Vec3d dir = d.normalize();
            for (double t = RNG.nextDouble(); t < d.length(); t += 1.5)
                mc.world.addParticle(ParticleTypes.END_ROD, b.s.x + dir.x * t, b.s.y + dir.y * t, b.s.z + dir.z * t,
                        (RNG.nextDouble() - .5) * .05, (RNG.nextDouble() - .5) * .05, (RNG.nextDouble() - .5) * .05);
        }

        if (impactAge >= 0 && ++impactAge > 12) impactAge = -1;
    }

    // ---------- HUD ----------
    public static void render(DrawContext ctx, float delta) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();

        if (impactAge >= 0) { renderImpact(ctx, w, h, delta); return; }
        if (charge > 0.01f) {
            boolean full = charge >= 1f;
            int a = (int) (charge * 90);
            int col = (a << 24) | (full ? 0xFFFFFF : 0x00CCFF);
            int clear = full ? 0x00FFFFFF : 0x0000CCFF;
            ctx.fillGradient(0, 0, w, h / 3, col, clear);
            ctx.fillGradient(0, h * 2 / 3, w, h, clear, col);
            int lineA = (int) (80 + charge * 175);
            speedLines(ctx, w, h, (lineA << 24) | (full ? 0xFFFFFF : 0x66E0FF), 28, (ticks / 2) * 31, 0.9f - 0.7f * charge);
        }
    }

    /** Anime-style impact frames: hard black/white inversions with radial speed lines, then a fade. */
    private static void renderImpact(DrawContext ctx, int w, int h, float delta) {
        int sub = (int) ((impactAge + delta) * 2);        // half-tick steps (~25 ms)
        long seed = sub * 7919L;
        switch (sub) {
            case 0 -> { ctx.fill(0, 0, w, h, BLACK); speedLines(ctx, w, h, WHITE, 70, seed, 0.04f); }
            case 1 -> { ctx.fill(0, 0, w, h, WHITE); speedLines(ctx, w, h, BLACK, 70, seed, 0.04f); }
            case 2 -> { ctx.fill(0, 0, w, h, BLACK); speedLines(ctx, w, h, WHITE, 40, seed, 0.25f); }
            case 3 -> { ctx.fill(0, 0, w, h, impactHit ? RED : WHITE); speedLines(ctx, w, h, BLACK, 50, seed, 0.15f); }
            default -> {
                float t = Math.min((sub - 4) / 16f, 1f);
                int a = (int) ((1 - t) * (1 - t) * 210);
                if (a > 0) {
                    ctx.fill(0, 0, w, h, (a << 24) | (impactHit ? 0xFFD0D0 : 0xFFFFFF));
                    speedLines(ctx, w, h, ((a / 2) << 24) | 0x66E0FF, 24, seed, 0.1f + t * 0.5f);
                }
            }
        }
    }

    static void speedLines(DrawContext ctx, int w, int h, int color, int count, long seed, float inner) {
        Random r = new Random(seed);
        float maxR = (float) Math.hypot(w, h) / 2f;
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(w / 2f, h / 2f, 0);
        for (int i = 0; i < count; i++) {
            float angle = r.nextFloat() * 360f;
            int start = (int) (maxR * (inner + r.nextFloat() * 0.3f));
            int th = 1 + r.nextInt(3);
            m.push();
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            ctx.fill(start, -th, (int) maxR, th, color);
            m.pop();
        }
        m.pop();
    }
}
