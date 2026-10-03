package com.example.railgun;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Builds a hollow sphere "pocket dimension" around the caster (cleared interior, checkerboard floor,
 * bedrock shell with sea-lantern lights), keeps it for a while, then puts every block back.
 */
public final class VoidSpells {
    private static final int CLEAR_TICKS = 20, SEAL_TICKS = 20, HOLD_TICKS = 160, RESTORE_TICKS = 24;
    private static final int FLAGS = Block.NOTIFY_LISTENERS;   // no neighbour updates -> no popped drops

    private static final class Spell {
        final ServerWorld world; final UUID owner; final BlockPos center;
        final List<BlockPos> order = new ArrayList<>();
        final List<BlockState> newStates = new ArrayList<>();
        final List<BlockState> oldStates = new ArrayList<>();
        int interiorCount, placed, age, holdStart, phase;   // phase 0 build, 1 hold, 2 restore
        Spell(ServerWorld w, UUID o, BlockPos c) { world = w; owner = o; center = c; }
    }
    private static final List<Spell> ACTIVE = new ArrayList<>();

    private VoidSpells() {}

    public static boolean canStart(ServerPlayerEntity p) {
        BlockPos c = p.getBlockPos();
        for (Spell s : ACTIVE) {
            if (s.owner.equals(p.getUuid())) return false;
            if (s.world == p.getServerWorld() && s.center.getSquaredDistance(c) < (2.0 * VoidItem.RADIUS + 2) * (2.0 * VoidItem.RADIUS + 2))
                return false;
        }
        return true;
    }

    public static void start(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        BlockPos c = p.getBlockPos();
        Spell s = new Spell(w, p.getUuid(), c);
        int R = VoidItem.RADIUS;
        double inner = R - 1.8;

        List<BlockPos> interior = new ArrayList<>(), shell = new ArrayList<>();
        for (int dx = -R; dx <= R; dx++)
            for (int dy = -R; dy <= R; dy++)
                for (int dz = -R; dz <= R; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > R * R) continue;
                    BlockPos pos = c.add(dx, dy, dz);
                    if (pos.getY() < w.getBottomY() || pos.getY() >= w.getTopY()) continue;
                    if (!w.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                    if (w.getBlockEntity(pos) != null) continue;      // never touch chests etc.
                    if (Math.sqrt(d2) > inner) shell.add(pos); else interior.add(pos);
                }
        interior.sort(Comparator.comparingDouble((BlockPos q) -> q.getSquaredDistance(c)));
        shell.sort(Comparator.comparingInt((BlockPos q) -> q.getY()).thenComparingDouble(q -> q.getSquaredDistance(c)));

        for (BlockPos q : interior) {
            s.order.add(q);
            if (q.getY() == c.getY() - 1)
                s.newStates.add(((q.getX() + q.getZ()) & 1) == 0 ? Blocks.BLACK_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState());
            else
                s.newStates.add(Blocks.AIR.getDefaultState());
        }
        s.interiorCount = interior.size();
        for (BlockPos q : shell) {
            s.order.add(q);
            int h = Math.floorMod(q.getX() * 31 + q.getY() * 17 + q.getZ() * 13, 9);
            s.newStates.add(h == 0 ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.BEDROCK.getDefaultState());
        }
        ACTIVE.add(s);

        Vec3d cv = Vec3d.ofCenter(c);
        send(w, cv, new VoidPayload(0, cv.x, cv.y, cv.z));
        play(w, cv, SoundEvents.ENTITY_WITHER_SPAWN, 3f, 0.5f);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Spell> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Spell s = it.next();
            s.age++;
            int total = s.order.size();
            Vec3d cv = Vec3d.ofCenter(s.center);

            if (s.phase == 0) {
                int target = s.age <= CLEAR_TICKS
                        ? (int) ((long) s.interiorCount * s.age / CLEAR_TICKS)
                        : s.interiorCount + (int) ((long) (total - s.interiorCount) * Math.min(s.age - CLEAR_TICKS, SEAL_TICKS) / SEAL_TICKS);
                while (s.placed < target) place(s, s.placed++);
                if (s.age % 4 == 0)
                    s.world.spawnParticles(ParticleTypes.PORTAL, cv.x, cv.y + 1, cv.z, 40, 3, 3, 3, 0.8);
                if (s.placed >= total) {
                    s.phase = 1;
                    s.holdStart = s.age;
                    send(s.world, cv, new VoidPayload(1, cv.x, cv.y, cv.z));
                    play(s.world, cv, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 4f, 0.5f);
                    play(s.world, cv, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 4f, 0.6f);
                    s.world.spawnParticles(ParticleTypes.END_ROD, cv.x, cv.y + 1, cv.z, 200, 4, 4, 4, 0.2);
                }
            } else if (s.phase == 1) {
                if (s.age - s.holdStart >= HOLD_TICKS) {
                    s.phase = 2;
                    send(s.world, cv, new VoidPayload(2, cv.x, cv.y, cv.z));
                    play(s.world, cv, SoundEvents.BLOCK_BEACON_DEACTIVATE, 4f, 0.7f);
                }
            } else {
                int n = (int) Math.ceil(total / (double) RESTORE_TICKS);
                for (int i = 0; i < n && s.placed > 0; i++) restore(s, --s.placed);
                if (s.placed == 0) it.remove();
            }
        }
    }

    /** Put everything back immediately (server shutdown). */
    public static void restoreAll() {
        for (Spell s : ACTIVE) while (s.placed > 0) restore(s, --s.placed);
        ACTIVE.clear();
    }

    private static void place(Spell s, int i) {
        BlockPos pos = s.order.get(i);
        s.oldStates.add(s.world.getBlockState(pos));
        s.world.setBlockState(pos, s.newStates.get(i), FLAGS);
    }

    private static void restore(Spell s, int i) {
        BlockState old = s.oldStates.get(i);
        if (old != null) s.world.setBlockState(s.order.get(i), old, FLAGS);
    }

    private static void send(ServerWorld w, Vec3d at, VoidPayload payload) {
        for (ServerPlayerEntity pl : PlayerLookup.around(w, at, 128))
            ServerPlayNetworking.send(pl, payload);
    }

    private static void play(ServerWorld w, Vec3d p, SoundEvent s, float vol, float pitch) {
        w.playSound(null, p.x, p.y, p.z, s, SoundCategory.PLAYERS, vol, pitch);
    }
}
