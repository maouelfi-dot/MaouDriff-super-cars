package com.maou.maoudrift.platform;

import com.maou.maoudrift.client.input.CarDriver;
import com.maou.maoudrift.car.CarId;
import com.maou.maoudrift.entity.CarEntity;
import com.maou.maoudrift.part.PartFitment;
import com.maou.maoudrift.part.PartSlot;
import com.maou.maoudrift.physics.CarSpec;
import com.maou.maoudrift.plate.PlateLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import com.maou.maoudrift.client.model.CarPartModel;
import com.maou.maoudrift.client.sound.CarSoundSet;
import com.maou.maoudrift.client.hud.HudCar;
import com.maou.maoudrift.client.CarCamera;
import com.maou.maoudrift.client.CarChaseCamera;
import com.maou.maoudrift.client.CarClient;
import com.maou.maoudrift.client.CarClientDriver;
import com.maou.maoudrift.render.CarObjRenderer;
import com.maou.maoudrift.client.CarSound;
import com.maou.maoudrift.client.CarTurbo;
import com.maou.maoudrift.client.DrivenCar;
import com.maou.maoudrift.render.HeadlightVolume;
import com.maou.maoudrift.client.MaouDriftKeys;
import com.maou.maoudrift.render.ObjModel;
import com.maou.maoudrift.render.PartAnchors;

/** core の {@link CarClient} の Minecraft 1.21.1 版。 */
final class McCarClient implements CarClient {

    @Override
    public boolean shaderPackInUse() {
        return HeadlightVolume.ShaderPacks.inUse();
    }

    @Override
    public boolean headlightSuppressedByShaderPack() {
        return HeadlightVolume.suppressedByShaderPack();
    }

