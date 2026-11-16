"""
blend_to_carprops.py - ubah file .blend mobil jadi folder model siap pakai untuk mod CarProps.

Butuh: Python 3.11 + paket `bpy` + `pillow`   (pip install bpy pillow)

Contoh:
  python blend_to_carprops.py mobil.blend hasil/bmw_m4 --id bmw_m4 --length 4.7 --front=-Y

Hasil di folder tujuan:
  model_hd.obj, model_lo.obj, palette.png, palette_s.png, model.json
Taruh folder itu di:  .minecraft/config/carprops/models/<id>/   (atau di dalam resource pack, lihat README)

Yang dilakukan: modifier (mirror/array/subsurf) + instance (ban duplikat) diterapkan, mobil diputar
supaya depan menghadap +Z Minecraft, diskalakan ke panjang --length blok, tiap material diganti jadi
satu sel warna di palette (cat mobil = satu material "paint" yang bisa diganti warna/finish di game),
dan dibuat tekstur labPBR (_s) supaya mengkilap di shader.
"""
import argparse, json, math, os, re, sys
import numpy as np
import bpy
from PIL import Image

ap = argparse.ArgumentParser()
ap.add_argument("blend"); ap.add_argument("out")
ap.add_argument("--id", default="car")
ap.add_argument("--name", default=None)
ap.add_argument("--length", type=float, default=4.8, help="panjang mobil di Minecraft (blok)")
ap.add_argument("--front", default="-Y", help="arah depan mobil di Blender: -Y (default), +Y, +X, -X")
ap.add_argument("--hd-subsurf", type=int, default=1)
ap.add_argument("--lo-subsurf", type=int, default=0)
ap.add_argument("--hd-tris", type=int, default=90000, help="batas segitiga model HD (dipangkas kalau lebih)")
ap.add_argument("--lo-tris", type=int, default=22000, help="batas segitiga model ringan")
ap.add_argument("--skip", default="", help="potongan nama objek yang dibuang, pisah koma")
ap.add_argument("--color", default="#e0589c"); ap.add_argument("--finish", default="metallic")
ap.add_argument("--steer-wheels", default="FL,FR")
ap.add_argument("--paint", default="", help="nama material Blender yang dianggap cat mobil (pisah koma), selain yang namanya mengandung 'paint'")
args = ap.parse_args(sys.argv[1:])

SKIP = ["ground", "earth", "light_plane", "duplication_plane", "lattice", "backdrop", "studio", "sky", "camera", "floor_plane"]
SKIP += [s.strip().lower() for s in args.skip.split(",") if s.strip()]
FORCE_PAINT = {x.strip().lower() for x in args.paint.split(",") if x.strip()}
WHEEL_RE = re.compile(r"(wheel|rim|tyre|tire|felge|reifen)", re.I)
os.makedirs(args.out, exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=args.blend)

# ---------------- material -> kelas + warna ----------------
def srgb(c): return int(max(0, min(255, round((max(c, 0.0) ** (1 / 2.2)) * 255))))

def mat_color(mat):
    col, emit = None, False
    if mat is not None and mat.use_nodes and mat.node_tree:
        nodes = mat.node_tree.nodes
        emit = any(n.type == 'EMISSION' for n in nodes)
        for n in nodes:
            if n.type == 'BSDF_PRINCIPLED':
                col = tuple(n.inputs['Base Color'].default_value[:3]); break
        if col is None:
            for n in nodes:
                if n.type in ('BSDF_DIFFUSE', 'BSDF_GLOSSY', 'BSDF_GLASS', 'BSDF_TRANSLUCENT', 'EMISSION') and 'Color' in n.inputs:
                    col = tuple(n.inputs['Color'].default_value[:3]); break
    if col is None and mat is not None: col = tuple(mat.diffuse_color[:3])
    if col is None: col = (0.5, 0.5, 0.5)
    return tuple(srgb(c) for c in col), emit

