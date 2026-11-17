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


import java.util.List;
import com.maou.maoudrift.client.race.RaceClient;
import com.maou.maoudrift.client.race.RaceLobby;

/**
 * K の画面の上のタブ（「タイムアタック」「レース」「クラス」）。
 *
 * <p>レースのタブは、レースが無ければ「レースを開く」（主催者画面）、あればそのレースの画面（主催者なら主催者画面）。
 * 3 つの画面がそれぞれ左上に置く。クラスのタブは、クラスを保存・削除できる人（OP とシングルプレイの本人）にだけ出す。</p>
 */
final class RaceTabs {

    static final int HEIGHT = 16;

    private RaceTabs() {
    }

    /** どのタブの画面か。 */
    enum Page {
        // クラスは右端。OP にしか出ないので、出ても出なくてもほかのタブの位置が変わらないように
        TIME_ATTACK, RACE, CLASSES
    }

    /** タブを作る。{@code current} は今の画面。並べた右端は {@link #right}。 */
    static List<Tab> create(int x, int y, Page current) {
        UiFont font = RaceClient.get().font();
        RaceText[] labels = {
                RaceText.translatable("screen.maoudrift.tab.time_attack"),
                RaceText.translatable(RaceLobby.lobby() == null
                        ? "screen.maoudrift.tab.race_open" : "screen.maoudrift.tab.race"),
                RaceText.translatable("screen.maoudrift.tab.classes")};
        Runnable[] opens = {
                () -> RaceClient.get().setScreen(new TimeAttackScreen()),
                () -> RaceLobby.openRaceTab(),
                () -> RaceClient.get().setScreen(RaceOrganizerScreen.classes(null))};
        List<Tab> tabs = new java.util.ArrayList<>();
        int at = x;
        for (Page page : Page.values()) {
            // クラスの管理は OP（とシングルプレイの本人）だけ。ほかの人はタイムアタックのタブで中身を見る
            if (page == Page.CLASSES && !RaceLobby.classAdmin() && current != Page.CLASSES) {
                continue;
            }
            int w = font.width(labels[page.ordinal()]) + 16;
            tabs.add(new Tab(at, y, w, labels[page.ordinal()], page == current, opens[page.ordinal()]));
            at += w + 2;
        }
        return tabs;
    }

    static int right(List<Tab> tabs) {
        Tab last = tabs.get(tabs.size() - 1);
        return last.getX() + last.getWidth();
    }

    /** タブ 1 つ。選んでいるものは白地に黒、ほかは暗い地。 */
    static final class Tab extends UiPressable {

        private final boolean selected;
        private final Runnable open;

        private Tab(int x, int y, int w, RaceText label, boolean selected, Runnable open) {
            super(x, y, w, HEIGHT, label);
            this.selected = selected;
            this.open = open;
        }

        @Override
        public void onPress() {
            if (!selected) {
                open.run();
            }
        }

        @Override
        public void renderWidget(UiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            UiFont font = RaceClient.get().font();
            int color = selected ? RaceLobby.WHITE : isHovered() ? 0xE0333333 : RaceLobby.PANEL;
            Slant.fill(graphics, getX(), getY(), getWidth(), getHeight(), color);
            if (selected) {
                graphics.fill(getX() + 4, getY() + getHeight() - 2, getX() + getWidth() - 4, getY() + getHeight(),
                        RaceLobby.ACCENT);
            }
            Slant.text(graphics, font, getMessage(), getX() + 8, getY() + 4,
                    selected ? RaceLobby.INK : RaceLobby.GREY);
        }

    }
}
