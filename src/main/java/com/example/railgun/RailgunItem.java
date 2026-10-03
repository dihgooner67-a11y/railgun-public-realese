package com.example.railgun;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.Optional;

public class RailgunItem extends Item {
    public static final int CHARGE_TICKS = 20;
    public static final double RANGE = 128;
    public static final float DAMAGE = 40f;      // 20 hearts
    public static final int COOLDOWN = 40;
    public static final int MAX_PIERCE = 4;      // how many blocks-deep the beam can tunnel
    public static final float MAX_HARDNESS = 20f; // stone 1.5, iron 5, obsidian 50 (won't break)

    public RailgunItem(Settings settings) { super(settings); }

    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.BOW; }
    @Override public int getMaxUseTime(ItemStack stack, LivingEntity user) { return 72000; }

    private static boolean hasAmmo(PlayerEntity p) {
        return p.isCreative() || p.getInventory().count(Items.IRON_INGOT) > 0;
    }

    private static void consumeAmmo(PlayerEntity p) {
        if (p.isCreative()) return;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.IRON_INGOT)) { s.decrement(1); return; }
        }
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!hasAmmo(player)) {
            if (!world.isClient)
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 1f, 1.4f);
            return TypedActionResult.fail(stack);
        }
        player.setCurrentHand(hand);
        if (!world.isClient)
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 1.2f, 1.9f);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient || !(user instanceof ServerPlayerEntity p)) return;
        int charged = getMaxUseTime(stack, user) - remainingUseTicks;
        if (charged < CHARGE_TICKS || !hasAmmo(p)) return;
        fire(p);
        consumeAmmo(p);
        p.getItemCooldownManager().set(this, COOLDOWN);
    }

    private void fire(ServerPlayerEntity p) {
        ServerWorld sw = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1f);
        Vec3d far = eye.add(dir.multiply(RANGE));
        boolean canBreak = p.getAbilities().allowModifyWorld;

        // march along the beam, blasting a crater at each block it hits, until it can't go further
        Vec3d cur = eye;
        Vec3d end = far;
        for (int i = 0; i <= MAX_PIERCE; i++) {
            BlockHitResult bhr = sw.raycast(new RaycastContext(cur, far,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            if (bhr.getType() == HitResult.Type.MISS) { end = far; break; }
            end = bhr.getPos();
            if (!canBreak) break;
            BlockPos bp = bhr.getBlockPos();
            float hardness = sw.getBlockState(bp).getHardness(sw, bp);
            if (hardness < 0 || hardness > MAX_HARDNESS) break;
            crater(sw, p, end.add(dir.multiply(0.3)));
            if (i == MAX_PIERCE) break;
            cur = end.add(dir.multiply(0.5));
        }

        boolean hit = false;
        Box box = new Box(eye, end).expand(1.5);
        for (Entity e : sw.getOtherEntities(p, box, x -> x instanceof LivingEntity le && le.isAlive())) {
            Optional<Vec3d> r = e.getBoundingBox().expand(0.4).raycast(eye, end);
            if (r.isEmpty()) continue;
            e.damage(sw.getDamageSources().playerAttack(p), DAMAGE);
            e.addVelocity(dir.x * 3.0, dir.y * 3.0 + 0.6, dir.z * 3.0);
            e.velocityModified = true;
            e.setOnFireFor(4);
            hit = true;
        }

        Vec3d muzzle = eye.add(dir.multiply(1.2)).add(0, -0.2, 0);
        RailShotPayload payload = new RailShotPayload(p.getId(), muzzle, end, hit);
        for (ServerPlayerEntity other : PlayerLookup.around(sw, p.getPos(), 192))
            ServerPlayNetworking.send(other, payload);

        play(sw, eye, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3f, 1.3f);
        play(sw, eye, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 2.0f);
        play(sw, end, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 3f, 0.7f);
        play(sw, end, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.6f);

        p.addVelocity(-dir.x * 0.6, -dir.y * 0.3, -dir.z * 0.6);
        p.velocityModified = true;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
    }

    /** Breaks a rough sphere (radius ~2) of blocks around the impact point, with drops. */
    private static void crater(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        BlockPos c = BlockPos.ofFloored(at);
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy + dz * dz > 5) continue;
                    BlockPos q = c.add(dx, dy, dz);
                    BlockState s = sw.getBlockState(q);
                    if (s.isAir()) continue;
                    float hd = s.getHardness(sw, q);
                    if (hd < 0 || hd > MAX_HARDNESS) continue;
                    sw.breakBlock(q, true, p);
                }
    }

    private static void play(ServerWorld w, Vec3d pos, SoundEvent s, float vol, float pitch) {
        w.playSound(null, pos.x, pos.y, pos.z, s, SoundCategory.PLAYERS, vol, pitch);
    }
}
