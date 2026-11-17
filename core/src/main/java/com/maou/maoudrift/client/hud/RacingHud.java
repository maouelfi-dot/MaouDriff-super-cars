package com.maou.maoudrift.client.hud;

import com.maou.maoudrift.client.ui.UiFont;
import com.maou.maoudrift.client.ui.UiGraphics;
import com.maou.maoudrift.config.ClientConfig;
import com.maou.maoudrift.config.SpeedUnit;
import com.maou.maoudrift.physics.CarSpec;

/**
 * Maou Drift 独自のレーシング HUD（原作のアナログ 2 眼メーターの置き換え）。
 *
 * <p>画面の下の中央に 1 つにまとめる。上から: 斜めの LED 式回転バー（緑→黄→赤、レブ付近で点滅）、
 * 大きな速度表示と段、左にブレーキ/アクセルの縦バー、右に回転数・横 G・ドリフト角と警告灯。
 * 画像は使わず四角形と文字だけなので軽い。</p>
 */
public final class RacingHud {

    private static final int SEGMENTS = 30;
    private static final float WIDTH = 300.0F;

    private static final int C_PLATE = 0xC00A0C12;
    private static final int C_PLATE_EDGE = 0xFF1B2030;
    private static final int C_ACCENT = 0xFFFF6A00;
    private static final int C_TEXT = 0xFFF2F5FA;
    private static final int C_DIM = 0xFF8A93A6;
    private static final int C_OFF = 0x50303848;
    private static final int C_COOL = 0xFF37D8FF;
    private static final int C_AMBER = 0xFFFFB41F;
    private static final int C_RED = 0xFFFF2B3A;
    private static final int C_GREEN = 0xFF3BE37A;

    private RacingHud() {
    }

