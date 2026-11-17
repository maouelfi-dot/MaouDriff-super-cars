"""Atlas tekstur Supra: gabungkan tekstur asli (livery, interior, lampu, grille...) jadi satu PNG + peta kilap labPBR."""
import os, re
import numpy as np
from PIL import Image

W, H = 2048, 1536
CELL = 4
COLS = 16

# kunci (substring nama bahan, huruf kecil) -> (berkas, ukuran tampil, (smooth, G))
SPECS = [
    ("painta_material", "LiveryPaintA_Diffuse.png", 1024, (0.93, 56)),
    ("interiora_material", "InteriorA_DiffuseAOSO.png", 768, (0.20, 22)),
    ("texturedA_material".lower(), "Toyota_SupraMKIV_1994_TexturedA_Diffuse.png", 512, (0.42, 40)),
    ("lighta_material", "Toyota_SupraMKIV_1994_LightA_Diffuse.png", 512, (0.92, 40)),
    ("coloured_material", "Global_Texture_Coloured_Diffuse.png", 256, (0.40, 40)),
    ("carbon2", "common_carbon05_black_diff.png", 128, (0.85, 60)),
    ("grille4a", "Grille4A_DiffuseAOSO.png", 256, (0.55, 230)),
    ("grille1a", "Grille1A_DiffuseAOSO.png", 128, (0.55, 230)),
    ("grille2a", "Toyota_SupraMKIV_1994_Grille2A_DiffuseAOSO.png", 64, (0.5, 230)),
    ("grille3a", "Toyota_SupraMKIV_1994_Grille3A_DiffuseAOSO.png", 64, (0.5, 230)),
    ("grille6a", "Toyota_SupraMKIV_1994_Grille6A_DiffuseAOSO.png", 64, (0.5, 230)),
    ("grille7a", "Toyota_SupraMKIV_1994_Grille7A_DiffuseAOSO.png", 64, (0.5, 230)),
    ("grille8a", "Toyota_SupraMKIV_1994_Grille8A_DiffuseAOSO.png", 64, (0.5, 230)),
    ("manufacturerplate", "Toyota_SupraMKIV_1994_ManufacturerPlateA_Diffuse.png", 256, (0.6, 200)),
]


def load_rgb(path, size):
    im = Image.open(path).convert("RGBA")
    bg = Image.new("RGBA", im.size, (128, 128, 128, 255))
    # kalau alfa dipakai sebagai masker (mis. LA), tampilkan warna aslinya
    rgb = im.convert("RGB")
    return rgb.resize((size, size) if im.size[0] == im.size[1] else (size, max(8, size * im.size[1] // im.size[0])), Image.LANCZOS)


class Region:
    def __init__(self, x, y, w, h):
        self.x, self.y, self.w, self.h = x, y, w, h


class SupraAtlas:
    def __init__(self, atlas):
        self.base = atlas
        self.diff = Image.new("RGB", (W, H), (255, 255, 255))
        self.spec = Image.new("RGBA", (W, H), (60, 20, 0, 255))
        self.regions = {}
        self.cells = {}
        self.shelf_x, self.shelf_y, self.shelf_h = 0, 0, 0

    # ---- palet kecil di pojok kiri-atas (64x64)
    def cell(self, key, rgb, smooth, g):
        if key not in self.cells:
            i = len(self.cells)
            self.cells[key] = i
            x0, y0 = (i % COLS) * CELL, (i // COLS) * CELL
            self.diff.paste(tuple(rgb), (x0, y0, x0 + CELL, y0 + CELL))
            self.spec.paste((int(smooth * 255), g, 0, 255), (x0, y0, x0 + CELL, y0 + CELL))
        i = self.cells[key]
        return ((i % COLS) * CELL + CELL / 2) / W, ((i // COLS) * CELL + CELL / 2) / H

    def place(self, key, im, x, y, smooth, g):
        w, h = im.size
        self.diff.paste(im, (x, y))
        self.spec.paste((int(smooth * 255), g, 0, 255), (x, y, x + w, y + h))
        self.regions[key] = Region(x, y, w, h)

    def pack_all(self, tex_dir):
        # tata letak tetap agar rapi
        cursor = [(0, 64), (1024, 0), (1024, 768), (1536, 768)]
        big = []
        small = []
        for key, fn, size, (sm, g) in SPECS:
            path = os.path.join(tex_dir, fn)
            if not os.path.exists(path):
                alt = os.path.join(tex_dir, "Toyota_SupraMKIV_1994_" + fn)
                path = alt if os.path.exists(alt) else path
            if not os.path.exists(path):
                print("  tekstur tidak ada:", fn); continue
            im = load_rgb(path, size)
            (big if size >= 512 else small).append((key, im, sm, g))
        for (key, im, sm, g), (x, y) in zip(big, cursor):
            self.place(key, im, x, y, sm, g)
        x, y, rowh = 0, 1100, 0
        for key, im, sm, g in small:
            w, h = im.size
            if x + w > W:
                x, y, rowh = 0, y + rowh + 4, 0
            self.place(key, im, x, y, sm, g)
            x += w + 4; rowh = max(rowh, h)

    def region_for(self, matname):
        n = (matname or "").lower().replace("_", "")
        for key, *_ in SPECS:
            if key.replace("_", "") in n:
                return self.regions.get(key)
        return None

    def remap(self, mat, uvs, Ps, kind):
        reg = self.region_for(mat.name if mat else None)
        if reg is not None and uvs is not None:
            u = uvs[..., 0].astype(np.float64); v = uvs[..., 1].astype(np.float64)
            # geser tile supaya tiap segitiga berada di [0,1]
            cu = np.floor(u.mean(axis=1, keepdims=True)); cv = np.floor(v.mean(axis=1, keepdims=True))
            u = np.clip(u - cu, 0.003, 0.997); v = np.clip(v - cv, 0.003, 0.997)
            px = reg.x + u * reg.w
            py = reg.y + (1.0 - v) * reg.h
            out = np.stack([px / W, 1.0 - py / H], axis=-1)
            return out
        # tanpa tekstur -> sel warna
        rgb, sm, g = {
            "plastic": ((22, 22, 24), 0.38, 36), "glass": ((18, 26, 32), 1.0, 70), "color": ((120, 120, 120), 0.45, 40),
            "chrome": ((236, 236, 242), 1.0, 255), "metal": ((120, 122, 128), 0.86, 255),
        }.get(kind, ((22, 22, 24), 0.4, 36))
        if kind in ("light_head", "light_tail"):
            rgb, sm, g = (245, 245, 240), 0.95, 40
        u, v = self.cell(("k", kind, rgb), rgb, sm, g)
        return np.tile(np.array([u, 1.0 - v]), (len(Ps), 3, 1))

    def flat(self, name, Ps):
        u, v = self.cell(("glass",), (18, 26, 32), 1.0, 70)
        return np.tile(np.array([u, 1.0 - v]), (len(Ps), 3, 1))

    def save(self, outdir, key):
        d = self.diff
        s = self.spec
        d.save(os.path.join(outdir, key + ".png"), optimize=True)
        s.save(os.path.join(outdir, key + "_s.png"), optimize=True)


def build_supra_atlas(tex_dir, atlas):
    a = SupraAtlas(atlas)
    a.pack_all(tex_dir)
    return a
