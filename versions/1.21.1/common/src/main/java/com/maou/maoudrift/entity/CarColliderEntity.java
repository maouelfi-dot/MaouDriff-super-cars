package com.maou.maoudrift.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * 車体に沿って並べる見えない固い箱。プレイヤーや動物は通り抜けられず、押せもしない。
 * 車（{@link CarEntity}）が作って毎ティック動かす。保存しない。
 */
public class CarColliderEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER =
            SynchedEntityData.defineId(CarColliderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BOX_W =
            SynchedEntityData.defineId(CarColliderEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BOX_H =
            SynchedEntityData.defineId(CarColliderEntity.class, EntityDataSerializers.FLOAT);
    private int orphanTicks;

    public CarColliderEntity(EntityType<? extends CarColliderEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, -1);
        builder.define(BOX_W, 1.0f);
        builder.define(BOX_H, 1.0f);
    }

    public void setOwnerId(int id) {
        if (this.entityData.get(OWNER) != id) {
            this.entityData.set(OWNER, id);
        }
    }

    public void setBox(float w, float h) {
        if (this.entityData.get(BOX_W) != w) {
            this.entityData.set(BOX_W, w);
        }
        if (this.entityData.get(BOX_H) != h) {
            this.entityData.set(BOX_H, h);
        }
    }

    private Entity owner() {
        int id = this.entityData.get(OWNER);
        return id < 0 ? null : this.level().getEntity(id);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (this.entityData == null) {
            return super.getDimensions(pose);
        }
        return EntityDimensions.fixed(this.entityData.get(BOX_W), this.entityData.get(BOX_H));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (BOX_W.equals(accessor) || BOX_H.equals(accessor)) {
            this.refreshDimensions();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            Entity o = owner();
            if (o == null || o.isRemoved()) {
                if (++orphanTicks > 40) {
                    this.discard();
                }
            } else {
                orphanTicks = 0;
            }
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    /** 見た目は車の方が受け持つ。クリックはこの箱を素通りして車に届く。 */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        Entity o = owner();
        return o != null ? o.interact(player, hand) : InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
