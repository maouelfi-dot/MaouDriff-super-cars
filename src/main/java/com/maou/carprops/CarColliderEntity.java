package com.maou.carprops;

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
 * Kotak tabrakan tak terlihat. Satu mobil memakai 2-3 kotak berjajar (dibuat & digerakkan oleh CarPropEntity).
 * Pemain/mob tidak bisa menembusnya dan tidak bisa mendorongnya; klik kanan / pukul diteruskan ke mobil.
 * Tidak disimpan ke dunia (noSave) - dibuat ulang otomatis oleh mobilnya.
 */
public class CarColliderEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(CarColliderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BOX_W = SynchedEntityData.defineId(CarColliderEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BOX_H = SynchedEntityData.defineId(CarColliderEntity.class, EntityDataSerializers.FLOAT);
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

    public void setOwnerId(int id) { this.entityData.set(OWNER, id); }
    public void setBox(float w, float h) { this.entityData.set(BOX_W, w); this.entityData.set(BOX_H, h); }

    private Entity owner() {
        int id = this.entityData.get(OWNER);
        return id < 0 ? null : this.level().getEntity(id);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        if (this.entityData == null) return super.getDimensions(pose);
        return EntityDimensions.fixed(this.entityData.get(BOX_W), this.entityData.get(BOX_H));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (BOX_W.equals(accessor) || BOX_H.equals(accessor)) this.refreshDimensions();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            Entity o = owner();
            if (o == null || o.isRemoved()) {
                if (++orphanTicks > 40) this.discard();
            } else {
                orphanTicks = 0;
            }
        }
    }

    @Override public boolean canBeCollidedWith() { return true; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        Entity o = owner();
        return o != null ? o.interact(player, hand) : InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity o = owner();
        return o != null && o.hurt(source, amount);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
}
