package com.maou.maoudrift.client.hook;

import com.maou.maoudrift.client.CarCamera;
import com.maou.maoudrift.client.CarChaseCamera;
import com.maou.maoudrift.client.CarClientDriver;
import com.maou.maoudrift.client.CarCockpitCamera;
import com.maou.maoudrift.client.CarDust;
import com.maou.maoudrift.client.hud.CarGaugeHud;
import com.maou.maoudrift.client.CarGhosts;
import com.maou.maoudrift.client.hud.CarHelp;
import com.maou.maoudrift.client.hud.CarHud;
import com.maou.maoudrift.render.CarIcons;
import com.maou.maoudrift.client.CarImpact;
import com.maou.maoudrift.render.CarMeshes;
import com.maou.maoudrift.client.model.CarModel;
import com.maou.maoudrift.client.model.CarPartModel;
import com.maou.maoudrift.client.CarPresets;
import com.maou.maoudrift.client.CarSoundEvents;
import com.maou.maoudrift.client.sound.CarSoundSet;
import com.maou.maoudrift.render.ObjModel;
import com.maou.maoudrift.client.model.PlateModel;
import com.maou.maoudrift.client.model.PlateMounts;
import com.maou.maoudrift.render.PlateTextures;
import com.maou.maoudrift.render.CarSkidMarks;
import com.maou.maoudrift.client.CarSmoke;
import com.maou.maoudrift.client.CarSounds;
import com.maou.maoudrift.client.CarTurbo;
import com.maou.maoudrift.render.CourseRenderer;
import com.maou.maoudrift.render.DeferredGlows;
import com.maou.maoudrift.render.HeadlightVolume;
import com.maou.maoudrift.platform.McRaceClient;
import com.maou.maoudrift.platform.McUiGraphics;
import com.maou.maoudrift.render.MirrorRenderer;
import com.maou.maoudrift.render.PartAnchors;
import com.maou.maoudrift.client.hud.RaceHud;
import com.maou.maoudrift.client.race.RaceLobby;
import com.maou.maoudrift.render.RiderPoseRenderer;
import com.maou.maoudrift.render.SteeringHands;
import com.maou.maoudrift.render.TireSmokeVolume;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.io.IOException;

/**
 * クライアント側の割り込み先の一覧。<b>どの場面で、どのクラスを、どの順に呼ぶかはここにだけ書く。</b>
 *
 * <p>各ローダーのつなぎ（NeoForge 版はイベント、Fabric 版は Fabric API の受け口と mixin）は、
 * 場面が来たらここを呼ぶだけ。順番を 2 か所に書くと、片方だけ直して食い違う。</p>
 *
 * <p><b>メソッド名の後ろの Normal / Low / Lowest / Highest は、NeoForge の {@code EventPriority}。</b>
 * ほかの MOD のハンドラとの前後を今のまま保つために分けてある（NeoForge 版はそれぞれをその優先度で
 * 受ける）。Fabric 版はこの順に続けて呼ぶ。同じ優先度の中の順番は NeoForge では決まっていなかったので、
 * ここではクラス名の順にしてある。</p>
 */
public final class ClientHooks {

    static {
        // レースのクライアント側（core）が Minecraft に触る口を置く。最初のティックより前に読み込まれる
        McRaceClient.install();
    }

    private ClientHooks() {
    }

    /** {@code Minecraft#tick} の先頭。 */
    public static void clientTickPre() {
        CarClientDriver.onClientTick();
        CarGhosts.onClientTickPre();
    }

    /** {@code Minecraft#tick} の終わり。 */
    public static void clientTickPost() {
        CarCamera.onClientTick();
        CarDust.onClientTick();
        CarGhosts.onClientTickPost();
        CarImpact.onClientTick();
        CarSkidMarks.onClientTick();
        CarSmoke.onClientTick();
        CarSounds.onClientTick();
        CarTurbo.onClientTick();
        RaceHud.onClientTick();
        RaceLobby.onClientTick();
        TireSmokeVolume.onClientTick();
    }

    /** カメラの向き。衝突の揺れ（普通）を先に足し、そのあと視点ごとの向き（低）で上書きする。 */
    public static void computeCameraAnglesNormal(CameraAngles event) {
        CarImpact.onComputeCameraAngles(event);
    }

    public static void computeCameraAnglesLow(CameraAngles event) {
        CarChaseCamera.onComputeCameraAngles(event);
        CarCockpitCamera.onComputeCameraAngles(event);
    }

    public static void computeFov(FovModifier event) {
        CarCamera.onComputeFov(event);
    }

    /** クライアントのワールドに実体が現れた。 */
    public static void entityJoin(Entity entity, Level level) {
        CarSounds.onEntityJoin(entity, level);
    }

