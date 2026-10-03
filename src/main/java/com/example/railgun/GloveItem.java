package com.example.railgun;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** Azure Flame Glove: hold it and punch. Blue-flame hits, 10% chance of a Black Flash. */
public class GloveItem extends Item {
    public static final float DAMAGE = 10f;          // 5 hearts
    public static final float BLACK_DAMAGE = 40f;    // 20 hearts
    public static final float BLACK_CHANCE = 0.10f;

    public GloveItem(Settings s) { super(s); }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker.getWorld() instanceof ServerWorld sw)) return true;

        boolean black = attacker.getRandom().nextFloat() < BLACK_CHANCE;
        Vec3d look = attacker.getRotationVec(1f);
        Vec3d flat = new Vec3d(look.x, 0, look.z).normalize();

        DamageSource src = attacker instanceof PlayerEntity pl
                ? sw.getDamageSources().playerAttack(pl)
                : sw.getDamageSources().mobAttack(attacker);
        target.damage(src, black ? BLACK_DAMAGE : DAMAGE);

        double kb = black ? 3.8 : 1.1;
        target.addVelocity(flat.x * kb, black ? 1.0 : 0.3, flat.z * kb);
        target.velocityModified = true;
        target.setOnFireFor(black ? 6 : 3);

        Vec3d at = target.getPos().add(0, target.getHeight() * 0.6, 0);
        GlovePayload payload = new GlovePayload(black ? 1 : 0, attacker.getId(), at.x, at.y, at.z);
        for (ServerPlayerEntity pl : PlayerLookup.around(sw, at, 96))
            ServerPlayNetworking.send(pl, payload);

        if (black) {
            play(sw, at, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 3f, 1.4f);
            play(sw, at, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2f, 1.1f);
            play(sw, at, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 3f, 0.8f);
        } else {
            play(sw, at, SoundEvents.ITEM_FIRECHARGE_USE, 1.5f, 0.8f);
            play(sw, at, SoundEvents.ENTITY_BLAZE_SHOOT, 1f, 1.3f);
        }
        return true;
    }

    private static void play(ServerWorld w, Vec3d p, SoundEvent s, float vol, float pitch) {
        w.playSound(null, p.x, p.y, p.z, s, SoundCategory.PLAYERS, vol, pitch);
    }
}
