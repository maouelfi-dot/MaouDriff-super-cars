# Maou Drift (Fabric 1.21.1)

Mod mobil balap untuk Minecraft Fabric 1.21.1, turunan dari **KurumaMod** karya **Deexel** (GPL-3.0-only).
Lihat `NOTICE.md` dan `LICENSE`.

## Isi
- 3 mobil: **BMW M3 GT3** (id `maoudrift:car`, bawaan), **BMW M4 F82** (`maoudrift:m4_f82`), **Toyota Supra MK IV** (`maoudrift:supra`).
  Body, interior, roda, stiker/livery, lampu menyala. Tekstur disertai labPBR `_s` (kilap / logam / emisi) untuk shader Iris.
- **HUD balap baru** (bar RPM LED, kecepatan besar, gigi, pedal, G-force, sudut drift, lampu ABS/TCS/HB/TURBO).
  Hotbar vanilla otomatis hilang saat naik mobil dan muncul lagi saat turun.
- **Tabrakan solid**: mobil tidak bisa ditembus pemain/mob; kotak tabrakan mengikuti panjang & lebar bodi.
- **Pintu dan kap mesin bisa dibuka/tutup**: `Shift + klik kanan` dekat pintu atau di depan mobil. Otomatis menutup saat mobil melaju.
- **Lampu senter nyata**: saat lampu menyala, 3 blok `minecraft:light` kecil (depan 6 m, 13 m, dan bawah mobil) ikut bergerak dan
  menerangi sekitar; ada juga lampu kolong (underglow) cyan. Matikan blok cahaya lewat `CarEntity.dynamicLightBlocks = false`.
- Sorotan lampu dibuat ringan secara bawaan (`headlightRange` 70, `headlightCars` 3, `headlightSmoke` 2).

## Build
Push ke GitHub; workflow `.github/workflows/build.yml` membangun jar (`./gradlew :fabric-1.21.1:build`).
Jar ada di artifact `maoudrift-fabric-1.21.1`.
Atau lokal: JDK 21, lalu `./gradlew :fabric-1.21.1:build`.

## Catatan
- Model dikonversi dari file Blender/FBX milik pemilik proyek memakai `tools/maou_car_export.py` (Blender 4/5 headless).
- Pastikan kamu berhak mendistribusikan model mobil yang dipakai.

## Kilap tanpa shader
Tanpa shader, mobil tetap mengilap lewat pantulan palsu ("matcap") yang digambar tambahan di atas bodi
(`config: fakeShine`, 0 = mati, 1 = normal, 2 = lebih terang). Otomatis mati saat shader pack aktif (shader pakai labPBR sendiri)
dan saat mobil jauh, supaya tetap ringan.