def classify(mat):
    name = (mat.name if mat else "none").lower()
    rgb, emit = mat_color(mat)
    if name in FORCE_PAINT: kind = "paint"
    elif "glass" in name or "windscreen" in name or "window" in name: kind = "glass"
    elif "paint" in name: kind = "paint"
    elif any(k in name for k in ("chrome", "mirror", "miror", "rim", "metal", "silver", "alu", "reflector", "steel", "disc", "bremse")): kind = "metal"
    elif any(k in name for k in ("light", "lamp", "led", "blink", "tailight")) or emit: kind = "light"
    elif any(k in name for k in ("tire", "tyre", "rubber", "reifen")): kind = "rubber"
    elif "piano" in name: kind = "piano"
    else: kind = "plastic"
    return kind, rgb

CELL, COLS = 4, 16
cells = []            # list of (albedo rgb, spec rgba)
cell_of = {}          # key -> index
mat_cache = {}
lights = []

def add_cell(key, alb, spec):
    if key not in cell_of:
        cell_of[key] = len(cells); cells.append((alb, spec))
    return cell_of[key]

# 0..3 = finish cat (putih; warna dari tint)
add_cell("p0", (255, 255, 255), (242, 12, 0, 255))   # gloss
add_cell("p1", (255, 255, 255), (236, 255, 0, 255))  # metallic
add_cell("p2", (255, 255, 255), (70, 12, 0, 255))    # matte
add_cell("p3", (255, 255, 255), (252, 255, 0, 255))  # chrome

def mat_entry(mat):
    key = mat.name if mat else "none"
    if key in mat_cache: return mat_cache[key]
    kind, rgb = classify(mat)
    if kind == "paint": res = ("paint", 0)
    elif kind == "glass":
        c = add_cell("glass", (18, 26, 32), (250, 20, 0, 255)); res = ("glass", c)
    else:
        if kind == "metal":
            alb = tuple(int(120 + 0.53 * v) for v in rgb); spec = (240, 255, 0, 255)
        elif kind == "light":
            alb = rgb; spec = (200, 10, 0, 225)
        elif kind == "rubber":
            alb = tuple(min(v, 40) for v in rgb); spec = (30, 8, 0, 255)
        elif kind == "piano":
            alb = tuple(min(v, 25) for v in rgb); spec = (225, 12, 0, 255)
        else:
            alb = rgb; spec = (90, 10, 0, 255)
        c = add_cell((kind, alb, spec), alb, spec)
        res = ("%s%d" % (kind, c), c)
        if kind == "light" and res[0] not in lights: lights.append(res[0])
    mat_cache[key] = res
    return res

# ---------------- ambil mesh (modifier + instance sudah diterapkan) ----------------
def front_rot(x, y):
    f = args.front
    if f == "-Y": return x, y
    if f == "+Y": return -x, -y
    if f == "+X": return y, -x
    if f == "-X": return -y, x
    raise SystemExit("--front harus -Y, +Y, +X atau -X")

def set_levels(level):
    for o in bpy.data.objects:
        if o.type != 'MESH': continue
        for m in list(o.modifiers):
            if m.name.startswith("cp_decimate"): o.modifiers.remove(m)
            elif m.type == 'SUBSURF': m.levels = level; m.show_viewport = True

