package com.maou.carprops.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.maou.carprops.CarProps;
import com.maou.carprops.CarPropsConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Mencari semua model (resource pack: assets/&lt;ns&gt;/carprops_models/&lt;id&gt;/model.json
 * dan folder config/carprops/models/&lt;id&gt;/model.json), memuat OBJ secara lazy di thread terpisah.
 */
public final class CarModelManager {
    private static final Map<String, CarModelDef> DEFS = new ConcurrentHashMap<>();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CarProps-Loader");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private CarModelManager() {}

    public static CarModelDef get(String id) { return DEFS.get(id); }
    public static Collection<CarModelDef> all() { return DEFS.values(); }

    public static void reload(ResourceManager rm) {
        DEFS.clear();
        // 1) resource pack / mod jar
        try {
            Map<ResourceLocation, Resource> found = rm.listResources("carprops_models", rl -> rl.getPath().endsWith("/model.json"));
            for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
                String path = e.getKey().getPath();
                String mid = path.substring("carprops_models/".length(), path.length() - "/model.json".length());
                try (Reader r = e.getValue().openAsReader()) {
                    JsonObject j = JsonParser.parseReader(r).getAsJsonObject();
                    CarModelDef d = CarModelDef.fromJson(mid.replace('/', '_'), j);
                    d.namespace = e.getKey().getNamespace();
                    d.resDir = "carprops_models/" + mid + "/";
                    DEFS.put(d.id, d);
                } catch (Exception ex) {
                    CarProps.LOGGER.warn("model.json rusak: {} ({})", e.getKey(), ex.toString());
                }
            }
        } catch (Exception ex) {
            CarProps.LOGGER.warn("Gagal scan model resource: {}", ex.toString());
        }
        // 2) folder config
        Path root = FabricLoader.getInstance().getConfigDir().resolve("carprops").resolve("models");
        try {
            Files.createDirectories(root);
            try (Stream<Path> s = Files.list(root)) {
                for (Path dir : (Iterable<Path>) s::iterator) {
                    Path mj = dir.resolve("model.json");
                    if (!Files.isDirectory(dir) || !Files.exists(mj)) continue;
                    try (Reader r = Files.newBufferedReader(mj)) {
                        JsonObject j = JsonParser.parseReader(r).getAsJsonObject();
                        CarModelDef d = CarModelDef.fromJson(dir.getFileName().toString(), j);
                        d.dir = dir;
                        DEFS.put(d.id, d);
                    } catch (Exception ex) {
                        CarProps.LOGGER.warn("model.json rusak di {} ({})", dir, ex.toString());
                    }
                }
            }
        } catch (IOException ex) {
            CarProps.LOGGER.warn("Gagal scan folder config models: {}", ex.toString());
        }
        CarProps.LOGGER.info("CarProps: {} model ditemukan: {}", DEFS.size(), DEFS.keySet());
    }

    // ---------- LOD ----------
    public static int chooseLod(CarModelDef d, double dist, float scale) {
        int n = d.lods.size();
        if (n <= 1) return 0;
        String q = CarPropsConfig.get().quality;
        if ("high".equalsIgnoreCase(q)) return 0;
        if ("low".equalsIgnoreCase(q)) return n - 1;
        return dist / Math.max(scale, 0.25f) < CarPropsConfig.get().lodDistance ? 0 : n - 1;
    }

    /** Model untuk LOD tertentu; kalau belum selesai dimuat, kembalikan LOD lain yang sudah ada (atau null). */
    public static ObjModel model(CarModelDef d, int lod) {
        ObjModel m = d.models.get(lod);
        if (m != null) return m;
        synchronized (d) {
            if (!d.loadStarted[lod]) {
                d.loadStarted[lod] = true;
                LOADER.execute(() -> load(d, lod));
            }
        }
        for (int i = 0; i < d.models.length(); i++) {
            ObjModel o = d.models.get(i);
            if (o != null) return o;
        }
        return null;
    }

    private static void load(CarModelDef d, int lod) {
        String file = d.lods.get(lod);
        long t0 = System.nanoTime();
        try (InputStream in = open(d, file)) {
            ObjModel m = ObjModel.parse(in);
            for (ObjModel.Part p : m.parts) {
                for (ObjModel.Group g : p.groups) {
                    int f = 0;
                    if (d.paintMaterials.contains(g.material)) f |= ObjModel.FLAG_PAINT;
                    if (d.glassMaterials.contains(g.material)) f |= ObjModel.FLAG_GLASS;
                    if (d.lightMaterials.contains(g.material)) f |= ObjModel.FLAG_LIGHT;
                    g.flags = f;
                }
            }
            d.models.set(lod, m);
            CarProps.LOGGER.info("CarProps: {} / {} dimuat ({} segitiga, {} ms)", d.id, file, m.triangles, (System.nanoTime() - t0) / 1_000_000);
        } catch (Throwable t) {
            CarProps.LOGGER.error("CarProps: gagal memuat {} / {}: {}", d.id, file, t.toString());
        }
    }

    private static InputStream open(CarModelDef d, String file) throws IOException {
        if (d.dir != null) return Files.newInputStream(d.dir.resolve(file));
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(d.namespace, d.resDir + file);
        Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (r.isEmpty()) throw new IOException("tidak ada: " + rl);
        return r.get().open();
    }

    // ---------- tekstur ----------
    /** Harus dipanggil di thread render. */
    public static ResourceLocation texture(CarModelDef d) {
        ResourceLocation t = d.textureLoc;
        if (t != null) return t;
        if (d.dir == null) {
            t = ResourceLocation.fromNamespaceAndPath(d.namespace, d.resDir + d.texture);
        } else {
            String safe = d.id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(CarProps.MOD_ID, "dyn/" + safe);
            try (InputStream in = Files.newInputStream(d.dir.resolve(d.texture))) {
                NativeImage img = NativeImage.read(in);
                Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
                t = loc;
            } catch (IOException e) {
                CarProps.LOGGER.error("CarProps: tekstur {} gagal dibaca: {}", d.texture, e.toString());
                t = MissingTextureAtlasSprite.getLocation();
            }
        }
        d.textureLoc = t;
        return t;
    }

    public static List<String> ids() {
        List<String> l = new ArrayList<>(DEFS.keySet());
        java.util.Collections.sort(l);
        return l;
    }
}
