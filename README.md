# 🎵 Swipey Swipey

Rhythm game **2 lane** ala FNF — not berbentuk panah ↑ ↓ ← → jatuh di dua kolom, dan cara mainnya
**di-swipe ke arah panahnya**. Pure HTML5 Canvas + vanilla JavaScript. Tanpa library, tanpa build
step, tanpa file audio (lagu bawaannya disintesis langsung di browser).

## ▶️ Main Sekarang

**https://osnailcyargta-ctrl.github.io/lehubiditi/**

Atau lokal: clone repo ini, buka `index.html` di browser. Selesai.

## 🎮 Kontrol

| Aksi | Cara |
|---|---|
| Swipe | **Drag mouse** atau **geser jari** ke arah ↑ ↓ ← → |
| Lane kiri | Mulai swipe di **separuh kiri layar** |
| Lane kanan | Mulai swipe di **separuh kanan layar** |
| Keyboard | **WASD** = lane kiri, **tombol panah** = lane kanan |
| Kecepatan not | `[` lebih lambat, `]` lebih cepat |
| Keluar / kembali | `Esc` |

Swipe tidak perlu angkat jari/mouse — setelah satu arah terdeteksi, titik awal di-reset, jadi bisa
bolak-balik cepat untuk pola rapat.

Penilaian: **PERFECT** (±45ms) · **GREAT** (±90ms) · **GOOD** (±145ms) · **MISS** (>195ms).
Combo menambah pengali skor, dan HP habis = gagal.

## 🎛️ Studio Chart — `Ctrl` + `Alt` + `M`

Tombol rahasia untuk **bikin level sendiri dari lagumu**:

1. **Pilih lagu** (mp3/ogg/wav) — atau pakai lagu bawaan untuk coba-coba
2. **Kalibrasi** — klik *Kalibrasi Otomatis*, ikuti 16 bunyi metronom; keterlambatanmu diukur
   dan jadi nilai offset
3. **Rekam** — lagu diputar, kamu swipe (atau pakai keyboard) mengikuti irama; tiap swipe jadi not
4. **Tes Level** — langsung mainkan chart-mu, `Esc` untuk balik ke studio
5. **Unduh .lvl** — filenya siap dipakai

### Kenapa not-nya nggak ditaruh persis di detik kamu swipe

Manusia selalu telat sedikit dari bunyinya. Kalau not disimpan tepat di waktu swipe, chart-nya
ikut telat dan rasanya meleset saat dimainkan. Jadi rumusnya:

```
waktu not = waktu swipe − offset
```

Not digeser **maju** sebanyak offset, supaya waktu dimainkan not itu sampai di garis hit persis
saat kamu swipe di posisi yang sama. Opsi **snap ke grid** (1/4, 1/8, 1/16) merapikan sisa
selisihnya ke ketukan terdekat berdasarkan BPM.

## 📦 Format `.lvl`

File JSON biasa:

```json
{
  "format": "swipey-lvl",
  "version": 1,
  "title": "Judul Lagu",
  "artist": "Artis",
  "difficulty": "Normal",
  "bpm": 140,
  "offset": 0,
  "audio": { "kind": "builtin", "id": "neon-pasar-malam" },
  "notes": [
    { "t": 1714, "lane": 0, "dir": "up" },
    { "t": 1928, "lane": 1, "dir": "left" }
  ]
}
```

- `t` — waktu not dalam milidetik sejak lagu mulai
- `lane` — `0` (kiri) atau `1` (kanan)
- `dir` — `up` / `down` / `left` / `right`
- `audio.kind` — `builtin` (lagu bawaan), `file` (butuh lagu terpisah), atau `embedded`
  (lagunya ikut tersimpan di dalam `.lvl` sebagai data URI)

## 📥 Memasukkan Level ke Game

**Cara cepat:** tarik file `.lvl` ke jendela game — langsung muncul di menu.

**Cara permanen** (biar ikut ke-deploy):

1. Taruh file `.lvl` di folder `levels/`
2. Tambahkan namanya ke `levels/index.json`:
   ```json
   { "levels": ["contoh.lvl", "levelku.lvl"] }
   ```
3. Push ke `main` — level muncul otomatis di menu

Kalau level butuh lagu terpisah, centang **"Sertakan audio di dalam .lvl"** saat menyimpan supaya
filenya berdiri sendiri.

## 🗂️ Struktur

```
index.html            halaman + UI Studio Chart
js/synth.js           lagu bawaan (disintesis via WebAudio)
js/chart.js           format .lvl + generator level bawaan
js/game.js            mesin game: audio clock, input swipe, penilaian, render
js/editor.js          Studio Chart (perekam + ekspor .lvl)
levels/               level tambahan + manifest
```

Semua timing memakai jam `AudioContext`, bukan `Date.now()`, supaya not tetap sinkron dengan lagu.
