"""
maou_car_export.py - ubah mobil Blender (.blend / .fbx) jadi aset siap pakai untuk Maou Drift.

Jalankan (Python 3.11+ dengan `pip install bpy pillow numpy`):
    python maou_car_export.py m3   <folder_keluaran>
    python maou_car_export.py m4   <folder_keluaran>
    python maou_car_export.py supra <folder_keluaran>
Path sumber diatur di CARS (bawah) atau lewat variabel lingkungan MAOU_SRC_<KEY>.

Hasil di <folder_keluaran>:
    <id>.obj/.mtl            bodi + kaca (glass) + lampu (light_head/light_tail), normal halus
    <id>.png / <id>_s.png    tekstur + peta kilap labPBR (R=smoothness, G=F0/metal, A=255)
    <id>_<cat>.png (+_s)     varian warna cat (kalau ada)
    <id>_wheel.obj/.mtl, <id>_wheel.png/_s.png     velg (sisi kiri, sumbu X, wajah +X)
    <id>.json                ukuran (wheel_base, track_width, wheel_radius, tinggi, dll)
Mobil diskalakan 1.2x (ukuran ala gauge Maou Drift). Depan = -Y Blender, tanah z=0,
titik tengah = tengah antara kedua as roda.
"""
import json, math, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
import bpy
from PIL import Image

GAME_SCALE = 1.2        # gauge Maou Drift = mobil asli x1.2
GAUGE_RIM_R = 0.352     # jari-jari luar velg di gauge
GAUGE_RIM_W = 0.32      # lebar velg standar
CELL = 4                # ukuran sel palet (piksel)
COLS = 16

# --------------------------------------------------------------------------------------
# bahan -> (kelas, rgb, smooth, f0/metal)
# --------------------------------------------------------------------------------------
def srgb(c):
    return int(max(0, min(255, round((max(float(c), 0.0) ** (1 / 2.2)) * 255))))


MAT_STYLES = {
    # kelas: (rgb default, smoothness 0..1, G (0-229 = F0, 230-255 = logam))
    "paint":   ((255, 255, 255), 0.93, 56),
    "plastic": ((20, 20, 22), 0.38, 36),
    "rubber":  ((18, 18, 19), 0.20, 24),
    "chrome":  ((236, 236, 242), 1.00, 255),
    "metal":   ((120, 122, 128), 0.86, 255),
    "satin":   ((170, 172, 176), 0.62, 255),
    "piano":   ((8, 8, 10), 0.97, 60),
    "fabric":  ((34, 34, 36), 0.12, 20),
    "carbon":  ((18, 18, 20), 0.82, 60),
    "glass":   ((18, 26, 32), 1.00, 70),
    "lens":    ((245, 245, 240), 0.96, 50),
    "color":   (None, 0.45, 40),
}


def classify_material(name, rgb, emit):
    n = (name or "none").lower()
    if n in ("red_glass", "orange_glass"):
        return "light_head"
    if "lighta_material" in n:
        return "light_head"
    if "glass" in n or "windscreen" in n or n.endswith("window_material1"):
        return "glass"
    if n.startswith("car_paint") or n in ("material",) or "paintmaterial" in n.replace("_", "") or n.endswith("paint_material1") or "paint_a" in n or n.endswith("painta_material1"):
        return "paint"
    if any(k in n for k in ("rearlight", "blinker", "backlight")):
        return "light_tail"
    if n in ("light", "light_blue") or n.startswith("light"):
        return "light_head"
    if any(k in n for k in ("chrome_matt",)):
        return "satin"
    if any(k in n for k in ("chrome", "mirror", "miror", "reflector")):
        return "chrome"
    if any(k in n for k in ("rim", "bremsscheibe", "disc", "calliper", "screw", "alu", "steel", "metal")):
        return "metal"
    if any(k in n for k in ("reifen", "tire", "tyre", "rubber", "seatbelt")):
        return "rubber"
    if "piano" in n:
        return "piano"
    if "carbon" in n:
        return "carbon"
    if "interior" in n:
        return "fabric"
    if any(k in n for k in ("black", "plastik", "plastic", "tailights", "base_material", "grille")):
        return "plastic"
    return "color"


