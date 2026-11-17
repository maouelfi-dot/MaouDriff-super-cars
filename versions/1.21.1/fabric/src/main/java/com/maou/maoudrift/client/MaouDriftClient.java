package com.maou.maoudrift.client;

import com.maou.maoudrift.config.ClientConfig;
import com.maou.maoudrift.MaouDrift;
import com.maou.maoudrift.Platform;
import com.maou.maoudrift.client.hook.ClientHooks;
import com.maou.maoudrift.client.hook.GuiRender;
import com.maou.maoudrift.network.MaouDriftNetworkClient;
import com.maou.maoudrift.particle.MaouDriftParticles;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import com.maou.maoudrift.render.CarItemRenderer;
import com.maou.maoudrift.render.CarObjRenderer;
import com.maou.maoudrift.render.DustParticle;
import com.maou.maoudrift.render.SparkParticle;

/**
 * Fabric 版のクライアントの入り口（{@code fabric.mod.json} の {@code client}）。
 *
 * <p>どの場面でどのクラスをどの順に呼ぶかは共有側の {@link ClientHooks} が決める。ここは Fabric API の受け口を
 * つなぐだけ。Fabric API に受け口が無い場面（カメラの向き・視野・手・生き物の描画・ワールドの描画の段・
 * フレームの始まり・音のエンジン・ホットバー）は {@code fabric.mixin} の各 mixin が呼ぶ。
 * NeoForge 版の同じ役目は {@code NeoForgeClientEvents} と {@code MaouDrift.ClientModEvents}。</p>
 */
public final class MaouDriftClient implements ClientModInitializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        // 画面の好み（maoudrift-client.toml）。NeoForge 版と同じファイル・同じ書き方
        ClientConfig.load(Platform.configDir());

        MaouDriftKeys.register();
        MaouDriftNetworkClient.register();

        EntityRendererRegistry.register(MaouDrift.CAR.get(), CarObjRenderer::new);
        EntityRendererRegistry.register(MaouDrift.COLLIDER.get(),
                ctx -> new net.minecraft.client.renderer.entity.NoopRenderer<>(ctx));

        // 粒の種類は両側で登録してある（MaouDriftParticles）。描く係だけがクライアント側
        ParticleFactoryRegistry particles = ParticleFactoryRegistry.getInstance();
        particles.register(MaouDriftParticles.DUST.get(), sprites -> new DustParticle.Provider(sprites, DustParticle.DUST));
        particles.register(MaouDriftParticles.TIRE_SMOKE.get(), sprites -> new DustParticle.Provider(sprites, DustParticle.TIRE_SMOKE));
        particles.register(MaouDriftParticles.SPARK.get(), SparkParticle.Provider::new);

        // 車のアイテムの見た目は車のモデルから作る（CarItemRenderer）。NeoForge 版は IClientItemExtensions
        BuiltinItemRendererRegistry.INSTANCE.register(MaouDrift.CAR_ITEM.get(),
                (stack, mode, pose, buffers, light, overlay) ->
                        CarItemRenderer.get().renderByItem(stack, mode, pose, buffers, light, overlay));

        CoreShaderRegistrationCallback.EVENT.register(context -> ClientHooks.registerShaders(context::register));

        // リソースを読み直したら OBJ と見た目の定義も読み直す（F3+T でモデルが変わる）
        ResourceLocation reloadId = ResourceLocation.fromNamespaceAndPath(MaouDrift.MODID, "client_models");
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return reloadId;
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                ClientHooks.resourcesReloaded();
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> ClientHooks.clientTickPre());
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientHooks.clientTickPost());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientHooks.loggingIn());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientHooks.loggingOut());
        ClientEntityEvents.ENTITY_LOAD.register(ClientHooks::entityJoin);
        HudRenderCallback.EVENT.register((graphics, delta) -> ClientHooks.renderGuiPost(new GuiRender(graphics, delta)));

        LOGGER.info("MaouDrift（Fabric）のクライアント側を準備した");
    }
}
