package com.example.railgun;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Void Eclipse: seals you inside a temporary pocket dimension built from blocks. */
public class VoidItem extends Item {
    public static final int RADIUS = 10;
    public static final int COOLDOWN = 2400;   // 2 min

    public VoidItem(Settings s) { super(s); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            if (!VoidSpells.canStart(sp)) return TypedActionResult.fail(stack);
            VoidSpells.start(sp);
        }
        player.getItemCooldownManager().set(this, COOLDOWN);
        return TypedActionResult.success(stack, world.isClient());
    }
}