    /** サーバーにつないだ（ワールドに入った）。 */
    public static void loggingIn() {
        CarSkidMarks.onLoggingIn();
        CourseRenderer.onLoggingIn();
        RaceHud.onLoggingIn();
        RaceLobby.onLoggingIn();
        TireSmokeVolume.onLoggingIn();
    }

    /** サーバーから抜けた。 */
    public static void loggingOut() {
        CarGhosts.onLoggingOut();
        CarSkidMarks.onLoggingOut();
        CourseRenderer.onLoggingOut();
        RaceHud.onLoggingOut();
        RaceLobby.onLoggingOut();
        TireSmokeVolume.onLoggingOut();
    }

    /** コアシェーダーの登録（リソースを読み直すたび）。 */
    public static void registerShaders(ShaderRegistrar registrar) throws IOException {
        CarMeshes.Registration.onRegisterShaders(registrar);
        CarSkidMarks.Registration.onRegisterShaders(registrar);
        DeferredGlows.Registration.onRegisterShaders(registrar);
        HeadlightVolume.Registration.onRegisterShaders(registrar);
        TireSmokeVolume.Registration.onRegisterShaders(registrar);
    }

    /** HUD をすべて描き終えた後。 */
    public static void renderGuiPost(GuiRender event) {
        McUiGraphics graphics = new McUiGraphics(event.getGuiGraphics());
        CarGaugeHud.onRenderGui(graphics);
        CarHelp.onRenderGui(graphics);
        CarHud.onRenderGui(graphics);
        // アクションバーはいちばん最後（この MOD の HUD より手前）
        OverlayMessageFront.draw(event);
    }

    /** 車に乗っている間はバニラのホットバー（と体力・満腹度）を隠す。降りれば戻る。 */
    public static boolean hideVanillaHotbar() {
        try {
            return com.maou.maoudrift.config.ClientConfig.showGauges
                    && com.maou.maoudrift.client.CarClient.get().ridingHud() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** ホットバーの層を描き終えた直後（チャットなどより下）。 */
    public static void renderHotbarPost(GuiRender event) {
        McUiGraphics graphics = new McUiGraphics(event.getGuiGraphics());
        RaceHud.onRenderOverlay(graphics, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        RaceLobby.onRenderOverlay(graphics);
    }

    /** 一人称の手を 1 本描く前。 */
    public static void renderHand(HandRender event) {
        SteeringHands.onRenderHand(event);
    }

    /** ワールドの描画の段（{@link LevelRenderStage}）。 */
    public static void renderLevelStageNormal(LevelRenderStage event) {
        HeadlightVolume.onRenderLevel(event);
        PartAnchors.onRenderLevel(event);
        RiderPoseRenderer.onRenderLevelStage(event);
    }

    public static void renderLevelStageLow(LevelRenderStage event) {
        CarSkidMarks.onRenderLevel(event);
        CourseRenderer.onRenderLevel(event);
        TireSmokeVolume.onRenderLevel(event);
    }

    /** 光芒・灯火の仕上げ。ほかのすべての描き込みの後。 */
    public static void renderLevelStageLowest(LevelRenderStage event) {
        DeferredGlows.onRenderLevel(event);
    }

    /** 生き物を描く前。ほかの MOD が姿勢をいじり終えた後に足すので最低。 */
    public static void renderLivingPreLowest(LivingRender event) {
        RiderPoseRenderer.onRenderLivingPre(event);
    }

    /** 生き物を描いた後。積んだ姿勢をほかの MOD より先に戻すので最高。 */
    public static void renderLivingPostHighest(LivingRender event) {
        RiderPoseRenderer.onRenderLivingPost(event);
    }

    /** 1 フレームを描き始める前。 */
    public static void renderFramePre(FrameRender event) {
        CarIcons.onRenderTick(event);
        MirrorRenderer.onRenderTick(event);
    }

    /**
     * リソースを読み直した（F3+T など）。OBJ と見た目の定義も読み直す。
     *
     * <p>これがあると、Blender から書き出し直して<b>ゲーム内で F3+T を押すだけ</b>で
     * 新しいモデルが反映される。モデルを詰めている間はこれが効く。</p>
     */
    public static void resourcesReloaded() {
        ObjModel.clearCache();
        CarModel.clearCache();
        CarSoundEvents.clearReports();
        CarPartModel.clearCache();
        CarPresets.clearCache();
        CarIcons.clearCache();
        PlateModel.clearCache();
        PlateMounts.clearCache();
        PlateTextures.clearCache();
    }

    /** 音のエンジンを作った・読み直した直後。 */
    public static void soundEngineLoad() {
        CarSounds.EngineReload.onSoundEngineLoad();
    }
}