def gather():
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    recs, planes = [], []
    for inst in dg.object_instances:
        ob = inst.object
        if ob.type != 'MESH': continue
        orig = ob.original
        low = orig.name.lower()
        if "duplication_plane" in low:            # trik lama Blender: ban diduplikasi lewat plane
            planes.append(np.array(inst.matrix_world, np.float64)); continue
        if orig.hide_render or any(s in low for s in SKIP): continue
        if len(ob.material_slots) == 0: continue
        me = ob.to_mesh()
        if me is None or len(me.polygons) == 0:
            ob.to_mesh_clear(); continue
        me.calc_loop_triangles()
        nv, nl, nt = len(me.vertices), len(me.loops), len(me.loop_triangles)
        if nt == 0: ob.to_mesh_clear(); continue
        co = np.empty(nv * 3, np.float32); me.vertices.foreach_get("co", co)
        lv = np.empty(nl, np.int32); me.loops.foreach_get("vertex_index", lv)
        cn = np.empty(nl * 3, np.float32); me.corner_normals.foreach_get("vector", cn)
        tl = np.empty(nt * 3, np.int32); me.loop_triangles.foreach_get("loops", tl)
        tm = np.empty(nt, np.int32); me.loop_triangles.foreach_get("material_index", tm)
        M = np.array(inst.matrix_world, np.float64)
        R = M[:3, :3]
        P = (co.reshape(-1, 3).astype(np.float64) @ R.T + M[:3, 3])[lv[tl]]
        try: Rn = np.linalg.inv(R).T
        except np.linalg.LinAlgError: Rn = R
        N = cn.reshape(-1, 3).astype(np.float64)[tl] @ Rn.T
        N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
        slots = [s.material for s in ob.material_slots]
        names = np.array([mat_entry(slots[min(i, len(slots) - 1)])[0] for i in tm])
        recs.append(dict(name=orig.name, P=P, N=N, mats=names, tris=nt))
        ob.to_mesh_clear()

    wheel_recs = [r for r in recs if WHEEL_RE.search(r["name"])]
    # buang titik nyasar yang jauh dari ban (mis. vertex sisa)
    for r in wheel_recs:
        cen = np.median(r["P"], axis=0)
        keep = np.linalg.norm(r["P"].reshape(-1, 3, 3).mean(1) - cen, axis=1) < 1.0
        if not keep.all():
            r["P"] = r["P"].reshape(-1, 3, 3)[keep].reshape(-1, 3)
            r["N"] = r["N"].reshape(-1, 3, 3)[keep].reshape(-1, 3)
            r["mats"] = r["mats"][keep]; r["tris"] = int(keep.sum())
    # ban duplikat: terapkan transform tiap plane ke ban asli
    if planes and wheel_recs:
        for Mp in planes:
            for r in wheel_recs:
                Rp = Mp[:3, :3]
                recs.append(dict(name=r["name"] + "_copy", P=r["P"] @ Rp.T + Mp[:3, 3],
                                 N=r["N"] @ Rp.T, mats=r["mats"], tris=r["tris"]))
    # putar supaya depan = -Y
    for r in recs:
        fx, fy = front_rot(r["P"][:, 0].copy(), r["P"][:, 1].copy()); r["P"][:, 0], r["P"][:, 1] = fx, fy
        fx, fy = front_rot(r["N"][:, 0].copy(), r["N"][:, 1].copy()); r["N"][:, 0], r["N"][:, 1] = fx, fy
    return recs

def build(level, target):
    set_levels(level)
    recs = gather()
    total = sum(r["tris"] for r in recs)
    if total > target:
        ratio = max(0.06, target / total)
        per = {}
        for r in recs: per[r["name"].replace("_copy", "")] = per.get(r["name"].replace("_copy", ""), 0) + r["tris"]
        for n, t in per.items():
            if t >= 150:
                m = bpy.data.objects[n].modifiers.new("cp_decimate", 'DECIMATE')
                m.ratio = ratio
        recs = gather()
    return recs

hd = build(args.hd_subsurf, args.hd_tris)
allP = np.concatenate([r["P"] for r in hd])
mn, mx = allP.min(0), allP.max(0)
cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
S = args.length / (mx[1] - mn[1])
dims = dict(length=round(args.length, 2), width=round(float((mx[0] - mn[0]) * S), 2), height=round(float((mx[2] - mn[2]) * S), 2))
print("bbox", mn, mx, "scale", S, "dims", dims)

def part_name(r):
    if WHEEL_RE.search(r["name"]):
        c = r["P"].mean(0)
        return "wheel_" + ("F" if c[1] < cy else "R") + ("L" if c[0] > cx else "R")
    return re.sub(r"[^A-Za-z0-9_]", "_", r["name"])

def mc(P, N):
    Q = np.stack([(P[:, 0] - cx) * S, (P[:, 2] - mn[2]) * S, -(P[:, 1] - cy) * S], 1)
    W = np.stack([N[:, 0], N[:, 2], -N[:, 1]], 1)
    return Q, W

W_PAL = COLS * CELL
def pal_dims():
    rows = math.ceil(len(cells) / COLS)
    h = 4
    while h < rows * CELL: h *= 2
    return W_PAL, h

