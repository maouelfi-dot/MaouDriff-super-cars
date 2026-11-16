package com.maou.carprops.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;

/** Isi satu model.json + status muat (lazy) model OBJ-nya. */
public final class CarModelDef {
    public String id, name, texture = "palette.png";
    public final List<String> lods = new ArrayList<>();
    public final Set<String> paintMaterials = new HashSet<>(), glassMaterials = new HashSet<>(),
            lightMaterials = new HashSet<>(), steerParts = new HashSet<>();
    public final Map<String, float[]> finishes = new HashMap<>();
    public String defaultFinish = "gloss";
    public int defaultColor = 0xFFFFFF;
    public float modelScale = 1f, rotY = 0f;
    public float[] offset = {0f, 0f, 0f};

    // lokasi file: salah satu dari (resource pack) atau (folder config)
    public String namespace, resDir;   // resource pack: assets/<namespace>/<resDir><file>
    public Path dir;                   // folder config: <dir>/<file>

    // status runtime
    public AtomicReferenceArray<ObjModel> models;
    public boolean[] loadStarted;
    public volatile ResourceLocation textureLoc;

    public static CarModelDef fromJson(String id, JsonObject j) {
        CarModelDef d = new CarModelDef();
        d.id = id;
        d.name = j.has("name") ? j.get("name").getAsString() : id;
        if (j.has("texture")) d.texture = j.get("texture").getAsString();
        if (j.has("lods")) for (JsonElement e : j.getAsJsonArray("lods")) d.lods.add(e.getAsString());
        if (d.lods.isEmpty()) d.lods.add("model.obj");
        fill(d.paintMaterials, j, "paintMaterials");
        fill(d.glassMaterials, j, "glassMaterials");
        fill(d.lightMaterials, j, "lightMaterials");
        fill(d.steerParts, j, "steerParts");
        if (j.has("finishes")) {
            for (Map.Entry<String, JsonElement> e : j.getAsJsonObject("finishes").entrySet()) {
                JsonArray a = e.getValue().getAsJsonArray();
                d.finishes.put(e.getKey(), new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()});
            }
        }
        if (j.has("defaultFinish")) d.defaultFinish = j.get("defaultFinish").getAsString();
        if (j.has("defaultColor")) d.defaultColor = parseColor(j.get("defaultColor").getAsString(), 0xFFFFFF);
        if (j.has("scale")) d.modelScale = j.get("scale").getAsFloat();
        if (j.has("rotY")) d.rotY = j.get("rotY").getAsFloat();
        if (j.has("offset")) {
            JsonArray a = j.getAsJsonArray("offset");
            d.offset = new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
        }
        d.models = new AtomicReferenceArray<>(d.lods.size());
        d.loadStarted = new boolean[d.lods.size()];
        return d;
    }

    private static void fill(Set<String> set, JsonObject j, String key) {
        if (j.has(key)) for (JsonElement e : j.getAsJsonArray(key)) set.add(e.getAsString());
    }

    public static int parseColor(String s, int fallback) {
        try {
            if (s.startsWith("#")) s = s.substring(1);
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
