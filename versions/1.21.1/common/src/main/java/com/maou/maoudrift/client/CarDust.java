package com.maou.maoudrift.client;

import com.maou.maoudrift.config.ClientConfig;
import com.maou.maoudrift.MaouDrift;
import com.maou.maoudrift.entity.CarEntity;
import com.maou.maoudrift.particle.MaouDriftParticles;
import com.maou.maoudrift.physics.CarSpec;
import com.maou.maoudrift.physics.Wheel;
import com.maou.maoudrift.surface.RoadSurface;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.maou.maoudrift.client.effect.DustEmitter;
import com.maou.maoudrift.platform.McParticleSink;

/**
 * 車を集めて、未舗装を走る車輪から土煙を出す（{@link DustEmitter}）。
 *
 * <p>車のモデルが描かれる範囲（{@code Entity#shouldRender}）の車だけ。この距離判定にカメラが要るのでここ（クライアント）で集める。</p>
 */
public final class CarDust {

    private CarDust() {
    }

    public static void onClientTick() {
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
            if (entity instanceof CarEntity car && !car.isGhost() && car.shouldRender(eye.x, eye.y, eye.z)) {
                DustEmitter.spawn(car, car.xo, car.yo, car.zo,
                        level.isRainingAt(car.blockPosition()), sink);
            }
        }
    }
}
