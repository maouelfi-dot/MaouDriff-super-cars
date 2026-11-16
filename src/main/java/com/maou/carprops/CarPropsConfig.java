package com.maou.carprops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Config: .minecraft/config/carprops.json (dibuat otomatis saat pertama jalan). */
public final class CarPropsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static CarPropsConfig INSTANCE = new CarPropsConfig();

    /** Ukuran fisik model (blok, pada skala 1). Dipakai untuk kotak tabrakan. */
    public static final class ModelDims {
        public float length = 4.8f, width = 2.0f, height = 1.3f;
        public ModelDims() {}
        public ModelDims(float l, float w, float h) { length = l; width = w; height = h; }
    }
    private static final ModelDims FALLBACK = new ModelDims();

    /** auto = HD kalau dekat, ringan kalau jauh | high = selalu HD | low = selalu ringan (hemat RAM/CPU). */
    public String quality = "auto";
    /** Jarak (blok) maksimal mobil digambar. */
    public double renderDistance = 96;
    /** Mode auto: di bawah jarak ini (dibagi skala mobil) pakai model HD. */
    public double lodDistance = 24;
    /** Transparansi kaca 0..1 (1 = tidak tembus pandang). */
    public double glassAlpha = 0.45;
    /** true = mobil padat (tidak bisa ditembus, tidak bisa didorong). */
    public boolean collision = true;
    /** Model yang dipakai Car Placer saat pertama. */
    public String defaultModel = "bmw_m3_gt3";
    public double defaultScale = 1.0;
    /** Daftar model yang bisa diganti-ganti dengan klik kanan ke udara pakai Car Placer. */
    public List<String> placerModels = new ArrayList<>(List.of("bmw_m3_gt3", "bmw_m4_f82"));
    /** Panjang/lebar/tinggi tiap model (untuk tabrakan). Model yang tidak ada di sini pakai 4.8 x 2.0 x 1.3. */
    public Map<String, ModelDims> models = new LinkedHashMap<>();

    public static CarPropsConfig get() { return INSTANCE; }

    public static ModelDims dims(String modelId) {
        ModelDims d = INSTANCE.models == null ? null : INSTANCE.models.get(modelId);
        return d != null ? d : FALLBACK;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("carprops.json");
    }

    public static void load() {
        Path f = file();
        try {
            if (Files.exists(f)) {
                CarPropsConfig c = GSON.fromJson(Files.readString(f), CarPropsConfig.class);
                if (c != null) INSTANCE = c;
            }
        } catch (Exception e) {
            CarProps.LOGGER.warn("Gagal baca carprops.json, pakai default: {}", e.toString());
        }
        if (INSTANCE.models == null) INSTANCE.models = new LinkedHashMap<>();
        INSTANCE.models.putIfAbsent("bmw_m3_gt3", new ModelDims(4.85f, 2.01f, 1.29f));
        INSTANCE.models.putIfAbsent("bmw_m4_f82", new ModelDims(4.70f, 1.85f, 1.33f));
        if (INSTANCE.placerModels == null || INSTANCE.placerModels.isEmpty()) {
            INSTANCE.placerModels = new ArrayList<>(List.of(INSTANCE.defaultModel));
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(INSTANCE));
        } catch (IOException e) {
            CarProps.LOGGER.warn("Gagal simpan carprops.json: {}", e.toString());
        }
    }
}
