# CarProps — mobil 3D buat foto & video sinematik (Fabric 1.21.1)

Taruh mobil 3D di dunia Minecraft, atur ukuran/warna/arah, padat (tidak tembus & tidak bisa didorong),
dan mengkilap/menyala saat pakai shader. Belum bisa dikendarai (sengaja, fokus foto/video).

Model bawaan: **bmw_m3_gt3** (Neubi, CC0) dan **bmw_m4_f82** (Blend Swap #81957, CC BY 3.0, fan art, non-komersial).

## 1. Cara build (tanpa PC, lewat GitHub Actions)
1. Buat repo GitHub baru, upload **seluruh isi folder ini** (termasuk folder `.github`).
2. Buka tab **Actions** → tunggu workflow `build` selesai (centang hijau).
3. Buka run-nya → **Artifacts** → unduh `Artifacts` → pakai `carprops-1.0.0.jar` (BUKAN yang `-sources`).
4. Taruh di folder `mods` bersama **Fabric API**. Untuk shader tambahkan **Sodium + Iris** (Fabric 1.21.1).
5. Kalau build merah: salin log error-nya dan kirim ke aku, nanti kuperbaiki.

## 2. Cara pakai di game
* Item **Car Placer** (tab Creative "Tools & Utilities"):
  * klik kanan **blok** = taruh mobil (depan menghadap kamu)
  * klik kanan **udara** = ganti model (daftar `placerModels` di config)
* Klik kanan mobil = putar 15° (jongkok = putar balik). Creative + jongkok + pukul = hapus.
* Perintah (butuh cheat/OP). Yang diubah = mobil yang kamu lihat, atau yang terdekat (maks 32 blok):

| Perintah | Fungsi |
|---|---|
| `/carprop spawn [model] [skala]` | taruh mobil di posisimu |
| `/carprop scale 0.5` | ukuran (0.05 – 30, 1 = ukuran asli ±4,8 blok) |
| `/carprop yaw 90` | arah hadap (derajat) |
| `/carprop pitch 10` / `roll 15` | miringkan hidung / badan (pose dramatis) |
| `/carprop steer 20` | belokkan roda depan (+ = kiri) |
| `/carprop color pink` | warna cat: nama (pink, black, white, red, blue, green, yellow, orange, purple, silver, gray, gold, cyan), hex `ff66aa`, atau `default` |
| `/carprop finish chrome` | `gloss`, `metallic`, `matte`, `chrome`, `default` |
| `/carprop lights true` | lampu menyala (emisif) / mati |
| `/carprop model bmw_m4_f82` | ganti model |
| `/carprop remove`, `/carprop clear [radius]`, `/carprop info` | hapus / hapus semua / lihat status |
| `/carmodels list`, `/carmodels reload` | daftar model terpasang / muat ulang tanpa restart |
| `/carmodels quality low` | `auto`, `high` (selalu HD), `low` (selalu ringan) |

Pindah posisi: `/tp @e[type=carprops:car_prop,limit=1,sort=nearest] ~ ~ ~5`

## 3. Biar mengkilap seperti di foto
Mod menggambar lewat render entity biasa, jadi shader membaca tekstur **labPBR** `palette_s.png`
(kilap, logam, lampu menyala). Syaratnya shader pack kamu mendukung **labPBR/PBR** dan pantulan:
nyalakan opsi PBR/labPBR + Reflections di pengaturan shader (mis. Complementary, BSL, Photon yang ada opsi PBR).
Tanpa PBR, mobil tetap tampil tapi tidak mengkilap. Hasil akhir tergantung shader — ini bagian yang perlu dicoba di HP-mu.

## 4. Hemat RAM/HP (2 GB)
* Tiap model punya 2 level: **HD** (~90 rb segitiga) dan **ringan** (~22–28 rb). Default `quality: auto`
  (HD kalau dekat < 24 blok). Pas jalan-jalan pakai `/carmodels quality low`, pas foto `high`.
* Model baru dibaca saat pertama kali dipakai (di thread terpisah), model yang tidak dipakai tidak dimuat.
* Jarak gambar `renderDistance` (default 96 blok), kecilkan kalau berat. Satu-dua mobil saja untuk HP RAM 2 GB.

## 5. Fisik
Tiap mobil dapat 2–3 kotak tabrakan tak terlihat (mengikuti arah & ukuran). Pemain/mob tidak bisa menembus,
tidak bisa mendorong, piston & air tidak menggeser. Kotak berbentuk persegi (tidak miring mengikuti body),
jadi pinggirnya agak "kotak" — lihat dengan F3+B. Matikan lewat `"collision": false` di config.
Kalau menambah model baru, isi ukurannya di `config/carprops.json` → `models` supaya tabrakannya pas.

## 6. Menambah model dari Blender
Minecraft/mod **tidak bisa membaca .blend langsung**; modelnya harus diubah ke OBJ. Dua cara:

**A. Pakai skrip (di PC, hasil terbaik)** — `tools/blend_to_carprops.py`
```
pip install bpy pillow        # Python 3.11
python blend_to_carprops.py mobil.blend hasil/mobil_baru --id mobil_baru --length 4.7 --front=-Y --name "Mobil Baru"
```
Modifier (mirror/array/subsurf) diterapkan, ban duplikat ikut, material jadi palette, dibuat model HD + ringan,
`_s` (kilap) otomatis, dan dicetak ukuran untuk config. Opsi penting: `--paint NamaMaterialCat` (material cat mobil
yang bisa diganti warna), `--front` (arah depan di Blender), `--skip nama1,nama2` (buang objek).
Kirim file `.blend` ke aku juga boleh, nanti kukonversi.

**B. Manual dari Blender** — `File > Export > Wavefront (.obj)`: Forward **Z**, Up **Y**, centang UV, Normals,
Triangulate. Buat folder `.minecraft/config/carprops/models/<id>/` isi `model.obj`, `texture.png`, dan `model.json`:
```json
{ "name": "Mobil Saya", "lods": ["model.obj"], "texture": "texture.png", "scale": 1.0, "rotY": 0, "offset": [0,0,0] }
```
Lalu `/carmodels reload`. Mobil menghadap +Z (kalau terbalik isi `"rotY": 180`; kalau terlalu besar/kecil ubah `"scale"`).
Tambahkan `"lods": ["hd.obj","ringan.obj"]` untuk dua level detail. Catatan: model di folder config tidak dapat
tekstur kilap (`_s`) dari shader. Supaya kilap, taruh folder yang sama di dalam resource pack:
`assets/carprops/carprops_models/<id>/` (plus `texture_s.png` format labPBR) dan aktifkan pack-nya.

Isi lengkap `model.json`: `paintMaterials` (material yang diwarnai `/carprop color`), `glassMaterials`, `lightMaterials`
(menyala), `steerParts` (nama objek roda depan), `finishes`, `defaultColor`, `defaultFinish`.

## 7. Config `.minecraft/config/carprops.json`
`quality`, `renderDistance`, `lodDistance`, `glassAlpha`, `collision`, `defaultModel`, `defaultScale`,
`placerModels` (daftar model untuk Car Placer — tambahkan id model barumu), `models` (ukuran tiap model).

## 8. Batasan yang jujur
* Kode ini ditulis tanpa bisa dikompilasi/dites di game (server Maven diblokir di lingkungan pembuatnya),
  jadi kemungkinan ada 1–2 error build kecil. Kirim lognya, kuperbaiki.
* Stiker/livery/tekstur gambar dari file .blend tidak ikut (cuma warna per material); cat bisa diganti warna & finish.
* Mobil digambar per-frame (bukan buffer GPU), jadi HD di HP lemah bisa berat — pakai mode `low` saat bergerak.
* Interior M4 ikut dimuat (terlihat lewat kaca transparan).