    /** 描いた塊の上端 [GUI 座標]（アクションバーを避けるのに使う）。 */
    public static float render(UiGraphics g, UiFont font, int screenW, int screenH, HudCar car) {
        float fit = (screenW - 16.0F) / WIDTH;
        float base = Math.max(0.5F, (float) Math.floor(Math.min(1.5F, fit) / 0.25F) * 0.25F);
        float s = base * (float) ClientConfig.gaugeScale;
        float totalH = 74.0F;
        float left = screenW * 0.5F - WIDTH * 0.5F * s;
        float top = screenH - (totalH + 6.0F) * s;

        CarSpec spec = car.getSpec();
        double redline = spec.redlineRpm();
        double topRpm = Math.ceil((redline + 500.0) / 1000.0) * 1000.0;
        double rpm = car.getRenderRpm();
        SpeedUnit unit = SpeedUnit.current();
        int speed = (int) Math.round(unit.fromMetersPerSecond(Math.abs(car.getRenderSpeed())));
        int gear = car.getRenderGear();
        boolean flash = rpm >= redline * 0.97 && ((System.currentTimeMillis() / 70L) & 1L) == 0L;

        g.pushPose();
        g.translateZ(50.0F);
        g.translate(left, top);
        g.scale(s, s);

        // --- 回転 LED バー ---
        float segW = (WIDTH - 8.0F) / SEGMENTS;
        double frac = Math.min(1.0, rpm / topRpm);
        double redFrom = redline / topRpm;
        for (int i = 0; i < SEGMENTS; i++) {
            double p = (i + 0.5) / SEGMENTS;
            boolean lit = flash || p <= frac;
            int c = p >= redFrom ? C_RED : (p >= redFrom - 0.18 ? C_AMBER : C_COOL);
            slant(g, 4.0F + i * segW, 0.0F, segW - 1.6F, 10.0F, 3.0F, lit ? c : (p >= redFrom ? 0x50601820 : C_OFF));
        }

        // --- 下の板 ---
        slant(g, 0.0F, 14.0F, WIDTH, 52.0F, 8.0F, C_PLATE);
        slant(g, 0.0F, 14.0F, WIDTH, 2.0F, 8.0F, C_ACCENT);
        slant(g, 0.0F, 64.0F, WIDTH, 2.0F, 8.0F, C_PLATE_EDGE);

        // 段の箱
        slant(g, 96.0F, 20.0F, 44.0F, 40.0F, 5.0F, C_ACCENT);
        String gs = gear > 0 ? Integer.toString(gear) : (gear < 0 ? "R" : "N");
        text(g, font, gs, 118.0F + 2.5F, 24.0F, 4.0F, 0xFF10131A, 0);

        // 速度
        text(g, font, Integer.toString(speed), 232.0F - 54.0F, 22.0F, 4.0F, C_TEXT, 1);
        g.flush();
        g.pushPose();
        g.translate(232.0F - 50.0F, 46.0F);
        g.scale(1.0F, 1.0F);
        g.drawString(font, unit.unit(), 0, 0, C_DIM, false);
        g.popPose();

        // 左: ブレーキ・アクセル縦バー
        bar(g, 14.0F, 22.0F, 8.0F, 36.0F, car.getRenderBrake(), C_RED);
        bar(g, 26.0F, 22.0F, 8.0F, 36.0F, car.getRenderThrottle(), C_GREEN);
        text(g, font, "B", 18.0F, 59.0F, 0.75F, C_DIM, 0);
        text(g, font, "T", 30.0F, 59.0F, 0.75F, C_DIM, 0);

        // 回転数 / 横 G / ドリフト角
        text(g, font, String.format("%.1f", rpm / 1000.0), 82.0F, 18.0F, 1.5F, flash ? C_RED : C_TEXT, 2);
        text(g, font, "x1000 RPM", 82.0F, 33.0F, 0.75F, C_DIM, 2);
        double gForce = Math.abs(car.getRenderLateralAcceleration()) / 9.81;
        text(g, font, String.format("%.2f G", gForce), 82.0F, 44.0F, 1.0F, C_TEXT, 2);
        int drift = (int) Math.round(Math.abs(car.getRenderSlipAngleDegrees()));
        text(g, font, "DRIFT " + drift + "°", 82.0F, 54.0F, 1.0F, drift > 8 ? C_AMBER : C_DIM, 2);

        // 右: 警告灯
        float lx = 244.0F;
        chip(g, font, "ABS", lx, 20.0F, car.isAbsActive(), C_AMBER);
        chip(g, font, "TCS", lx + 26.0F, 20.0F, car.isTractionControlActive(), C_AMBER);
        chip(g, font, "HB", lx, 34.0F, car.isHandbrakeOn(), C_RED);
        chip(g, font, "TURBO", lx + 26.0F, 34.0F, car.hasTurbo() && car.getRenderThrottle() > 0.6, C_COOL);
        g.flush();
        g.popPose();
        return top;
    }

    /** 右に傾いた長方形（斜め 45 度ではなく {@code slant} だけ上を右へずらす）。 */
    private static void slant(UiGraphics g, float x, float y, float w, float h, float slant, int color) {
        g.fillQuad(color, x, y + h, x + w, y + h, x + w + slant, y, x + slant, y);
    }

    private static void bar(UiGraphics g, float x, float y, float w, float h, double v, int color) {
        float f = (float) Math.max(0.0, Math.min(1.0, v));
        g.fillQuad(C_OFF, x, y + h, x + w, y + h, x + w, y, x, y);
        if (f > 0.0F) {
            g.fillQuad(color, x, y + h, x + w, y + h, x + w, y + h - h * f, x, y + h - h * f);
        }
    }

    private static void chip(UiGraphics g, UiFont font, String label, float x, float y, boolean on, int color) {
        slant(g, x, y, 24.0F, 11.0F, 2.0F, on ? color : C_OFF);
        text(g, font, label, x + 13.0F, y + 2.0F, 0.75F, on ? 0xFF10131A : C_DIM, 0);
    }

    /** align: 0 中央、1 右寄せ、2 右端が x、それ以外 左。 */
    private static void text(UiGraphics g, UiFont font, String t, float x, float y, float scale, int color, int align) {
        g.flush();
        g.pushPose();
        g.translate(x, y);
        g.scale(scale, scale);
        int w = font.width(t);
        int ox = align == 0 ? -w / 2 : (align == 1 || align == 2 ? -w : 0);
        g.drawString(font, t, ox, 0, color, false);
        g.popPose();
    }
}
