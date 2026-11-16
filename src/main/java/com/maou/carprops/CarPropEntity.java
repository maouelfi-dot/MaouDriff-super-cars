package com.maou.carprops;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Mobil "properti": tidak bergerak, tidak bisa dikendarai. Cuma berdiri di dunia untuk difoto.
 * Semua pengaturan (model, skala, warna, dll) disinkronkan ke klien lewat SynchedEntityData.
 */
public class CarPropEntity extends Entity {
    private static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(CarPropEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(CarPropEntity.class, EntityDataSerializers.FLOAT);
    /** RGB (0xRRGGBB); -1 = pakai warna bawaan model. */
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(CarPropEntity.class, EntityDataSerializers.INT);
    /** gloss / metallic / matte / chrome; kosong = bawaan model. */
    private static final EntityDataAccessor<String> FINISH = SynchedEntityData.defineId(CarPropEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> LIGHTS = SynchedEntityData.defineId(CarPropEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> STEER = SynchedEntityData.defineId(CarPropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(CarPropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(CarPropEntity.class, EntityDataSerializers.FLOAT);

    public CarPropEntity(EntityType<? extends CarPropEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODEL, CarPropsConfig.get().defaultModel);
        builder.define(SCALE, 1.0f);
        builder.define(COLOR, -1);
        builder.define(FINISH, "");
        builder.define(LIGHTS, true);
        builder.define(STEER, 0.0f);
        builder.define(PITCH, 0.0f);
        builder.define(ROLL, 0.0f);
    }

    // ---------- getter / setter ----------
    public String getModelId() { return this.entityData.get(MODEL); }
    public void setModelId(String id) { this.entityData.set(MODEL, id); }

    public float getCarScale() { return this.entityData.get(SCALE); }
    public void setCarScale(float s) { this.entityData.set(SCALE, Mth.clamp(s, 0.05f, 30.0f)); }

    public int getColor() { return this.entityData.get(COLOR); }
    public void setColor(int rgb) { this.entityData.set(COLOR, rgb < 0 ? -1 : (rgb & 0xFFFFFF)); }

    public String getFinish() { return this.entityData.get(FINISH); }
    public void setFinish(String f) { this.entityData.set(FINISH, f == null ? "" : f); }

    public boolean isLightsOn() { return this.entityData.get(LIGHTS); }
    public void setLightsOn(boolean on) { this.entityData.set(LIGHTS, on); }

    public float getSteer() { return this.entityData.get(STEER); }
    public void setSteer(float deg) { this.entityData.set(STEER, Mth.clamp(deg, -45.0f, 45.0f)); }

    public float getPitchTilt() { return this.entityData.get(PITCH); }
    public void setPitchTilt(float deg) { this.entityData.set(PITCH, Mth.clamp(deg, -180.0f, 180.0f)); }

    public float getRollTilt() { return this.entityData.get(ROLL); }
    public void setRollTilt(float deg) { this.entityData.set(ROLL, Mth.clamp(deg, -180.0f, 180.0f)); }

    // ---------- kotak tabrakan (fisik) ----------
    private final List<CarColliderEntity> colliders = new ArrayList<>();

    /** Server: pastikan jumlah/ukuran/posisi kotak tabrakan sesuai model, skala, dan arah mobil. */
    private void updateColliders() {
        CarPropsConfig cfg = CarPropsConfig.get();
        CarPropsConfig.ModelDims d = CarPropsConfig.dims(getModelId());
        colliders.removeIf(Entity::isRemoved);
        int want = cfg.collision ? Math.max(1, (int) Math.ceil(d.length / d.width - 1.0e-3)) : 0;
        while (colliders.size() > want) colliders.remove(colliders.size() - 1).discard();
        while (colliders.size() < want) {
            CarColliderEntity c = new CarColliderEntity(CarProps.CAR_COLLIDER, this.level());
            c.setOwnerId(this.getId());
            colliders.add(c);
            this.level().addFreshEntity(c);
        }
        if (want == 0) return;
        float s = getCarScale();
        double yaw = Math.toRadians(this.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double step = want > 1 ? (d.length - d.width) * s / (want - 1) : 0.0;
        double start = -step * (want - 1) / 2.0;
        for (int i = 0; i < want; i++) {
            CarColliderEntity c = colliders.get(i);
            c.setOwnerId(this.getId());
            c.setBox(d.width * s, d.height * s);
            double off = start + i * step;
            c.setPos(this.getX() + fx * off, this.getY(), this.getZ() + fz * off);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) updateColliders();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!this.level().isClientSide) {
            for (CarColliderEntity c : colliders) c.discard();
            colliders.clear();
        }
        super.remove(reason);
    }

    /** Kotak besar seukuran mobil untuk culling & penargetan perintah (hitbox aslinya kecil). */
    @Override
    public AABB getBoundingBoxForCulling() {
        CarPropsConfig.ModelDims d = CarPropsConfig.dims(getModelId());
        double r = Math.max(d.length, Math.max(d.width, d.height)) * getCarScale() * 0.6 + 0.5;
        return new AABB(getX() - r, getY() - 0.5, getZ() - r, getX() + r, getY() + r * 1.2, getZ() + r);
    }

    // ---------- perilaku ----------
    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    /** Klik kanan = putar 15 derajat (sambil jongkok = putar balik arah). */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            this.setYRot(Mth.wrapDegrees(this.getYRot() + (player.isShiftKeyDown() ? -15.0f : 15.0f)));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** Creative + jongkok + pukul = hapus mobil. Selain itu kebal. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !this.isRemoved()
                && source.getEntity() instanceof Player p
                && p.getAbilities().instabuild && p.isShiftKeyDown()) {
            this.discard();
            return true;
        }
        return false;
    }

    // ---------- simpan / muat ----------
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Model")) setModelId(tag.getString("Model"));
        if (tag.contains("Scale")) setCarScale(tag.getFloat("Scale"));
        setColor(tag.contains("Color") ? tag.getInt("Color") : -1);
        setFinish(tag.getString("Finish"));
        setLightsOn(!tag.contains("Lights") || tag.getBoolean("Lights"));
        setSteer(tag.getFloat("Steer"));
        setPitchTilt(tag.getFloat("PitchTilt"));
        setRollTilt(tag.getFloat("RollTilt"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Model", getModelId());
        tag.putFloat("Scale", getCarScale());
        tag.putInt("Color", getColor());
        tag.putString("Finish", getFinish());
        tag.putBoolean("Lights", isLightsOn());
        tag.putFloat("Steer", getSteer());
        tag.putFloat("PitchTilt", getPitchTilt());
        tag.putFloat("RollTilt", getRollTilt());
    }
}
