package com.maou.carprops;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Klik kanan blok = taruh mobil di titik itu (menghadap kamu).
 * Klik kanan ke udara = ganti model (daftar "placerModels" di config/carprops.json).
 */
public class CarPlacerItem extends Item {
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();

    public CarPlacerItem(Properties properties) {
        super(properties);
    }

    private static String currentModel(Player player) {
        List<String> list = CarPropsConfig.get().placerModels;
        int i = SELECTED.getOrDefault(player.getUUID(), Math.max(0, list.indexOf(CarPropsConfig.get().defaultModel)));
        return list.get(Math.floorMod(i, list.size()));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.FAIL;

        Vec3 hit = ctx.getClickLocation();
        Direction face = ctx.getClickedFace();
        BlockPos next = ctx.getClickedPos().relative(face);
        double y = face == Direction.UP ? hit.y : next.getY();

        CarPropEntity car = new CarPropEntity(CarProps.CAR_ENTITY, level);
        car.moveTo(hit.x, y, hit.z, Math.floorMod((int) (player.getYRot() + 180.0f), 360), 0.0f);
        car.setModelId(currentModel(player));
        car.setCarScale((float) CarPropsConfig.get().defaultScale);
        level.addFreshEntity(car);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            List<String> list = CarPropsConfig.get().placerModels;
            int i = SELECTED.getOrDefault(player.getUUID(), Math.max(0, list.indexOf(CarPropsConfig.get().defaultModel)));
            i = Math.floorMod(i + 1, list.size());
            SELECTED.put(player.getUUID(), i);
            player.displayClientMessage(Component.literal("Model: " + list.get(i)), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
