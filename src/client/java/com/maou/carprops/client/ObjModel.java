package com.maou.carprops.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pembaca OBJ minimal (tanpa dependensi Minecraft). Mendukung v / vt / vn / f (segi-n, indeks negatif),
 * o / g (jadi "part") dan usemtl (jadi "group" per material). Wajah segi-n dipecah jadi segitiga.
 * Koordinat vt dibalik (v -> 1 - v) karena OBJ memakai v ke atas, Minecraft v ke bawah.
 */
public final class ObjModel {
    public static final int FLAG_PAINT = 1, FLAG_GLASS = 2, FLAG_LIGHT = 4;

    public static final class Group {
        public final String material;
        public float[] pos;   // 3 float per titik sudut
        public float[] uv;    // 2 float per titik sudut
        public float[] nrm;   // 3 float per titik sudut
        public int verts;     // kelipatan 3 (segitiga)
        public int flags;
        Group(String material) { this.material = material; }
    }

    public static final class Part {
        public final String name;
        public final List<Group> groups = new ArrayList<>();
        public float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        public float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        public float cx, cz;
        Part(String name) { this.name = name; }
    }

    public final List<Part> parts = new ArrayList<>();
    public float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
    public float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
    public int triangles;

    // ---------- buffer float yang bisa membesar ----------
    private static final class FloatBuf {
        float[] a = new float[1024];
        int n;
        void add(float v) { if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2); a[n++] = v; }
        float[] toArray() { return java.util.Arrays.copyOf(a, n); }
    }

    private static final class GroupBuilder {
        final Group g;
        final FloatBuf pos = new FloatBuf(), uv = new FloatBuf(), nrm = new FloatBuf();
        GroupBuilder(String m) { g = new Group(m); }
    }

    private static final class PartBuilder {
        final Part p;
        final Map<String, GroupBuilder> groups = new LinkedHashMap<>();
        PartBuilder(String name) { p = new Part(name); }
    }

    public static ObjModel parse(InputStream in) throws IOException {
        FloatBuf v = new FloatBuf(), vt = new FloatBuf(), vn = new FloatBuf();
        List<PartBuilder> builders = new ArrayList<>();
        PartBuilder cur = null;
        String mat = "default";
        float[] tmp = new float[3];
        int[] idx = new int[3];

        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            while ((line = r.readLine()) != null) {
                int len = line.length();
                if (len < 2) continue;
                char c0 = line.charAt(0), c1 = line.charAt(1);
                if (c0 == 'v' && c1 == ' ') {
                    floats(line, 2, tmp, 3);
                    v.add(tmp[0]); v.add(tmp[1]); v.add(tmp[2]);
                } else if (c0 == 'v' && c1 == 't') {
                    floats(line, 3, tmp, 2);
                    vt.add(tmp[0]); vt.add(1.0f - tmp[1]);
                } else if (c0 == 'v' && c1 == 'n') {
                    floats(line, 3, tmp, 3);
                    vn.add(tmp[0]); vn.add(tmp[1]); vn.add(tmp[2]);
                } else if (c0 == 'f' && c1 == ' ') {
                    if (cur == null) { cur = new PartBuilder("default"); builders.add(cur); }
                    GroupBuilder gb = cur.groups.computeIfAbsent(mat, GroupBuilder::new);
                    face(line, v, vt, vn, gb);
                } else if ((c0 == 'o' || c0 == 'g') && c1 == ' ') {
                    String name = line.substring(2).trim();
                    if (cur != null && cur.groups.isEmpty()) {
                        cur = new PartBuilder(name);
                        builders.set(builders.size() - 1, cur);
                    } else {
                        cur = new PartBuilder(name);
                        builders.add(cur);
                    }
                } else if (line.startsWith("usemtl ")) {
                    mat = line.substring(7).trim();
                }
            }
        }

        ObjModel m = new ObjModel();
        for (PartBuilder pb : builders) {
            if (pb.groups.isEmpty()) continue;
            Part p = pb.p;
            for (GroupBuilder gb : pb.groups.values()) {
                Group g = gb.g;
                g.pos = gb.pos.toArray(); g.uv = gb.uv.toArray(); g.nrm = gb.nrm.toArray();
                g.verts = g.pos.length / 3;
                m.triangles += g.verts / 3;
                for (int i = 0; i < g.pos.length; i += 3) {
                    float x = g.pos[i], y = g.pos[i + 1], z = g.pos[i + 2];
                    if (x < p.minX) p.minX = x; if (x > p.maxX) p.maxX = x;
                    if (y < p.minY) p.minY = y; if (y > p.maxY) p.maxY = y;
                    if (z < p.minZ) p.minZ = z; if (z > p.maxZ) p.maxZ = z;
                }
                p.groups.add(g);
            }
            p.cx = (p.minX + p.maxX) * 0.5f;
            p.cz = (p.minZ + p.maxZ) * 0.5f;
            m.minX = Math.min(m.minX, p.minX); m.maxX = Math.max(m.maxX, p.maxX);
            m.minY = Math.min(m.minY, p.minY); m.maxY = Math.max(m.maxY, p.maxY);
            m.minZ = Math.min(m.minZ, p.minZ); m.maxZ = Math.max(m.maxZ, p.maxZ);
            m.parts.add(p);
        }
        return m;
    }

    /** Baca sampai {@code max} angka dari posisi {@code start}. */
    private static void floats(String s, int start, float[] out, int max) {
        int n = 0, i = start, len = s.length();
        while (n < max && i < len) {
            while (i < len && s.charAt(i) == ' ') i++;
            int j = i;
            while (j < len && s.charAt(j) != ' ') j++;
            if (j > i) out[n++] = Float.parseFloat(s.substring(i, j));
            i = j;
        }
        while (n < max) out[n++] = 0f;
    }

    private static int resolve(int i, int size) { return i > 0 ? i - 1 : size + i; }

    private static void face(String line, FloatBuf v, FloatBuf vt, FloatBuf vn, GroupBuilder gb) {
        int[] vi = new int[16], ti = new int[16], ni = new int[16];
        int count = 0;
        int i = 2, len = line.length();
        while (i < len) {
            while (i < len && line.charAt(i) == ' ') i++;
            if (i >= len) break;
            int j = i;
            while (j < len && line.charAt(j) != ' ') j++;
            if (count == vi.length) {
                vi = java.util.Arrays.copyOf(vi, count * 2);
                ti = java.util.Arrays.copyOf(ti, count * 2);
                ni = java.util.Arrays.copyOf(ni, count * 2);
            }
            // token: v | v/vt | v//vn | v/vt/vn
            int s1 = line.indexOf('/', i);
            int a, b = Integer.MIN_VALUE, c = Integer.MIN_VALUE;
            if (s1 < 0 || s1 >= j) {
                a = Integer.parseInt(line.substring(i, j));
            } else {
                a = Integer.parseInt(line.substring(i, s1));
                int s2 = line.indexOf('/', s1 + 1);
                if (s2 < 0 || s2 >= j) {
                    if (j > s1 + 1) b = Integer.parseInt(line.substring(s1 + 1, j));
                } else {
                    if (s2 > s1 + 1) b = Integer.parseInt(line.substring(s1 + 1, s2));
                    if (j > s2 + 1) c = Integer.parseInt(line.substring(s2 + 1, j));
                }
            }
            vi[count] = resolve(a, v.n / 3);
            ti[count] = b == Integer.MIN_VALUE ? -1 : resolve(b, vt.n / 2);
            ni[count] = c == Integer.MIN_VALUE ? -1 : resolve(c, vn.n / 3);
            count++;
            i = j;
        }
        for (int k = 1; k + 1 < count; k++) {
            emit(v, vt, vn, gb, vi[0], ti[0], ni[0], vi[k], ti[k], ni[k], vi[k + 1], ti[k + 1], ni[k + 1]);
        }
    }

    private static void emit(FloatBuf v, FloatBuf vt, FloatBuf vn, GroupBuilder gb,
                             int v0, int t0, int n0, int v1, int t1, int n1, int v2, int t2, int n2) {
        float fnx = 0, fny = 0, fnz = 0;
        if (n0 < 0 || n1 < 0 || n2 < 0) {
            float ax = v.a[v1 * 3] - v.a[v0 * 3], ay = v.a[v1 * 3 + 1] - v.a[v0 * 3 + 1], az = v.a[v1 * 3 + 2] - v.a[v0 * 3 + 2];
            float bx = v.a[v2 * 3] - v.a[v0 * 3], by = v.a[v2 * 3 + 1] - v.a[v0 * 3 + 1], bz = v.a[v2 * 3 + 2] - v.a[v0 * 3 + 2];
            fnx = ay * bz - az * by; fny = az * bx - ax * bz; fnz = ax * by - ay * bx;
            float l = (float) Math.sqrt(fnx * fnx + fny * fny + fnz * fnz);
            if (l > 1e-8f) { fnx /= l; fny /= l; fnz /= l; } else { fny = 1f; }
        }
        put(v, vt, vn, gb, v0, t0, n0, fnx, fny, fnz);
        put(v, vt, vn, gb, v1, t1, n1, fnx, fny, fnz);
        put(v, vt, vn, gb, v2, t2, n2, fnx, fny, fnz);
    }

    private static void put(FloatBuf v, FloatBuf vt, FloatBuf vn, GroupBuilder gb,
                            int vi, int ti, int ni, float fx, float fy, float fz) {
        gb.pos.add(v.a[vi * 3]); gb.pos.add(v.a[vi * 3 + 1]); gb.pos.add(v.a[vi * 3 + 2]);
        if (ti >= 0) { gb.uv.add(vt.a[ti * 2]); gb.uv.add(vt.a[ti * 2 + 1]); }
        else { gb.uv.add(0.5f); gb.uv.add(0.5f); }
        if (ni >= 0) { gb.nrm.add(vn.a[ni * 3]); gb.nrm.add(vn.a[ni * 3 + 1]); gb.nrm.add(vn.a[ni * 3 + 2]); }
        else { gb.nrm.add(fx); gb.nrm.add(fy); gb.nrm.add(fz); }
    }
}