def mat_rgb(mat):
    col = None
    if mat is not None and mat.node_tree:
        for nd in mat.node_tree.nodes:
            if nd.type == "BSDF_PRINCIPLED":
                col = tuple(nd.inputs["Base Color"].default_value[:3]); break
        if col is None:
            for nd in mat.node_tree.nodes:
                if nd.type in ("BSDF_DIFFUSE", "BSDF_GLOSSY", "EMISSION") and "Color" in nd.inputs:
                    col = tuple(nd.inputs["Color"].default_value[:3]); break
    if col is None and mat is not None:
        col = tuple(mat.diffuse_color[:3])
    if col is None:
        col = (0.5, 0.5, 0.5)
    return tuple(srgb(c) for c in col)


# --------------------------------------------------------------------------------------
# resep per mobil
# --------------------------------------------------------------------------------------
HERE = os.path.dirname(os.path.abspath(__file__))
CARS = {
    "m3": dict(
        kind="blend", src=os.environ.get("MAOU_SRC_M3", "m3/bmw_m3_gt3_cycles.blend"),
        title="BMW M3 GT3", real_wb=2.76,
        skip=["earth", "light_plane", "camera", "lamp", "point"],
        wheel_objs=["Felge_FR"],  # roda kiri (+X)
        wheel_prefix="Felge_",
        wheel_centers="empties:WFL,WFR,WRL,WRR",
        budget=50000, wheel_budget=4200,
        paints=[("white", "#EDEDEA"), ("black", "#0D0D0F"), ("graphite", "#3A3C40"), ("silver", "#A6A9AE"),
                ("red", "#B3141C"), ("blue", "#0F2F9E"), ("orange", "#E8620C"), ("yellow", "#F0C410")],
        wheel_keep_materials=["chrome", "bremsscheibe"],
    ),
    "m4": dict(
        kind="blend", src=os.environ.get("MAOU_SRC_M4", "m4/BMW_M4_(20)_blendswap.blend"),
        title="BMW M4 F82", real_wb=2.812,
        skip=["ground", "floor", "camera", "duplication_plane", "windscreen_for_lattice", "rim", "tyre", "rim_cap", "main_body.004"],
        wheel_objs=["Rim", "Rim_cap", "Main_Body.004"],
        wheel_centers="manual:0.71,-1.525,0.365;1.23",
        budget=62000, wheel_budget=4200,
        paints=[("white", "#EFEFEC"), ("black", "#0C0C0E"), ("blue", "#1B4DB3"), ("red", "#B3141C"),
                ("silver", "#A6A9AE"), ("yellow", "#F0C410"), ("orange", "#E8620C"), ("green", "#1D6B3A")],
        wheel_keep_materials=["rim", "rim_screw.001", "rim.001"],
    ),
    "supra": dict(
        kind="fbx", src=os.environ.get("MAOU_SRC_SUPRA", "supra/src/fm/FINAL_MODEL_SUPRA.fbx"),
        title="Toyota Supra MK IV (F&F)", real_wb=2.55,
        skip=["engine_geo", "calliper", "polysurface85", "polysurface86", "polysurface96", "underlighting"],
        wheel_objs=["polySurface858"],  # Front L
        wheel_prefix="polySurface8",
        wheel_all=["polySurface857", "polySurface859", "polySurface860"],
        wheel_centers="objects:polySurface858,polySurface859,polySurface860,polySurface857",
        budget=72000, wheel_budget=4200,
        tex_dir=os.environ.get("MAOU_TEX_SUPRA", "supra/src/fm"),
        paints=[],
        wheel_keep_materials=["wheel1a"],
    ),
}


# --------------------------------------------------------------------------------------
# util geometri
# --------------------------------------------------------------------------------------
def world_tris(obj, dg, ratio=None):
    """Segitiga objek (modifier diterapkan) dalam koordinat dunia."""
    e = obj.evaluated_get(dg)
    me = e.to_mesh()
    me.calc_loop_triangles()
    nv, nl, nt = len(me.vertices), len(me.loops), len(me.loop_triangles)
    if nt == 0:
        e.to_mesh_clear(); return None
    co = np.empty(nv * 3, np.float32); me.vertices.foreach_get("co", co); co = co.reshape(-1, 3)
    lv = np.empty(nl, np.int32); me.loops.foreach_get("vertex_index", lv)
    cn = np.empty(nl * 3, np.float32); me.corner_normals.foreach_get("vector", cn); cn = cn.reshape(-1, 3)
    tl = np.empty(nt * 3, np.int32); me.loop_triangles.foreach_get("loops", tl); tl = tl.reshape(-1, 3)
    tm = np.empty(nt, np.int32); me.loop_triangles.foreach_get("material_index", tm)
    uv = None
    if me.uv_layers.active is not None:
        uvl = me.uv_layers.active
        a = np.empty(nl * 2, np.float32)
        try:
            uvl.uv.foreach_get("vector", a)
        except Exception:
            uvl.data.foreach_get("uv", a)
        uv = a.reshape(-1, 2)[tl]            # nt,3,2
    M = np.array(obj.matrix_world, np.float64)
    R = M[:3, :3]
    P = co.astype(np.float64)[lv[tl]] @ R.T + M[:3, 3]        # nt,3,3
    try:
        Rn = np.linalg.inv(R).T
    except np.linalg.LinAlgError:
        Rn = R
    N = cn.astype(np.float64)[tl] @ Rn.T
    N /= np.maximum(np.linalg.norm(N, axis=-1, keepdims=True), 1e-12)
    if np.linalg.det(R) < 0:                  # cermin -> balik urutan sudut
        P = P[:, ::-1]; N = N[:, ::-1]
        if uv is not None:
            uv = uv[:, ::-1]
    slots = [s.material for s in obj.material_slots]
    e.to_mesh_clear()
    return dict(P=P, N=N, UV=uv, mi=tm, slots=slots, name=obj.name)


