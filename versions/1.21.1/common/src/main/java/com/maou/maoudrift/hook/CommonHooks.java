package com.maou.maoudrift.hook;

import com.maou.maoudrift.CarRuleCommands;
import com.maou.maoudrift.car.CarRules;
import com.maou.maoudrift.Config;
import com.maou.maoudrift.network.CarPartTypesPacket;
import com.maou.maoudrift.network.CarRulesPacket;
import com.maou.maoudrift.network.CarTypesPacket;
import com.maou.maoudrift.network.MaouDriftNetwork;
import com.maou.maoudrift.race.RaceCommands;
import com.maou.maoudrift.race.RaceManager;
import com.maou.maoudrift.race.RaceSync;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * サーバー側（統合サーバーを含む）の割り込み先の一覧。<b>どの場面で、どのクラスを、どの順に呼ぶかはここにだけ書く。</b>
 *
 * <p>各ローダーのつなぎ（NeoForge 版は {@code NeoForgeCommonEvents}、Fabric 版は {@code MaouDriftFabric}）は、
 * 場面が来たらここを呼ぶだけ。クライアント側は {@code client.hook.ClientHooks}。</p>
 */
public final class CommonHooks {

    private CommonHooks() {
    }

    /** サーバーが起動する。 */
    public static void serverStarting() {
        // 走行のルールはサーバーの設定が正。ここで一度移しておき、以降は
        // ログイン時とコマンドで配る（クライアントは自分の toml を見ない）
        CarRules.setCarCollision(Config.carCollision);
    }

    /**
     * 読み込んだ車種・部品・走行のルールをその人へ配る。ログイン時は本人、{@code /reload} 時は全員に対して呼ぶ。
     *
     * <p><b>クライアントに自分でデータパックを読ませない。</b>シングルプレイでは読めて
     * しまうが、マルチプレイで車種を決めるのはサーバーであって手元のカーパックではない。
     * 配られたものだけを見る、で揃えておけば、サーバーに無い車がクリエイティブタブに
     * 並ぶということが起きない。</p>
     */
    public static void syncDataPack(ServerPlayer player) {
        MaouDriftNetwork.sendToPlayer(player, CarTypesPacket.current());
        MaouDriftNetwork.sendToPlayer(player, CarPartTypesPacket.current());
        MaouDriftNetwork.sendToPlayer(player, CarRulesPacket.current());
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        CarRuleCommands.register(dispatcher);
        RaceCommands.register(dispatcher);
    }

    /** サーバーのティックの終わり。 */
    public static void serverTickPost(MinecraftServer server) {
        RaceManager.onServerTick(server);
    }

    public static void serverStopped() {
        RaceCommands.onServerStopped();
        RaceManager.onServerStopped();
    }

    public static void playerLoggedIn(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            // クラスの一覧はレースの様子より先に（宣言したクラスがまだあるかを、届いた一覧で確かめるため）
            com.maou.maoudrift.race.RaceManager.sendClasses(serverPlayer);
            RaceSync.sendTo(serverPlayer);
        }
    }

    public static void playerLoggedOut(Player player) {
        RaceManager.onLogout(player);
    }

    /** 次元を移った。{@code from} は移る前の次元。 */
    public static void playerChangedDimension(Player player, ResourceKey<Level> from) {
        RaceManager.onChangedDimension(player, from);
        if (player instanceof ServerPlayer serverPlayer) {
            RaceSync.sendTo(serverPlayer);
        }
    }
}
