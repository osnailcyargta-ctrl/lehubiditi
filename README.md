# BlockForge 2D

Game engine 2D untuk Android — **aplikasi APK, bukan halaman HTML**. Kamu membuat game langsung di
HP: susun scene, unggah sprite dan MP3, program dengan blok, mainkan di tempat, lalu **ekspor jadi
proyek Android Studio yang siap di-build**.

```
┌──────────────┐   edit    ┌────────────────┐  ekspor  ┌───────────────────────┐
│  APK Editor  │──────────▶│   game.json    │─────────▶│ Proyek Android Studio │
│ (BlockForge) │           │  + res/ aset   │          │  + workflow build APK │
└──────────────┘           └────────────────┘          └───────────────────────┘
```

---

## Model blok: satu lane ke bawah, cabang ke kanan

Ini yang membedakannya dari Scratch. Blok tidak "menelan" blok lain ke dalam bentuk C. Sebaliknya:

- **Blok kepala** (`saat game dimulai`, `setiap frame`, …) membuka **satu lane utama** yang mengalir
  lurus ke bawah.
- **Blok bercabang** (`jika … maka`, `ulangi`, `selamanya`, `cabang`, …) membuka **lane baru ke
  kanan**, tersambung lewat siku. Kode di lane itu milik cabang tersebut — tidak terlihat oleh lane
  lain.
- Lane **memanjang otomatis** setiap satu blok ditambahkan dan **menyusut satu baris** setiap satu
  blok dihapus. Tinggi lane tidak pernah disimpan; selalu dihitung dari isinya.

```
┌────────────────────────┐
│ ⬤ setiap frame         │        lane utama mengalir ke bawah
└───────────┬────────────┘
            │
┌───────────┴────────────┐        ┌──────────────────────────┐
│ jika ⟨tombol ▶⟩ maka   ├───────▶│ geser x 5 y 0            │  ← cabang, lane ke kanan
└───────────┬────────────┘        └──────────────────────────┘
            │
┌───────────┴────────────┐        ┌──────────────────────────┐
│ jika ⟨tombol A diklik⟩ ├───────▶│ lompat dengan kekuatan…  │
└───────────┬────────────┘        └──────────────────────────┘
            │
┌───────────┴────────────┐
│ jangan keluar layar    │
└────────────────────────┘
```

### Blok yang tersedia (± 85)

| Kategori | Contoh |
|---|---|
| **Kejadian** | `saat game dimulai`, `setiap frame`, `saat menerima pesan`, `saat tombol ditekan/dilepas`, `saat objek disentuh`, `saat {variabel} ≥ {nilai}`, `saat menyentuh objek bertag`, `saat salinan dibuat` |
| **Kontrol** | `tunggu {detik} detik`, `jika … maka`, `jika … maka / kalau tidak`, **`jika … maka ulangi sampai …`**, `ulangi N kali`, `selamanya`, `ulangi sampai`, `selama`, **`cabang {nama}`**, `tunggu sampai`, `siarkan pesan`, `siarkan dan tunggu`, `hentikan` |
| **Gerak** | `maju`, `geser x y`, `pergi ke`, `putar`, `hadap ke arah/objek`, `atur/tambah kecepatan`, `lompat`, `pantul di tepi`, `jangan keluar layar` |
| **Tampilan** | `ganti gambar`, `tampilkan/sembunyikan`, `ukuran`, `transparansi`, `warna`, `katakan`, `lapisan Z` |
| **Suara** | `mainkan efek suara`, `mainkan sampai selesai`, `musik latar`, `hentikan musik`, `volume` |
| **Variabel** | `atur`, `ubah sebanyak`, `tampilkan/sembunyikan`, pembaca nilai |
| **Game** | `buat salinan`, `hapus objek`, `hapus semua bertag`, `pindah scene`, `ulangi scene`, `kamera ikuti`, `guncang kamera`, `keluar` |
| **Sensor** | `tombol sedang ditekan`, **`tombol baru diklik`**, `menyentuh tag/objek/tepi`, `sentuhan x/y`, properti objek, `jarak ke`, `waktu`, `acak`, `jumlah objek bertag` |
| **Operator** | `+ − × ÷`, sisa bagi, perbandingan, `dan/atau/tidak`, fungsi matematika, `min/max`, `batasi`, `gabung` |

