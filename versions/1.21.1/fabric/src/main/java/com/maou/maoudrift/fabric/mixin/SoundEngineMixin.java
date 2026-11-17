package com.maou.maoudrift.fabric.mixin;

import com.maou.maoudrift.client.hook.ClientHooks;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 音のエンジンを作った・読み直した直後（NeoForge の {@code SoundEngineLoadEvent} にあたる場面）。NeoForge と同じく、
 * コンストラクタの終わりと {@code reload} の終わり。
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void maoudrift$created(CallbackInfo ci) {
        ClientHooks.soundEngineLoad();
    }

    @Inject(method = "reload", at = @At("RETURN"))
    private void maoudrift$reloaded(CallbackInfo ci) {
        ClientHooks.soundEngineLoad();
    }
}