def ensure_smooth(obj):
    me = obj.data
    try:
        flat = not any(p.use_smooth for p in me.polygons)
    except Exception:
        flat = False
    if flat:
        try:
            with bpy.context.temp_override(active_object=obj, selected_objects=[obj], object=obj):
                bpy.ops.object.shade_smooth_by_angle(angle=math.radians(38))
        except Exception as ex:
            print("  smooth gagal", obj.name, ex)


def eval_all(objs, budget, extra_subsurf=None):
    """Evaluasi semua objek; turunkan level subsurf / decimate sampai total <= budget."""
    bpy.context.view_layer.update()
    for lvl_cap in (99, 2, 1, 0):
        for o in objs:
            for m in o.modifiers:
                if m.type == "SUBSURF":
                    m.levels = min(getattr(m, "_orig", m.levels), lvl_cap) if False else min(m.levels, lvl_cap)
                    m.render_levels = m.levels
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        counts = {}
        for o in objs:
            e = o.evaluated_get(dg); me = e.to_mesh(); me.calc_loop_triangles()
            counts[o.name] = len(me.loop_triangles); e.to_mesh_clear()
        total = sum(counts.values())
        print("  subsurf cap", lvl_cap, "total tris", total)
        if total <= budget * 1.3:
            break
    if total > budget:
        # decimate objek besar secara proporsional (objek kecil dibiarkan)
        big = {k: v for k, v in counts.items() if v > 1500}
        small_total = total - sum(big.values())
        ratio = max(0.08, (budget - small_total) / max(1, sum(big.values())))
        print("  decimate ratio", round(ratio, 3))
        for o in objs:
            if o.name in big:
                m = o.modifiers.new("maou_dec", "DECIMATE")
                m.decimate_type = "COLLAPSE"; m.ratio = ratio; m.use_collapse_triangulate = True
        bpy.context.view_layer.update()
    return bpy.context.evaluated_depsgraph_get()


