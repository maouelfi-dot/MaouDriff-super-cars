package com.maou.carprops.client;

import com.maou.carprops.CarProps;
import com.maou.carprops.CarPropsConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;

import java.util.Locale;

public class CarPropsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(CarProps.CAR_ENTITY, CarPropRenderer::new);
        EntityRendererRegistry.register(CarProps.CAR_COLLIDER, NoopRenderer::new);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new CarModelReloader());

        // /carmodels list | reload | quality <auto|high|low>
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("carmodels")
                        .then(ClientCommandManager.literal("list").executes(c -> {
                            var ids = CarModelManager.ids();
                            c.getSource().sendFeedback(Component.literal(ids.isEmpty()
                                    ? "Belum ada model. Cek folder config/carprops/models/"
                                    : "Model tersedia: " + String.join(", ", ids)));
                            return ids.size();
                        }))
                        .then(ClientCommandManager.literal("reload").executes(c -> {
                            CarModelManager.reload(Minecraft.getInstance().getResourceManager());
                            c.getSource().sendFeedback(Component.literal("Model dimuat ulang: " + CarModelManager.ids()));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("quality")
                                .then(ClientCommandManager.argument("mode", StringArgumentType.word()).executes(c -> {
                                    String q = StringArgumentType.getString(c, "mode").toLowerCase(Locale.ROOT);
                                    if (!q.equals("auto") && !q.equals("high") && !q.equals("low")) {
                                        c.getSource().sendError(Component.literal("Pilih: auto, high, atau low"));
                                        return 0;
                                    }
                                    CarPropsConfig.get().quality = q;
                                    CarPropsConfig.save();
                                    c.getSource().sendFeedback(Component.literal("Kualitas mobil = " + q));
                                    return 1;
                                })))));
    }
}