---

## Umpan balik visual

- Blok **menyala** saat dieksekusi — pratinjau kecil bisa dijalankan langsung di tab Blok, jadi
  kamu melihat lane mana yang sedang berjalan sambil mengedit.
- Titik sisip berdenyut, `+ blok` di ujung tiap lane, dan bilah sisip menyala saat blok diseret.
- Lane digambar sebagai slab dengan siku penghubung, jadi bentuk program terbaca sebelum teksnya.
- Blok nilai digambar **inline** di dalam slot, bertingkat sampai dua level, dengan warna kategorinya.
- Tekan lama untuk menyeret; blok kepala dipindah bebas di kanvas, blok biasa disisipkan ke lane.
- Editor scene menampilkan kotak fisika, bingkai resolusi, dan pegangan seleksi.
- Undo/redo penuh, dan setiap gestur seret hanya menjadi **satu** langkah undo.

---

## Runtime

- Interpreter berbasis **fiber**: setiap skrip adalah mesin keadaan yang bisa dilanjutkan, bukan
  thread. `selamanya` menghasilkan satu putaran per frame, jadi tidak mungkin membekukan game.
- Fisika AABB: gravitasi, benda statis/dinamis, pantulan, gesekan, deteksi menapak untuk `lompat`.
- Kamera: mengikuti objek, guncangan, letterbox dari resolusi desain ke layar apa pun.
- Input: gamepad virtual di layar, keyboard fisik, dan gamepad — semuanya masuk ke set tombol yang sama.
- Audio: `SoundPool` untuk efek, `MediaPlayer` untuk musik latar.
- `GameView` yang dipakai di tab **Main** adalah **kelas yang sama persis** yang dipakai APK hasil
  ekspor. Yang kamu tes adalah gamenya, bukan simulasinya.

---

## Ekspor ke proyek Android

Tombol Android di kanan atas menulis satu berkas `.zip` berisi proyek Gradle lengkap:

```
NamaGame/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/…
├── .github/workflows/build-apk.yml      ← APK otomatis saat di-push ke GitHub
├── README.md
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/<package-kamu>/GameActivity.kt
        ├── java/com/blockforge/engine/…   ← runtime engine sebagai source
        ├── assets/game.json               ← seluruh game
        ├── assets/res/…                   ← sprite dan audio
        └── res/values/strings.xml, res/drawable/ic_launcher.xml
```

Cara pakai:

```bash
unzip NamaGame-android-project.zip
cd NamaGame
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
```

atau buka foldernya di Android Studio, atau push ke GitHub dan ambil APK-nya dari tab **Actions**.

Proyek hasil ekspor tidak bergantung pada BlockForge sama sekali: satu-satunya dependensi eksternal
adalah `kotlinx-serialization-json`.

---

## Struktur repo

| Modul | Isi |
|---|---|
| `engine/` | Model data, katalog blok, interpreter, fisika, renderer, `GameView`. Tanpa Compose — inilah yang ikut ke proyek hasil ekspor. |
| `app/` | Editor: kanvas blok, editor scene, panel aset, tab main, eksporter. Jetpack Compose. |

Saat `:app` dibangun, sumber `engine/` disalin ke `app/src/main/assets/engine_src/` (bersama Gradle
wrapper) supaya eksporter bisa menuliskannya kembali ke proyek hasil ekspor.

## Build editor-nya

```bash
./gradlew :app:assembleDebug     # → app/build/outputs/apk/debug/app-debug.apk
```

Butuh JDK 17 dan Android SDK 35. Workflow `.github/workflows/android.yml` membangun dan mengunggah
APK editor pada setiap push.
