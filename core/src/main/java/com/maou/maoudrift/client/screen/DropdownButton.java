package com.maou.maoudrift.client.screen;

import com.maou.maoudrift.client.ui.Slant;
import com.maou.maoudrift.client.ui.UiButton;
import com.maou.maoudrift.client.ui.UiEditBox;
import com.maou.maoudrift.client.ui.UiFont;
import com.maou.maoudrift.client.ui.UiGraphics;
import com.maou.maoudrift.client.ui.UiKey;
import com.maou.maoudrift.client.ui.UiPressable;
import com.maou.maoudrift.client.ui.UiScreen;
import com.maou.maoudrift.client.ui.UiTooltip;
import com.maou.maoudrift.race.RaceCar;
import com.maou.maoudrift.race.RaceEngine;
import com.maou.maoudrift.race.RaceText;


import java.util.function.BooleanSupplier;

/**
 * ドロップダウンを開くボタン（「トラック ▼」「クラス ▼」）。<b>「▼」は右端に固定し、名前だけをその左で流す。</b>
 *
 * <p>名前の後ろに「▼」を付けた字にすると、長い名前を流している間は「▼」も一緒に流れて見えなくなる
 * （狭い画面ではほとんどの時間見えない）。</p>
 */
final class DropdownButton extends UiButton {

    /** 右端の「▼」の列の幅。 */
    private static final int ARROW = 12;

    private final BooleanSupplier open;

    DropdownButton(int x, int y, int width, int height, BooleanSupplier open, OnPress onPress) {
        super(x, y, width, height, RaceText.empty(), onPress);
        this.open = open;
    }

    @Override
    public boolean customLabel() {
        return true;
    }

    @Override
    public void renderLabel(UiGraphics graphics, UiFont font, int color) {
        int right = getX() + getWidth() - ARROW - 2;
        graphics.renderScrollingString(font, getMessage(), getX() + 4, getY(), right, getY() + getHeight(), color);
        String arrow = open.getAsBoolean() ? "▲" : "▼";
        graphics.drawString(font, arrow, getX() + getWidth() - ARROW + (ARROW - font.width(arrow)) / 2 - 2,
                getY() + (getHeight() - 8) / 2, color, false);
    }
}
