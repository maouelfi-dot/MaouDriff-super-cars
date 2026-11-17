package com.maou.maoudrift.client;

import com.maou.maoudrift.car.CarType;
import com.maou.maoudrift.car.CarTypes;
import com.maou.maoudrift.entity.CarEntity;
import com.maou.maoudrift.network.CourseLinesPacket;
import com.maou.maoudrift.network.GhostRidePacket;
import com.maou.maoudrift.network.RaceEventPacket;
import com.maou.maoudrift.network.RaceStandingsPacket;
import com.maou.maoudrift.part.CarPart;
import com.maou.maoudrift.part.CarParts;
import com.maou.maoudrift.part.PartFitment;
import com.maou.maoudrift.physics.CarSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.List;
import com.maou.maoudrift.client.hud.RaceHud;
import com.maou.maoudrift.client.race.RaceLobby;
import com.maou.maoudrift.client.screen.TimeAttackScreen;
import com.maou.maoudrift.render.CourseRenderer;

/**
 * サーバーから届いたパケットのクライアント側の処理。
 *
 * <p>{@code net.minecraft.client.*} を参照するので、専用サーバーでは絶対に読み込まれないよう
 * <b>クライアントでしか走らない処理の中からだけ</b>呼ぶこと（パケットのクライアント側の処理など）。</p>
 */
public final class CarClientPackets {

    private CarClientPackets() {
    }

    /** レース中の順位表を受け取る。 */
    /** 運転者の画面で走っているゴーストの 1 コマ（助手席で受け取る）。 */
    public static void applyGhostRide(GhostRidePacket packet) {
        CarGhosts.applyRide(packet);
    }

    public static void applyStandings(RaceStandingsPacket packet) {
        RaceHud.acceptStandings(packet);
    }

    /** コースの線を受け取る。ゲートとして描く。 */
    public static void applyCourseLines(CourseLinesPacket packet) {
        CourseRenderer.accept(packet);
    }

    /** レースの様子（受付中か・決まり・参加者と判定）を受け取る。 */
    public static void applyRaceLobby(com.maou.maoudrift.network.RaceLobbyPacket packet) {
        RaceLobby.accept(packet);
    }

    /** サーバーに保存してあるクラスの一覧を受け取る。 */
    public static void applyRaceClasses(com.maou.maoudrift.network.RaceClassesPacket packet) {
        RaceLobby.acceptClasses(packet.classes());
    }

    /** タイムアタックの記録を受け取る。開いている K の画面へ渡す。 */
    public static void applyTimeAttackRecords(com.maou.maoudrift.network.TimeAttackRecordsPacket packet) {
        TimeAttackScreen.acceptRecords(packet);
    }

    /** ラップ計測とスタート信号の合図を受け取る。 */
    public static void applyRaceEvent(RaceEventPacket packet) {
        RaceHud.accept(packet);
        CarGhosts.onRaceEvent(packet);
    }

    /**
     * 壁と擦れている車から火花を出す。運転者本人は自分の画面で出しているので、
     * 中継されて戻ってきたものは捨てる（衝突と同じ）。
     */
    public static void applyScrape(int entityId, float[] offset, float[] normal, float[] velocity,
                                   float impact) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity instanceof CarEntity car && !car.isControlledByLocalInstance()) {
            CarSparks.emit(car, offset, normal, velocity, impact);
        }
    }

    /**
     * 衝突の合図を受け取る。
     *
     * <p><b>運転している本人のぶんは捨てる。</b>本人は物理を解いた時点で自分の画面に
     * 鳴らしているので、中継されて戻ってきたものを鳴らすと二重になる。</p>
     */
    public static void applyImpact(int entityId, float impact) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity instanceof CarEntity car && !car.isControlledByLocalInstance()) {
            CarImpact.onImpact(car, impact);
        }
    }

    /**
     * ぶつけられた側の車を弾く。
     *
     * <p>物理を解いているのは<b>その車を運転している自分</b>だけなので、
     * {@code applyPush} 側で手元の車かどうかを見て弾く。関係ない車のぶんが届いても捨てる。</p>
     */
    public static void applyPush(int entityId, float dx, float dz, float dYawRate) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity instanceof CarEntity car) {
            car.applyPush(dx, dz, dYawRate);
        }
    }

    /**
     * 読み込まれている車種の一覧を受け取る。
     *
     * <p>受け取ったらクリエイティブタブを組み直す。<b>タブの中身はログインより前に
     * 作られていることがある</b>ので、後から届いた車種はそのままでは並ばない。</p>
     */
    public static void applyCarTypes(List<CarType> types) {
        CarTypes.replaceAll(types);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            CreativeModeTabs.tryRebuildTabContents(
                    minecraft.player.connection.enabledFeatures(),
                    minecraft.player.canUseGameMasterBlocks(),
                    minecraft.player.level().registryAccess());
        }
    }

    /**
     * 選べる部品の一覧を受け取る。
     *
     * <p>車種と違ってクリエイティブタブには並ばないので、入れ替えるだけでよい。
     * 換装画面（{@code CarPartScreen}）は開くたびにここから引く。</p>
     */
    public static void applyCarParts(List<CarPart> parts) {
        CarParts.replaceAll(parts);
    }

    /**
     * 車 1 台の装着状態を受け取る。
     *
     * <p><b>運転している本人のぶんも捨てない。</b>諸元は調整画面が正なので送り返された
     * ぶんを捨てるが、装着状態を決めるのは<b>サーバー</b>（部品の一覧と突き合わせて弾く）。
     * 届いたものが正なので、そのまま入れる。</p>
     */
    public static void applyParts(int entityId, PartFitment parts) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity instanceof CarEntity car) {
            car.setFitment(parts);
        }
    }

    /** 車の諸元を受け取る。 */
    public static void applySpec(int entityId, CarSpec spec) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        Entity entity = Minecraft.getInstance().level.getEntity(entityId);
        if (entity instanceof CarEntity car && !car.isControlledByLocalInstance()) {
            // 運転している本人は自分の調整画面が正なので、送り返されたぶんは捨てる
            car.setSpec(spec);
        }
    }
}
