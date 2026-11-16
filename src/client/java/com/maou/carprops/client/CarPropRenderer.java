package com.maou.carprops.client;

import com.maou.carprops.CarPropEntity;
import com.maou.carprops.CarPropsConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Menggambar model OBJ lewat RenderType entity vanilla (cutout + translucent), jadi shader (Iris/Oculus)
 * memperlakukannya sebagai entity biasa dan membaca tekstur labPBR "_s" untuk kilap/logam/emisi.
 * Segitiga dikirim sebagai quad degenerat (titik ke-3 diulang) karena RenderType entity berbentuk QUADS.
 */
public class CarPropRenderer extends EntityRenderer<CarPropEntity> {

    public CarPropRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(CarPropEntity entity) {
        return MissingTextureAtlasSprite.getLocation();
    }

    @Override
    public boolean shouldRender(CarPropEntity e, Frustum frustum, double camX, double camY, double camZ) {
        double max = CarPropsConfig.get().renderDistance;
        if (e.distanceToSqr(camX, camY, camZ) > max * max) return false;
        return frustum.isVisible(e.getBoundingBoxForCulling().inflate(0.5));
    }

    @Override
    public void render(CarPropEntity e, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        CarModelDef def = CarModelManager.get(e.getModelId());
        if (def == null) return;

        float scale = e.getCarScale();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dist = Math.sqrt(e.distanceToSqr(cam.x, cam.y, cam.z));
        ObjModel m = CarModelManager.model(def, CarModelManager.chooseLod(def, dist, scale));
        if (m == null) return; // masih dimuat di thread lain
        ResourceLocation tex = CarModelManager.texture(def);

        int tint = e.getColor() >= 0 ? e.getColor() : def.defaultColor;
        int tr = (tint >> 16) & 255, tg = (tint >> 8) & 255, tb = tint & 255;
        String fin = e.getFinish().isEmpty() ? def.defaultFinish : e.getFinish();
        float[] finUv = def.finishes.get(fin);
        boolean lightsOn = e.isLightsOn();
        float steer = e.getSteer();
        int glassA = Mth.clamp((int) (CarPropsConfig.get().glassAlpha * 255.0), 0, 255);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, e.yRotO, e.getYRot())));

        float pitch = e.getPitchTilt(), roll = e.getRollTilt();
        if (pitch != 0.0f || roll != 0.0f) {
            float cy = (m.minY + m.maxY) * 0.5f * scale * def.modelScale;
            pose.translate(0.0, cy, 0.0);
            pose.mulPose(Axis.XP.rotationDegrees(-pitch)); // pitch + = hidung naik
            pose.mulPose(Axis.ZP.rotationDegrees(-roll));  // roll + = miring ke kiri
            pose.translate(0.0, -cy, 0.0);
        }
        pose.scale(scale, scale, scale);
        pose.translate(def.offset[0], def.offset[1], def.offset[2]);
        if (def.rotY != 0.0f) pose.mulPose(Axis.YP.rotationDegrees(def.rotY));
        if (def.modelScale != 1.0f) pose.scale(def.modelScale, def.modelScale, def.modelScale);

        // lintasan 1: semua kecuali kaca (cutout), lintasan 2: kaca (translucent, digambar terakhir)
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
        drawParts(m, def, pose, solid, false, steer, tr, tg, tb, glassA, finUv, lightsOn, light);
        boolean hasGlass = false;
        for (ObjModel.Part p : m.parts) for (ObjModel.Group g : p.groups) if ((g.flags & ObjModel.FLAG_GLASS) != 0) hasGlass = true;
        if (hasGlass) {
            VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(tex));
            drawParts(m, def, pose, glass, true, steer, tr, tg, tb, glassA, finUv, lightsOn, light);
        }
        pose.popPose();
    }

    private static void drawParts(ObjModel m, CarModelDef def, PoseStack pose, VertexConsumer vc, boolean glassPass,
                                  float steer, int tr, int tg, int tb, int glassA, float[] finUv,
                                  boolean lightsOn, int light) {
        for (ObjModel.Part part : m.parts) {
            boolean steered = steer != 0.0f && def.steerParts.contains(part.name);
            if (steered) {
                pose.pushPose();
                pose.translate(part.cx, 0.0, part.cz);
                pose.mulPose(Axis.YP.rotationDegrees(steer)); // steer + = belok kiri
                pose.translate(-part.cx, 0.0, -part.cz);
            }
            PoseStack.Pose last = pose.last();
            for (ObjModel.Group g : part.groups) {
                boolean isGlass = (g.flags & ObjModel.FLAG_GLASS) != 0;
                if (isGlass != glassPass) continue;
                int r = 255, gr = 255, b = 255, a = 255, l = light;
                float fu = 0, fv = 0;
                boolean overrideUv = false;
                if ((g.flags & ObjModel.FLAG_PAINT) != 0) {
                    r = tr; gr = tg; b = tb;
                    if (finUv != null) { overrideUv = true; fu = finUv[0]; fv = finUv[1]; }
                }
                if (isGlass) a = glassA;
                if ((g.flags & ObjModel.FLAG_LIGHT) != 0 && lightsOn) l = LightTexture.FULL_BRIGHT;
                emit(vc, last, g, r, gr, b, a, l, overrideUv, fu, fv);
            }
            if (steered) pose.popPose();
        }
    }

    private static void emit(VertexConsumer vc, PoseStack.Pose pose, ObjModel.Group g,
                             int r, int gr, int b, int a, int light, boolean overrideUv, float fu, float fv) {
        final float[] pos = g.pos, uv = g.uv, nrm = g.nrm;
        for (int i = 0; i < g.verts; i += 3) {
            for (int k = 0; k < 4; k++) {
                int vi = i + (k == 3 ? 2 : k);
                int p3 = vi * 3, p2 = vi * 2;
                vc.addVertex(pose, pos[p3], pos[p3 + 1], pos[p3 + 2])
                        .setColor(r, gr, b, a)
                        .setUv(overrideUv ? fu : uv[p2], overrideUv ? fv : uv[p2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, nrm[p3], nrm[p3 + 1], nrm[p3 + 2]);
            }
        }
    }
}