def cell_uv(i, w, h):
    return ((i % COLS) * CELL + CELL / 2) / w, ((i // COLS) * CELL + CELL / 2) / h

def write_obj(recs, path):
    w, h = pal_dims()
    groups = {}
    for r in recs: groups.setdefault(part_name(r), []).append(r)
    name2cell = {}
    for m, (n, c) in mat_cache.items(): name2cell[n] = c
    used = sorted({c for c in name2cell.values()})
    lines = ["# CarProps export"]
    vt_index = {}
    for c in used:
        u, v = cell_uv(c, w, h); lines.append("vt %.5f %.5f" % (u, 1 - v)); vt_index[c] = len(vt_index) + 1
    vb = nb = 1; tris = 0
    for pname in sorted(groups):
        rs = groups[pname]
        P = np.concatenate([r["P"] for r in rs]); N = np.concatenate([r["N"] for r in rs])
        mats = np.concatenate([r["mats"] for r in rs])
        Q, W = mc(P, N)
        up, ip = np.unique(np.round(Q, 4), axis=0, return_inverse=True)
        un, inn = np.unique(np.round(W, 3), axis=0, return_inverse=True)
        ip = ip.reshape(-1); inn = inn.reshape(-1)
        lines.append("o " + pname)
        lines += ["v %.4f %.4f %.4f" % tuple(p) for p in up]
        lines += ["vn %.3f %.3f %.3f" % tuple(n) for n in un]
        ntri = len(mats)
        ip = ip.reshape(ntri, 3) + vb; inn = inn.reshape(ntri, 3) + nb
        seen = []
        for mname in mats:
            if mname not in seen: seen.append(mname)
        for mname in seen:
            idx = np.nonzero(mats == mname)[0]
            t = vt_index[name2cell[str(mname)]]
            lines.append("usemtl " + str(mname))
            lines += ["f %d/%d/%d %d/%d/%d %d/%d/%d" % (ip[i, 0], t, inn[i, 0], ip[i, 1], t, inn[i, 1], ip[i, 2], t, inn[i, 2]) for i in idx]
            tris += len(idx)
        vb += len(up); nb += len(un)
    open(path, "w").write("\n".join(lines) + "\n")
    return tris

t_hd = write_obj(hd, args.out + "/model_hd.obj")
lo = build(args.lo_subsurf, args.lo_tris)
t_lo = write_obj(lo, args.out + "/model_lo.obj")

# ---------------- palette + labPBR ----------------
w, h = pal_dims()
alb = Image.new("RGBA", (w, h), (255, 255, 255, 255)); spc = Image.new("RGBA", (w, h), (60, 8, 0, 255))
for i, (a, s) in enumerate(cells):
    x0, y0 = (i % COLS) * CELL, (i // COLS) * CELL
    for x in range(CELL):
        for y in range(CELL):
            alb.putpixel((x0 + x, y0 + y), tuple(a) + (255,)); spc.putpixel((x0 + x, y0 + y), tuple(s))
alb.save(args.out + "/palette.png"); spc.save(args.out + "/palette_s.png")

wheels = sorted({part_name(r) for r in hd if WHEEL_RE.search(r["name"])})
steer = ["wheel_" + s.strip() for s in args.steer_wheels.split(",") if ("wheel_" + s.strip()) in wheels]
fin = {n: list(cell_uv(i, w, h)) for n, i in zip(("gloss", "metallic", "matte", "chrome"), range(4))}
meta = {
    "name": args.name or args.id,
    "lods": ["model_hd.obj", "model_lo.obj"], "texture": "palette.png",
    "paintMaterials": ["paint"], "glassMaterials": ["glass"], "lightMaterials": lights,
    "steerParts": steer, "finishes": fin, "defaultFinish": args.finish, "defaultColor": args.color,
    "scale": 1.0, "rotY": 0, "offset": [0, 0, 0], "dims": dims,
}
json.dump(meta, open(args.out + "/model.json", "w"), indent=2)
print("SELESAI tris_hd=%d tris_lo=%d parts=%d wheels=%s lights=%s cells=%d" % (t_hd, t_lo, len({part_name(r) for r in hd}), wheels, lights, len(cells)))
print("config dims:", json.dumps({args.id: dims}))
