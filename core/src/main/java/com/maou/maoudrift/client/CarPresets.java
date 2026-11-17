package com.maou.maoudrift.client;

import com.maou.maoudrift.car.CarId;
import com.maou.maoudrift.race.RaceText;
import com.maou.maoudrift.util.GsonHelper;

import com.google.gson.JsonObject;
import com.maou.maoudrift.part.PartFitment;
import com.maou.maoudrift.physics.CarSpec;
import com.maou.maoudrift.tuning.CarSpecCodec;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * MOD に同梱するセッティングのプリセット。<b>読むだけで、書き換えられない。</b>
 *
 * <p>置き場所は {@code assets/maoudrift/presets/*.json}。ファイル名がそのまま id になり、
 * 表示名は翻訳キー {@code preset.maoudrift.<id>} から引く。リソースとして読むので
 * <b>F3+T で読み直せる</b>し、リソースパックで足すこともできる。</p>
 *
 * <h2>中身は項目名 → 内部単位の値</h2>
 *
 * <p>{@link CarSetups} が {@code config/maoudrift-setups.json} へ書くのと<b>同じ形</b>に
 * してある。ゲーム内で作り込んだセッティングを保存し、そのファイルから 1 つコピーして
 * ここへ置けばプリセットになる、という作り方ができる。</p>
 *
 * <p>単位が内部単位（角度はラジアン）なのはそのため。手で書くより、<b>ゲーム内で作って
 * 書き出したものを持ってくる</b>のが正しい使い方。</p>
 *
 * <h2>これは物理をリソースで決めているのか</h2>
 *
 * <p>いいえ。プリセットは<b>プレイヤーが選ぶ出発点</b>で、選んだ結果はスライダーを手で
 * 動かしたときとまったく同じ経路（{@link CarTuning} → {@code CarSpecPacket}）でサーバーへ
 * 届く。車の諸元がリソースパック次第になるわけではない。</p>
 */
public final class CarPresets {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CarPresets.class);

    private static final String DIRECTORY = "presets";
    private static final String SUFFIX = ".json";

    /** 並び順を書かなかったプリセットの位置。数字の小さいものが先。 */
    private static final int DEFAULT_ORDER = 100;

    private static List<Preset> cached;

    private CarPresets() {
    }

    /**
     * プリセット 1 つ。
     *
     * @param id     ファイル名から取った識別子。翻訳キーに使う
     * @param values 項目名 → 内部単位の値。載っていない項目は既定値になる
     * @param parts  装着状態（{@code "parts"}）。書かれていなければ null で、読み込んでもパーツに触らない
     * @param order  表示順。同じなら id 順
     */
    public record Preset(String id, Map<String, Double> values, @Nullable PartFitment parts, int order) {

        public String translationKey() {
            return "preset.maoudrift." + id;
        }

        /** 画面に出す名前。翻訳が無ければ id をそのまま出す。 */
        public RaceText displayName() {
            String key = translationKey();
            RaceText translated = RaceText.translatable(key);
            return translated.getString().equals(key) ? RaceText.literal(id) : translated;
        }

        public CarSpec toSpec() {
            return CarSpecCodec.fromMap(values);
        }
    }

    /** 一覧。まだ読んでいなければ読む。 */
    public static List<Preset> all() {
        if (cached == null) {
            cached = load();
        }
        return cached;
    }

    /** リソースの再読み込みで呼ぶ。 */
    public static void clearCache() {
        cached = null;
    }

    private static List<Preset> load() {
        Map<CarId, Assets.Asset> found = Assets.get()
                .listResources(DIRECTORY, location -> location.getPath().endsWith(SUFFIX));

        List<Preset> presets = new ArrayList<>();
        for (Map.Entry<CarId, Assets.Asset> entry : found.entrySet()) {
            CarId location = entry.getKey();
            if (!location.getNamespace().equals("maoudrift")) {
                continue;
            }
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                presets.add(parse(idOf(location), GsonHelper.parse(reader)));
            } catch (IOException | RuntimeException e) {
                // 1 つ壊れていても他のプリセットは使えるべきなので、その 1 つだけ捨てる
                LOGGER.error("プリセットの読み込みに失敗しました: {}", location, e);
            }
        }
        presets.sort(Comparator.comparingInt(Preset::order).thenComparing(Preset::id));
        LOGGER.info("組み込みプリセットを {} 件読み込みました", presets.size());
        return List.copyOf(presets);
    }

    /** {@code presets/drift.json} → {@code drift}。 */
    private static String idOf(CarId location) {
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1, path.length() - SUFFIX.length());
    }

    private static Preset parse(String id, JsonObject root) {
        int order = root.has("order") ? root.get("order").getAsInt() : DEFAULT_ORDER;
        JsonObject values = root.has("values") ? root.getAsJsonObject("values") : root;

        // ユーザーのセッティングと同じ読み方をする（数でないキーと "parts" はここで分かれる）
        CarSetups.Setup setup = CarSetups.parse(values);
        Map<String, Double> parsed = new TreeMap<>(setup.values());
        // order を values と同じ階層に書ける形にしてあるので、拾わないよう避ける
        if (values == root) {
            parsed.remove("order");
        }
        return new Preset(id, Map.copyOf(parsed), setup.parts(), order);
    }
}
