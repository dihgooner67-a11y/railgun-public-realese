package com.example.railgun;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class RailgunMod implements ModInitializer {
    public static final String MOD_ID = "railgun";
    public static final Item RAILGUN = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "railgun"),
            new RailgunItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));
    public static final Item VOID_ECLIPSE = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "void_eclipse"),
            new VoidItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));
    public static final Item FLAME_GLOVE = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "flame_glove"),
            new GloveItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(RailShotPayload.ID, RailShotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(VoidPayload.ID, VoidPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GlovePayload.ID, GlovePayload.CODEC);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
            e.add(RAILGUN); e.add(VOID_ECLIPSE); e.add(FLAME_GLOVE);
        });
        ServerTickEvents.END_SERVER_TICK.register(VoidSpells::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> VoidSpells.restoreAll());
    }
}