# --------------------------------------------------------------------------------------
# atlas
# --------------------------------------------------------------------------------------
class Atlas:
    def __init__(self, size=64):
        self.size = size
        self.cells = []          # (rgb, smooth, G, emit)
        self.cell_of = {}
        self.regions = {}        # nama -> (x,y,w,h) piksel, untuk gambar asli
        self.images = []         # (nama, PIL rgba diffuse, PIL rgba spec, x, y)
        self.cursor = None

    def cell(self, key, rgb, smooth, g):
        if key not in self.cell_of:
            self.cell_of[key] = len(self.cells)
            self.cells.append((tuple(rgb), smooth, g))
        return self.cell_of[key]

    def cell_uv(self, i):
        return (((i % COLS) * CELL + CELL / 2) / self.size, ((i // COLS) * CELL + CELL / 2) / self.size)


# --------------------------------------------------------------------------------------
# tulis OBJ
# --------------------------------------------------------------------------------------
def split_panels(og):
    """Pisahkan segitiga bodi/kaca yang jatuh di daerah pintu dan kap mesin ke objek sendiri
    (door_l, door_r, hood, glass_door_l, glass_door_r). Koordinat Blender: depan -Y, kiri +X, atas Z."""
    allp = [c[1].reshape(-1, 3) for k in ("body", "glass_main") for c in og.get(k, [])]
    if not allp:
        return og
    A = np.concatenate(allp)
    minY, maxY = A[:, 1].min(), A[:, 1].max()
    minX, maxX = A[:, 0].min(), A[:, 0].max()
    minZ, maxZ = A[:, 2].min(), A[:, 2].max()
    L, W, H = maxY - minY, maxX - minX, maxZ - minZ
    out = {k: list(v) for k, v in og.items()}
    for k in ("part_hood", "part_door_l", "part_door_r"):
        out[k] = []
    def regions(c, src):
        f = (c[:, 1] - minY) / L
        ax = np.abs(c[:, 0]) / W
        z = (c[:, 2] - minZ) / H
        if src == "body":
            hood = (f > 0.08) & (f < 0.36) & (z > 0.50) & (ax < 0.34)
            door = (f > 0.40) & (f < 0.62) & (ax > 0.40) & (z > 0.17) & (z < 0.80)
        else:
            hood = np.zeros(len(c), bool)
            door = (f > 0.40) & (f < 0.64) & (ax > 0.36) & (z > 0.30)
        return hood, door

    def refine(P, N, UV, src, rounds=3):
        """Bagi segitiga yang melintasi batas daerah supaya tepi pintu/kap rapi (bukan bergerigi)."""
        for _ in range(rounds):
            hs, ds = [], []
            for k in range(3):
                h, d = regions(P[:, k], src)
                hs.append(h); ds.append(d)
            h = np.stack(hs, 1); d = np.stack(ds, 1)
            mixed = (h.any(1) & ~h.all(1)) | (d.any(1) & ~d.all(1))
            e = np.maximum.reduce([np.linalg.norm(P[:, 0] - P[:, 1], axis=1),
                                   np.linalg.norm(P[:, 1] - P[:, 2], axis=1),
                                   np.linalg.norm(P[:, 2] - P[:, 0], axis=1)])
            sel = mixed & (e > 0.03)
            if not sel.any():
                break
            keep = ~sel
            Q, M, T = P[sel], N[sel], UV[sel]
            def mid(A):
                return np.stack([(A[:, 0] + A[:, 1]) / 2, (A[:, 1] + A[:, 2]) / 2, (A[:, 2] + A[:, 0]) / 2], 1)
            Qm, Mm, Tm = mid(Q), mid(M), mid(T)
            Mm = Mm / np.maximum(np.linalg.norm(Mm, axis=2, keepdims=True), 1e-9)
            def kids(A, Am):
                a0, a1, a2 = A[:, 0], A[:, 1], A[:, 2]
                m01, m12, m20 = Am[:, 0], Am[:, 1], Am[:, 2]
                return [np.stack(t, 1) for t in ((a0, m01, m20), (m01, a1, m12), (m20, m12, a2), (m01, m12, m20))]
            kp, kn, kt = kids(Q, Qm), kids(M, Mm), kids(T, Tm)
            P = np.concatenate([P[keep]] + kp); N = np.concatenate([N[keep]] + kn); UV = np.concatenate([UV[keep]] + kt)
        return P, N, UV

    for src in ("body", "glass_main"):
        keep = []
        for mat, P, N, UV in og.get(src, []):
            P, N, UV = refine(P, N, UV, src)
            c = P.mean(axis=1)
            hood, door = regions(c, src)
            left = c[:, 0] > 0
            masks = [("part_hood", hood), ("part_door_l", door & left), ("part_door_r", door & ~left)]
            rest = np.ones(len(c), bool)
            for name, m in masks:
                if m.any():
                    out[name].append(("m_doorglass" if src == "glass_main" else mat, P[m], N[m], UV[m]))
                    rest &= ~m
            if rest.any():
                keep.append((mat, P[rest], N[rest], UV[rest]))
        out[src] = keep
    return out


def write_obj(path, groups, mtl_name):
    """groups: list of (nama_objek, [(material, P, N, UV)])  koordinat mobil (Blender: depan -Y, atas Z)."""
    vl, vi = {}, {}
    nl, ni = {}, {}
    tl, ti = {}, {}
    vlines, nlines, tlines, flines = [], [], [], []

    def idx(table, lines, key, text):
        i = table.get(key)
        if i is None:
            i = len(table) + 1; table[key] = i; lines.append(text)
        return i

    tris = 0
    out = ["# Maou Drift export", "mtllib " + mtl_name]
    mats_used = []
    body = []
    for oname, chunks in groups:
        body.append("o " + oname)
        for mat, P, N, UV in chunks:
            if mat not in mats_used:
                mats_used.append(mat)
            body.append("usemtl " + mat)
            for t in range(len(P)):
                corner = []
                for k in range(3):
                    x, y, z = P[t, k]
                    ox, oy, oz = x, z, -y            # konvensi ekspor OBJ Blender: Forward -Z, Up Y
                    a = idx(vi, vlines, (round(ox, 5), round(oy, 5), round(oz, 5)), "v %.5f %.5f %.5f" % (ox, oy, oz))
                    nx, ny, nz = N[t, k]
                    b = idx(ni, nlines, (round(nx, 3), round(nz, 3), round(-ny, 3)), "vn %.3f %.3f %.3f" % (nx, nz, -ny))
                    u, v = UV[t, k]
                    c = idx(ti, tlines, (round(u, 5), round(v, 5)), "vt %.5f %.5f" % (u, v))
                    corner.append("%d/%d/%d" % (a, c, b))
                body.append("f " + " ".join(corner))
                tris += 1
    with open(path, "w") as f:
        f.write("\n".join(out + vlines + tlines + nlines + body) + "\n")
    return tris, mats_used


def write_mtl(path, mats, glass_alpha=None):
    with open(path, "w") as f:
        f.write("# Maou Drift\n")
        for m in mats:
            f.write("newmtl %s\nKd 1.000000 1.000000 1.000000\nd %s\nillum 2\n\n" % (m, "0.350000" if m == "m_doorglass" else "1.000000"))


# --------------------------------------------------------------------------------------
# utama
# --------------------------------------------------------------------------------------
def main(key, outdir):
    cfg = CARS[key]
    os.makedirs(outdir, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    if cfg["kind"] == "blend":
        bpy.ops.wm.open_mainfile(filepath=cfg["src"])
    else:
        bpy.ops.wm.fbx_import(filepath=cfg["src"])
    bpy.context.view_layer.update()

    allmesh = [o for o in bpy.data.objects if o.type == "MESH" and not o.hide_render]
    skip = cfg["skip"]

    def is_skipped(o):
        n = o.name.lower()
        return any(s in n for s in skip)

    wheel_objs = [bpy.data.objects[n] for n in cfg["wheel_objs"] if n in bpy.data.objects]
    wnames = set(cfg.get("wheel_all", [])) | {o.name for o in wheel_objs}
    wprefix = cfg.get("wheel_prefix", "@@")
    body_objs = [o for o in allmesh if not is_skipped(o) and len(o.data.polygons) > 0 and len(o.material_slots) > 0
                 and o.name not in wnames and not o.name.startswith(wprefix)]
    print("BODY OBJECTS:", [o.name for o in body_objs])
    for o in body_objs + wheel_objs:
        ensure_smooth(o)

    # ---------- posisi roda & skala ----------
    dg0 = bpy.context.evaluated_depsgraph_get()
    wc = cfg["wheel_centers"]
    if wc.startswith("empties:"):
        names = wc.split(":")[1].split(",")
        W = [np.array(bpy.data.objects[n].matrix_world.translation) for n in names]   # FL FR RL RR
    elif wc.startswith("objects:"):
        names = wc.split(":")[1].split(",")
        W = []
        for n in names:
            t = world_tris(bpy.data.objects[n], dg0)
            P = t["P"].reshape(-1, 3)
            W.append((P.min(0) + P.max(0)) / 2)
    else:  # manual: "x,y,z;yRear"  -> roda kiri depan (+X)
        a, b = wc.split(":")[1].split(";")
        x, y, z = [float(v) for v in a.split(",")]; yr = float(b)
        W = [np.array([x, y, z]), np.array([-x, y, z]), np.array([x, yr, z]), np.array([-x, yr, z])]
    W = np.array(W)
    wb_model = float(abs(W[2, 1] - W[0, 1]))
    track_model = float(abs(W[0, 0] - W[1, 0]))
    S = cfg["real_wb"] / wb_model * GAME_SCALE
    cy = float((W[0, 1] + W[2, 1]) / 2)
    # roda kiri harus +X: kalau FL ada di -X, cermin sumbu X (tidak kita lakukan; cukup beri tanda)
    tq = world_tris(wheel_objs[0], dg0) if wheel_objs else None
    tire_R = None
    print("wb_model", wb_model, "track_model", track_model, "scale", S)

    # ---------- evaluasi badan ----------
    dg = eval_all(body_objs, cfg["budget"])
    # tanah: bagian bawah roda
    ground = float(W[:, 2].min() - ( (W[0, 2] - 0.0) if False else 0))
    # perkiraan jari-jari ban dari bbox objek roda (bukan velg): pakai objek roda pertama
    wobj = world_tris(wheel_objs[0], dg0) if wheel_objs else None

    # kumpulkan segitiga
    recs = []
    for o in body_objs:
        t = world_tris(o, dg)
        if t is not None:
            recs.append(t)

    # ---------- tinggi tanah: z minimum roda ----------
    allP = np.concatenate([r["P"].reshape(-1, 3) for r in recs])
    # radius ban: jarak z pusat roda ke z paling bawah roda
    if cfg["kind"] == "blend" and key == "m3":
        wp = world_tris(wheel_objs[0], dg0)["P"].reshape(-1, 3)
        tire_R = float((wp[:, 2].max() - wp[:, 2].min()) / 2)
        ground_z = float(wp[:, 2].min())
    elif key == "m4":
        tire_R = 0.295; ground_z = float(W[0, 2] - tire_R)
    else:
        wp = world_tris(wheel_objs[0], dg0)["P"].reshape(-1, 3)
        tire_R = float((wp[:, 2].max() - wp[:, 2].min()) / 2)
        ground_z = float(wp[:, 2].min())
    print("tire_R", tire_R, "ground", ground_z)

    def to_car(P):
        Q = P.copy()
        Q[..., 0] = P[..., 0] * S
        Q[..., 1] = (P[..., 1] - cy) * S
        Q[..., 2] = (P[..., 2] - ground_z) * S
        return Q

    # ---------- atlas / palet ----------
    atlas = Atlas(64 if cfg["kind"] == "blend" else 2048)
    white_cell = atlas.cell("white", (255, 255, 255), 0.95, 40)
    paint_cell = atlas.cell("paint", (255, 255, 255), 0.93, 56)
    textures = {}                     # nama gambar asli -> PIL (hanya Supra)
    tex_regions = {}
    out_groups = {"body": [], "glass_main": [], "light_head": [], "light_tail": []}
    mats_in_group = {}

    FRONT = lambda y: y < 0       # setelah to_car: depan = -Y (y<0)

    material_cells = {}
    def mat_info(mat):
        nm = mat.name if mat else "none"
        if nm in material_cells:
            return material_cells[nm]
        rgb = mat_rgb(mat)
        kind = classify_material(nm, rgb, False)
        material_cells[nm] = (kind, rgb)
        return material_cells[nm]

    supra_images = {}
    if cfg["kind"] == "fbx":
        from supra_atlas import build_supra_atlas  # modul pembantu (lihat bawah file)
        supra = build_supra_atlas(cfg["tex_dir"], atlas)
    else:
        supra = None

    for r in recs:
        P = to_car(r["P"]); N = r["N"]
        UV = r["UV"]
        nt = len(P)
        for mi in np.unique(r["mi"]):
            mat = r["slots"][min(mi, len(r["slots"]) - 1)] if r["slots"] else None
            kind, rgb = mat_info(mat)
            sel = np.nonzero(r["mi"] == mi)[0]
            Ps, Ns = P[sel], N[sel]
            uvs = None if UV is None else UV[sel]
            role = "body"
            matname = None
            if kind == "glass":
                role = "glass_main"; cell = atlas.cell("glass", MAT_STYLES["glass"][0], 1.0, 70)
                matname = "m_glass"
            elif kind in ("light_head", "light_tail"):
                role = kind; cell = white_cell; matname = "m_lens"
            else:
                nm = mat.name if mat else "none"
                if supra is not None:
                    cell = None
                    matname = "m_" + re.sub(r"[^A-Za-z0-9]", "", nm)[-24:]
                else:
                    if kind == "paint":
                        cell = paint_cell
                    elif kind == "color":
                        cell = atlas.cell(("c", rgb), rgb, 0.45, 40)
                    else:
                        base = MAT_STYLES[kind]
                        use_rgb = base[0] if base[0] is not None else rgb
                        if kind in ("metal", "plastic", "fabric", "rubber") and rgb != (0, 0, 0) and kind == "metal":
                            use_rgb = tuple(int(0.5 * a + 0.5 * b) for a, b in zip(base[0], rgb))
                        cell = atlas.cell((kind, use_rgb), use_rgb, base[1], base[2])
                    matname = "m_" + kind
            # UV
            if supra is not None and role in ("body",):
                UVo = supra.remap(mat, uvs, Ps, kind)
            elif supra is not None and role in ("light_head", "light_tail", "glass_main"):
                UVo = supra.remap(mat, uvs, Ps, kind) if role != "glass_main" else supra.flat("glass", Ps)
            else:
                u, v = atlas.cell_uv(cell)
                UVo = np.tile(np.array([u, 1.0 - v]), (len(Ps), 3, 1))
            # lampu depan/belakang menurut posisi, untuk objek campuran Supra
            if role in ("light_head", "light_tail") and supra is not None:
                cy_f = Ps.reshape(-1, 3)[:, 1].mean()
                role = "light_head" if cy_f < 0 else "light_tail"
            out_groups[role].append((matname, Ps, Ns, UVo))

    # ---------- pintu & kap mesin: pisahkan menurut daerah bodi ----------
    out_groups = split_panels(out_groups)

    # tulis body
    groups = []
    if out_groups["body"]:
        groups.append(("body", out_groups["body"]))
    if out_groups["glass_main"]:
        groups.append(("glass_main", out_groups["glass_main"]))
    for k in ("part_hood", "part_door_l", "part_door_r"):
        if out_groups.get(k):
            groups.append((k, out_groups[k]))
    for k in ("light_head", "light_tail"):
        if out_groups[k]:
            groups.append((k, out_groups[k]))
    path_obj = os.path.join(outdir, key + ".obj")
    tris, mats = write_obj(path_obj, groups, key + ".mtl")
    write_mtl(os.path.join(outdir, key + ".mtl"), mats)
    print("BODY tris", tris)

    # ---------- tekstur ----------
    if supra is None:
        save_palette(atlas, outdir, key, cfg["paints"])
    else:
        supra.save(outdir, key)

    # ---------- roda ----------
    wheel_info = export_wheel(key, cfg, outdir, wheel_objs, W, S, dg0, tire_R)

    allQ = np.concatenate([to_car(r["P"]).reshape(-1, 3) for r in recs])
    info = dict(
        title=cfg["title"], tris_body=tris, scale=S,
        wheel_base=round(wb_model * S, 4), track_width=round(track_model * S, 4),
        wheel_radius=round(tire_R * S, 4),
        length=round(float(allQ[:, 1].max() - allQ[:, 1].min()), 3),
        width=round(float(allQ[:, 0].max() - allQ[:, 0].min()), 3),
        height=round(float(allQ[:, 2].max()), 3),
        front_y=round(float(allQ[:, 1].min()), 3), rear_y=round(float(allQ[:, 1].max()), 3),
        paints=cfg["paints"], wheel=wheel_info,
    )
    json.dump(info, open(os.path.join(outdir, key + ".json"), "w"), indent=2)
    print("INFO", json.dumps(info))


def save_palette(atlas, outdir, key, paints):
    size = atlas.size
    def build(paint_rgb):
        d = Image.new("RGBA", (size, size), (255, 255, 255, 255))
        s = Image.new("RGBA", (size, size), (60, 20, 0, 255))
        for i, (rgb, smooth, g) in enumerate(atlas.cells):
            x0, y0 = (i % COLS) * CELL, (i // COLS) * CELL
            col = paint_rgb if (i == atlas.cell_of.get("paint")) else rgb
            d.paste(tuple(col) + (255,), (x0, y0, x0 + CELL, y0 + CELL))
            s.paste((int(round(smooth * 255)), g, 0, 255), (x0, y0, x0 + CELL, y0 + CELL))
        return d, s
    def hexrgb(h):
        h = h.lstrip("#"); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    first = True
    for name, hx in paints:
        d, s = build(hexrgb(hx))
        fn = key if first else "%s_%s" % (key, name)
        d.save(os.path.join(outdir, fn + ".png")); s.save(os.path.join(outdir, fn + "_s.png"))
        first = False
    if not paints:
        d, s = build((255, 255, 255))
        d.save(os.path.join(outdir, key + ".png")); s.save(os.path.join(outdir, key + "_s.png"))


def export_wheel(key, cfg, outdir, wheel_objs, W, S, dg0, tire_R):
    """Velg kiri (+X) dalam ruang gauge: pusat as di origin, sumbu X, wajah +X."""
    keep = [k.lower() for k in cfg["wheel_keep_materials"]]
    recs = []
    for o in wheel_objs:
        t = world_tris(o, dg0)
        if t is not None:
            recs.append(t)
    # kumpulkan segitiga yang bahannya velg
    Ps, Ns, UVs, mats = [], [], [], []
    for t in recs:
        for mi in np.unique(t["mi"]):
            mat = t["slots"][min(mi, len(t["slots"]) - 1)] if t["slots"] else None
            nm = (mat.name if mat else "none").lower()
            if not any(k in nm for k in keep):
                continue
            sel = np.nonzero(t["mi"] == mi)[0]
            Ps.append(t["P"][sel]); Ns.append(t["N"][sel]); UVs.append(None if t["UV"] is None else t["UV"][sel])
            mats.append((mat, len(sel)))
    P = np.concatenate(Ps); N = np.concatenate(Ns)
    # pusat as = pusat roda kiri depan (W[0]); untuk objek yang tidak tepat di W[0], pakai pusat bbox objek
    ctr = W[0].copy()
    # hanya segitiga yang dekat roda ini (jarak radial < 1.2 x tire_R)
    cen = P.mean(1)
    d = np.linalg.norm((cen - ctr)[:, 1:], axis=1)
    near = d < tire_R * 1.15
    P = P[near]; N = N[near]
    if len(P) == 0:
        print("!! tidak ada segitiga velg dekat pusat roda"); return {}
    if key == "supra":
        # satu mesh = velg + ban: buang segitiga luar (ban), simpan velg
        c2 = np.linalg.norm((P.mean(1) - ctr)[:, 1:], axis=1)
        keep_r = c2 < tire_R * cfg.get("rim_frac", 0.74)
        P = P[keep_r]; N = N[keep_r]
        UVsrc = np.concatenate([u for u in UVs if u is not None])[near][keep_r]
    Pf = P.reshape(-1, 3)
    xs = Pf[:, 0]
    ctr_x = (xs.min() + xs.max()) / 2
    L = P - np.array([ctr_x, ctr[1], ctr[2]])
    # sumbu X = as; pastikan sisi luar (+X). Roda +X => luar = +X (roda kiri)
    if ctr_x < 0:
        L[..., 0] *= -1; N = N.copy(); N[..., 0] *= -1; L = L[:, ::-1]; N = N[:, ::-1]
    R = np.linalg.norm(L.reshape(-1, 3)[:, 1:], axis=1)
    R_rim = float(np.percentile(R, 99.5))
    W_rim = float(L.reshape(-1, 3)[:, 0].max() - L.reshape(-1, 3)[:, 0].min())
    sr = GAUGE_RIM_R / R_rim
    sa = np.clip(GAUGE_RIM_W / max(W_rim, 1e-6), 0.8 * sr, 1.6 * sr)
    print("WHEEL R_rim", R_rim, "W_rim", W_rim, "sr", sr, "sa", sa)
    Q = L.copy()
    Q[..., 0] *= sa; Q[..., 1] *= sr; Q[..., 2] *= sr
    Qn = N.copy()
    # normal: skala non-uniform -> kira-kira
    Qn[..., 0] /= sa; Qn[..., 1] /= sr; Qn[..., 2] /= sr
    Qn /= np.maximum(np.linalg.norm(Qn, axis=-1, keepdims=True), 1e-12)
    # budget: kurangi segitiga dengan sampling merata? (decimate dilakukan di model sumber lewat subsurf) -> cukup
    # palet velg
    if key == "supra":
        from supra_atlas import load_rgb
        tex = os.path.join(cfg["tex_dir"], "Toyota_SupraMKIV_1994_Wheel1A_DiffuseAOSO.png")
        if not os.path.exists(tex):
            tex = os.path.join(cfg["tex_dir"], "Wheel1A_DiffuseAOSO.png")
        load_rgb(tex, 512).save(os.path.join(outdir, key + "_wheel.png"))
        Image.new("RGBA", (512, 512), (215, 255, 0, 255)).save(os.path.join(outdir, key + "_wheel_s.png"))
        if ctr_x < 0:
            pass
        UV = UVsrc.copy()
        UV[..., 1] = UV[..., 1]
        tris, mats_u = write_obj(os.path.join(outdir, key + "_wheel.obj"), [("rim", [("m_rim", Q, Qn, UV)])], key + "_wheel.mtl")
        write_mtl(os.path.join(outdir, key + "_wheel.mtl"), mats_u)
        return dict(tris=tris, R_rim=R_rim, W_rim=W_rim)
    atlas = Atlas(64)
    cell = atlas.cell("rim", (205, 207, 212), 0.93, 255)
    u, v = atlas.cell_uv(cell)
    UV = np.tile(np.array([u, 1.0 - v]), (len(Q), 3, 1))
    tris, mats_u = write_obj(os.path.join(outdir, key + "_wheel.obj"), [("rim", [("m_rim", Q, Qn, UV)])], key + "_wheel.mtl")
    write_mtl(os.path.join(outdir, key + "_wheel.mtl"), mats_u)
    # tekstur
    save_palette(atlas, outdir, key + "_wheel", [])
    return dict(tris=tris, R_rim=R_rim, W_rim=W_rim)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    main(args[0], args[1])
