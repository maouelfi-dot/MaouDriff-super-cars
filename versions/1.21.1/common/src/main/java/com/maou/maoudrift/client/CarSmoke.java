package com.maou.maoudrift.client;

import com.maou.maoudrift.config.ClientConfig;
import com.maou.maoudrift.MaouDrift;
import com.maou.maoudrift.entity.CarEntity;
import com.maou.maoudrift.particle.MaouDriftParticles;
import com.maou.maoudrift.physics.CarPhysics;
import com.maou.maoudrift.physics.CarSpec;
import com.maou.maoudrift.physics.Wheel;
import com.maou.maoudrift.surface.RoadSurface;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.maou.maoudrift.client.effect.SmokeEmitter;
import com.maou.maoudrift.platform.McParticleSink;

/**
 * 車を集めて、滑っている車輪から煙を出す（{@link SmokeEmitter}）。
 *
 * <p>車のモデルが描かれる範囲（{@code Entity#shouldRender}）の車だけ。この距離判定にカメラが要るのでここ（クライアント）で集める。</p>
 */
public final class CarSmoke {

    private CarSmoke() {
    }

    public static void onClientTick() {
        // 設定の「煙と土煙を出す」で土煙（CarDust）と一緒に切れる
        if (!ClientConfig.showDust) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Vec3 eye = camera.getPosition();
        McParticleSink sink = new McParticleSink(level);
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof CarEntity car && !car.isGhost()
                    && car.shouldRender(eye.x, eye.y, eye.z)) {
                SmokeEmitter.spawn(car, car.xo, car.yo, car.zo,
                        level.isRainingAt(car.blockPosition()), sink);
            }
        }
    }
}