    private static CarEntity ridingEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getVehicle() instanceof CarEntity car ? car : null;
    }

    @Override
    public List<HelpLine> helpLines() {
        List<HelpLine> lines = new ArrayList<>();
        lines.add(new HelpLine(key(Minecraft.getInstance().options.keyUp) + "/" + key(Minecraft.getInstance().options.keyDown),
                "help.maoudrift.pedals"));
        lines.add(new HelpLine(key(Minecraft.getInstance().options.keyLeft) + "/" + key(Minecraft.getInstance().options.keyRight),
                "help.maoudrift.steer"));
        lines.add(new HelpLine(key(Minecraft.getInstance().options.keyJump), "help.maoudrift.handbrake"));
        lines.add(new HelpLine(key(MaouDriftKeys.SHIFT_UP) + "/" + key(MaouDriftKeys.SHIFT_DOWN),
                "help.maoudrift.shift"));
        lines.add(new HelpLine(key(Minecraft.getInstance().options.keyShift), "help.maoudrift.dismount"));
        lines.add(new HelpLine(key(MaouDriftKeys.OPEN_TUNING), "help.maoudrift.tuning"));
        lines.add(new HelpLine(key(MaouDriftKeys.OPEN_MENU), "help.maoudrift.menu"));
        lines.add(new HelpLine(key(MaouDriftKeys.OPEN_RACE), "help.maoudrift.race"));
        lines.add(new HelpLine(key(MaouDriftKeys.TOGGLE_HEADLIGHTS), "help.maoudrift.headlights"));
        lines.add(new HelpLine(key(MaouDriftKeys.OPEN_HEADLIGHT_SETTINGS), "help.maoudrift.headlight_settings"));
        lines.add(new HelpLine(key(MaouDriftKeys.TOGGLE_CAMERA), "help.maoudrift.camera"));
        lines.add(new HelpLine(key(MaouDriftKeys.CAMERA_CLOSER) + "/" + key(MaouDriftKeys.CAMERA_FARTHER),
                "help.maoudrift.camera_distance"));
        lines.add(new HelpLine(key(MaouDriftKeys.OPEN_CONTROLLER), "help.maoudrift.controller"));
        return lines;
    }

    /** キーの表示名。割り当てを変えたらそのまま追従する。 */
    private static String key(net.minecraft.client.KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }

    @Override
    public HudCar ridingHud() {
        return ridingEntity();
    }

    @Override
    public double turboBoost() {
        CarEntity car = ridingEntity();
        return car == null ? 0.0 : CarTurbo.boost(car, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }

    @Override
    public com.maou.maoudrift.config.CameraMode cameraMode() {
        return CarCamera.currentMode();
    }

    @Override
    public String cameraKeyName() {
        return key(MaouDriftKeys.TOGGLE_CAMERA);
    }

    @Override
    public CarId ridingCarId() {
        CarEntity car = ridingEntity();
        return car == null ? null : car.getCarId();
    }

    @Override
    public CarSoundSet ridingCarSounds() {
        CarEntity car = ridingEntity();
        return car == null ? null : CarSound.soundsOf(car);
    }

    @Override
    public boolean ridingCarHasTurbo() {
        CarEntity car = ridingEntity();
        return car != null && car.hasTurbo();
    }

    @Override
    public boolean ridingCarHasBlowoff() {
        CarEntity car = ridingEntity();
        return car != null && car.hasBlowoff();
    }

    @Override
    public void setClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    /** 車を core の {@link DrivenCar} として包む。 */
    static DrivenCar wrap(CarEntity car) {
        return car == null ? null : new DrivenCar() {
            @Override
            public int getId() {
                return car.getId();
            }

            @Override
            public CarId getCarId() {
                return car.getCarId();
            }

            @Override
            public CarSpec getSpec() {
                return car.getSpec();
            }

            @Override
            public PartFitment getFitment() {
                return car.getFitment();
            }

            @Override
            public PartFitment getEffectiveFitment() {
                return car.getEffectiveFitment();
            }

            @Override
            public void setFitment(PartFitment fitment) {
                car.setFitment(fitment);
            }

            @Override
            public boolean hasTurbo() {
                return car.hasTurbo();
            }

            @Override
            public boolean hasBlowoff() {
                return car.hasBlowoff();
            }

            @Override
            public boolean same(DrivenCar other) {
                return other != null && other.getId() == car.getId();
            }
        };
    }

    @Override
    public DrivenCar riding() {
        return wrap(CarClientDriver.ridingCar(Minecraft.getInstance()));
    }

    @Override
    public DrivenCar driving() {
        return wrap(CarClientDriver.driving());
    }

    @Override
    public boolean isDriver() {
        return CarDriver.isDriver();
    }

    @Override
    public void requestPart(PartSlot slot, PartFitment.Setting setting) {
        CarDriver.requestPart(slot, setting);
    }

    @Override
    public void requestPaint(String paint) {
        CarDriver.requestPaint(paint);
    }

    @Override
    public void requestPlate(PlateLook plate) {
        CarDriver.requestPlate(plate);
    }

    @Override
    public void sendTuningToServer() {
        CarDriver.sendTuningToServer();
    }

    @Override
    public void viewPassengerCar(DrivenCar car) {
        CarEntity entity = CarClientDriver.ridingCar(Minecraft.getInstance());
        if (entity != null && car != null && entity.getId() == car.getId()) {
            CarDriver.viewPassengerCar(entity);
        }
    }

    @Override
    public void orbitCamera(double dragX, double dragY) {
        CarChaseCamera.orbit(dragX, dragY);
    }

    @Override
    public void zoomCamera(double delta) {
        CarChaseCamera.zoom(delta);
    }

    @Override
    public float[] partAnchor(PartSlot slot) {
        return PartAnchors.point(slot);
    }

    @Override
    public float[] paintAnchor() {
        return PartAnchors.paint();
    }

    @Override
    public float[] centerAnchor() {
        return PartAnchors.center();
    }

    @Override
    public double beadRadius(CarPartModel.Wheel look) {
        return CarObjRenderer.beadRadius(look, ObjModel.get(look.model()));
    }

    @Override
    public boolean shiftDown() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    @Override
    public boolean firstPerson() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options.getCameraType().isFirstPerson();
    }

    @Override
    public void turnPlayer(double yaw, double pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.turn(yaw, pitch);
        }
    }

    @Override
    public double eyeAboveVehicle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.getVehicle() == null) {
            return Double.NaN;
        }
        return minecraft.player.getEyeY() - minecraft.player.getVehicle().getY();
    }

    @Override
    public void returnTo(Object parent) {
        Minecraft.getInstance().setScreen(parent instanceof com.maou.maoudrift.client.ui.UiScreen ui
                ? new McUiScreen(ui) : (Screen) parent);
    }
}
