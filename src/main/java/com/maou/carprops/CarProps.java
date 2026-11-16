package com.maou.carprops;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarProps implements ModInitializer {
    public static final String MOD_ID = "carprops";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static EntityType<CarPropEntity> CAR_ENTITY;
    public static EntityType<CarColliderEntity> CAR_COLLIDER;
    public static Item CAR_PLACER;

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        CarPropsConfig.load();

        CAR_ENTITY = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                id("car_prop"),
                EntityType.Builder.<CarPropEntity>of(CarPropEntity::new, MobCategory.MISC)
                        .sized(0.8f, 0.8f)
                        .clientTrackingRange(16)
                        .updateInterval(2)
                        .fireImmune()
                        .build("car_prop"));

        CAR_COLLIDER = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                id("car_collider"),
                EntityType.Builder.<CarColliderEntity>of(CarColliderEntity::new, MobCategory.MISC)
                        .sized(1.0f, 1.0f)
                        .clientTrackingRange(16)
                        .updateInterval(1)
                        .fireImmune()
                        .noSave()
                        .noSummon()
                        .build("car_collider"));

        CAR_PLACER = Registry.register(
                BuiltInRegistries.ITEM,
                id("car_placer"),
                new CarPlacerItem(new Item.Properties().stacksTo(1)));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(CAR_PLACER));

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> CarCommands.register(dispatcher));

        LOGGER.info("CarProps siap. Pakai /carprop spawn atau item Car Placer.");
    }
}
