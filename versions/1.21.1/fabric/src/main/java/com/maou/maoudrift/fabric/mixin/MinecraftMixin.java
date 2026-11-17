package com.maou.maoudrift.fabric.mixin;

import com.maou.maoudrift.client.hook.ClientHooks;
import com.maou.maoudrift.client.hook.FrameRender;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1 フレームを描き始める前（NeoForge の {@code RenderFrameEvent.Pre} にあたる場面）。NeoForge と同じく
 * {@code Minecraft#runTick} で {@code GameRenderer#render} を呼ぶ直前。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void maoudrift$renderFramePre(boolean renderLevel, CallbackInfo ci) {
        ClientHooks.renderFramePre(new FrameRender(((Minecraft) (Object) this).getTimer()));
    }
}
