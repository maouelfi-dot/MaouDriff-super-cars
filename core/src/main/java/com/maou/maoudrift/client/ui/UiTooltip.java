package com.maou.maoudrift.client.ui;

import com.maou.maoudrift.race.RaceText;

/** カーソルを乗せたときの説明。バニラの {@code Tooltip} の代わり。 */
public record UiTooltip(RaceText text) {

    public static UiTooltip create(RaceText text) {
        return new UiTooltip(text);
    }
}
