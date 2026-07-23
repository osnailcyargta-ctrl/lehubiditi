# ⚔️ Pusaka Nusantara

Top-down action RPG original — pure **HTML5 Canvas + vanilla JavaScript**, tanpa library, tanpa build step.

Kamu adalah **Raka**, penjaga muda sebuah desa di pulau terpencil. Roh bayangan mencuri tiga pusaka keramat desa. Jelajahi pulau, tebas monster, naik level, dan rebut kembali ketiganya:

| Pusaka | Lokasi | Penjaga |
|---|---|---|
| 🗡️ Keris Sukma | Hutan Utara | Slime bayangan |
| 🐉 Gelang Naga | Gurun Timur | Kalajengking pasir |
| 👑 Mahkota Bayang | Kuburan Barat Daya | **Raja Bayangan** (boss) |

## ▶️ Main Sekarang

**https://osnailcyargta-ctrl.github.io/lehubiditi/**

> Kalau link di atas belum aktif, lihat bagian [Setup GitHub Pages](#-setup-github-pages-sekali-saja) di bawah.

Atau jalankan lokal: clone repo ini lalu buka `index.html` di browser. Selesai — tidak perlu install apa pun.

## 🎮 Kontrol

- `WASD` / panah — gerak
- **Klik kiri mouse** — tebas pedang ke arah kursor (bisa diagonal)
- `Spasi` / `J` — tebas pedang ke arah hadap
- `E` / `Enter` — bicara dengan NPC / konfirmasi

## ✨ Fitur

- Dunia pulau 64×48 tile: hutan, danau, gurun, kuburan, desa — dengan jalan setapak antar lokasi
- Combat real-time: ayunan pedang berbentuk busur, knockback, partikel, damage number
- 4 jenis musuh dengan AI beda: slime (lambat), kalajengking (agresif), hantu (nembus tembok!), dan boss yang **ngamuk** saat darahnya tipis
- Sistem level & XP — naik level nambah HP dan serangan
- NPC dengan dialog, quest marker, kompas penunjuk arah objektif
- Auto-save di `localStorage` — progres aman walau tab ditutup
- Efek suara procedural via WebAudio (tanpa file audio)

## 🚀 Setup GitHub Pages (sekali saja)

1. Merge branch ini ke `main`
2. Buka **Settings → Pages** di repo
3. Di bagian **Source**, pilih **GitHub Actions**
4. Workflow `Deploy game ke GitHub Pages` akan jalan otomatis di setiap push ke `main` (atau trigger manual lewat tab **Actions**)
5. Game live di `https://osnailcyargta-ctrl.github.io/lehubiditi/`

## 🗂️ Struktur

```
index.html   — halaman + canvas
game.js      — seluruh game (engine, map, AI, combat, UI)
.github/workflows/deploy.yml — auto-deploy ke GitHub Pages
```
