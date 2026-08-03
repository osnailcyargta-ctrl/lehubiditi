// ============================================================
//  SWIPEY SWIPEY — mesin rhythm game 2 lane
//  Input: swipe jari / drag mouse / keyboard. Tanpa dependency.
// ============================================================
'use strict';

const cv = document.getElementById('game');
const cx = cv.getContext('2d');

let W = 960, H = 600, DPR = 1;
function resize() {
  DPR = Math.min(window.devicePixelRatio || 1, 2);
  W = cv.clientWidth || window.innerWidth;
  H = cv.clientHeight || window.innerHeight;
  cv.width = Math.round(W * DPR);
  cv.height = Math.round(H * DPR);
  cx.setTransform(DPR, 0, 0, DPR, 0, 0);
}
window.addEventListener('resize', resize);

// ---------- Warna & konstanta ----------
const DIR_COLOR = { left: '#c24bff', down: '#22e0ff', up: '#4dff8f', right: '#ff2e88' };
const DIR_ANGLE = { up: 0, right: Math.PI / 2, down: Math.PI, left: -Math.PI / 2 };

const JUDGE = [
  { name: 'PERFECT', window: 45, score: 350, acc: 1.00, hp: 2.0, color: '#4dff8f' },
  { name: 'GREAT',   window: 90, score: 200, acc: 0.75, hp: 1.2, color: '#22e0ff' },
  { name: 'GOOD',    window: 145, score: 100, acc: 0.50, hp: 0.4, color: '#ffd24d' },
];
const MISS_WINDOW = 195;   // lewat dari ini = miss
const HP_MISS = -7.5;
const HP_WRONG = -3.5;

const STATE = { MENU: 0, COUNT: 1, PLAY: 2, RESULT: 3, FAIL: 4 };
let state = STATE.MENU;

// ---------- Audio ----------
let actx = null;
let masterGain = null;
function audio() {
  if (!actx) {
    actx = new (window.AudioContext || window.webkitAudioContext)();
    masterGain = actx.createGain();
    masterGain.gain.value = 0.85;
    masterGain.connect(actx.destination);
  }
  if (actx.state === 'suspended') actx.resume();
  return actx;
}

function sfxHit(strength) {
  try {
    const a = audio();
    const t = a.currentTime;
    const o = a.createOscillator();
    const g = a.createGain();
    o.type = 'triangle';
    o.frequency.setValueAtTime(660 + strength * 340, t);
    o.frequency.exponentialRampToValueAtTime(280, t + 0.06);
    g.gain.setValueAtTime(0.10, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.08);
    o.connect(g); g.connect(masterGain);
    o.start(t); o.stop(t + 0.09);
  } catch (e) { /* abaikan */ }
}

function sfxMiss() {
  try {
    const a = audio();
    const t = a.currentTime;
    const o = a.createOscillator();
    const g = a.createGain();
    o.type = 'sawtooth';
    o.frequency.setValueAtTime(150, t);
    o.frequency.exponentialRampToValueAtTime(70, t + 0.14);
    g.gain.setValueAtTime(0.07, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.16);
    o.connect(g); g.connect(masterGain);
    o.start(t); o.stop(t + 0.17);
  } catch (e) { /* abaikan */ }
}

// ---------- Sesi permainan ----------
const LEAD_IN_MS = 2400;      // hitung mundur sebelum lagu jalan
let approachMs = 1700;        // waktu not terlihat sebelum garis hit (scroll speed)
let globalOffset = 0;         // koreksi audio global (ms)

let session = null;
let library = [];             // daftar level tersedia
let menuIndex = 0;
let toast = null;
let returnToEditor = false;

function makeSession(chart, opts) {
  return {
    chart,
    buffer: (opts && opts.buffer) || null,
    source: null,
    builtin: null,
    startCtxTime: 0,
    notes: chart.notes.map((n) => ({ ...n, hit: false, dead: false })),
    idx: 0,
    score: 0, combo: 0, maxCombo: 0,
    counts: { PERFECT: 0, GREAT: 0, GOOD: 0, MISS: 0 },
    accSum: 0, accTotal: 0,
    hp: 55,
    popups: [], sparks: [], lanePulse: [0, 0], recept: {},
    judge: null, judgeT: 0,
    endsAt: chart.notes[chart.notes.length - 1].t + 2200,
    started: false,
  };
}

function songTime() {
  if (!session) return 0;
  return (actx.currentTime - session.startCtxTime) * 1000 - globalOffset;
}

function startChart(chart, opts) {
  const a = audio();
  stopAudio();
  session = makeSession(chart, opts);
  session.startCtxTime = a.currentTime + LEAD_IN_MS / 1000;
  state = STATE.COUNT;
  return session;
}

function beginAudio() {
  if (!session || session.started) return;
  session.started = true;
  const a = audio();
  const at = session.startCtxTime;
  const audioSpec = session.chart.audio || {};
  if (session.buffer) {
    const src = a.createBufferSource();
    src.buffer = session.buffer;
    src.connect(masterGain);
    // kalau start-nya sudah lewat (tab ter-throttle), mulai dari posisi yang benar
    const delta = at - a.currentTime;
    if (delta >= 0) src.start(at);
    else src.start(a.currentTime, Math.min(-delta / 1000 + 0, src.buffer.duration));
    session.source = src;
  } else if (audioSpec.kind === 'builtin') {
    session.builtin = createBuiltinSong(a, masterGain, at);
  }
}

function stopAudio() {
  if (!session) return;
  if (session.source) { try { session.source.stop(); } catch (e) {} session.source = null; }
  if (session.builtin) { session.builtin.stop(); session.builtin = null; }
}

function quitToMenu() {
  stopAudio();
  session = null;
  state = STATE.MENU;
  if (returnToEditor) {
    returnToEditor = false;
    // ditunda satu tick: kalau dipicu oleh Escape, event yang sama
    // jangan sampai langsung menutup editor yang baru dibuka
    setTimeout(() => { if (window.SwipeyEditor) window.SwipeyEditor.open(); }, 0);
  }
}

// ---------- Penilaian ----------
function judgeSwipe(lane, dir) {
  if (state !== STATE.PLAY || !session) return;
  const now = songTime();
  session.lanePulse[lane] = 1;
  session.recept[lane + '|' + dir] = 1;

  let best = null, bestAbs = Infinity;
  for (const n of session.notes) {
    if (n.hit || n.dead || n.lane !== lane) continue;
    const d = n.t - now;
    if (d > MISS_WINDOW) break;
    const abs = Math.abs(d);
    if (abs <= MISS_WINDOW && abs < bestAbs) { best = n; bestAbs = abs; }
  }

  if (!best) { registerWrong(lane, 'MELESET'); return; }
  if (best.dir !== dir) { registerWrong(lane, 'SALAH ARAH'); return; }

  const j = JUDGE.find((x) => bestAbs <= x.window) || JUDGE[JUDGE.length - 1];
  best.hit = true;
  session.counts[j.name]++;
  session.combo++;
  session.maxCombo = Math.max(session.maxCombo, session.combo);
  const mult = 1 + Math.min(session.combo, 100) / 200;
  session.score += Math.round(j.score * mult);
  session.accSum += j.acc; session.accTotal++;
  session.hp = Math.min(100, session.hp + j.hp);
  session.judge = j.name; session.judgeT = 1;
  spawnSpark(lane, dir, j.color);
  sfxHit(j === JUDGE[0] ? 1 : 0.4);
}

function registerWrong(lane, label) {
  session.combo = 0;
  session.hp += HP_WRONG;
  session.judge = label; session.judgeT = 1;
  if (session.hp <= 0) failRun();
}

function missNote(n) {
  n.dead = true;
  session.counts.MISS++;
  session.combo = 0;
  session.accTotal++;
  session.hp += HP_MISS;
  session.judge = 'MISS'; session.judgeT = 1;
  sfxMiss();
  if (session.hp <= 0) failRun();
}

function failRun() {
  session.hp = 0;
  stopAudio();
  state = STATE.FAIL;
}

function spawnSpark(lane, dir, color) {
  const L = laneRect(lane);
  for (let i = 0; i < 12; i++) {
    const a = Math.random() * Math.PI * 2;
    const s = 90 + Math.random() * 200;
    session.sparks.push({
      x: L.cx, y: receptorY(), vx: Math.cos(a) * s, vy: Math.sin(a) * s - 60,
      life: 0.35 + Math.random() * 0.25, color,
    });
  }
  session.popups.push({ x: L.cx, y: receptorY() - 46, life: 0.6, color, dir });
}

// ---------- Tata letak ----------
function laneW() { return Math.max(96, Math.min(200, W * 0.21)); }
function laneGap() { return Math.max(18, W * 0.035); }
function receptorY() { return H - Math.max(110, H * 0.19); }
function laneRect(lane) {
  const w = laneW(), gap = laneGap();
  const total = w * 2 + gap;
  const x0 = (W - total) / 2 + lane * (w + gap);
  return { x: x0, w, cx: x0 + w / 2 };
}
function noteY(dt) {
  const ry = receptorY();
  return ry - (dt / approachMs) * (ry + 90);
}

// ---------- Input ----------
const pointers = new Map();
const SWIPE_MIN = 26;
const SWIPE_COOLDOWN = 70;

function fireSwipe(lane, dir) {
  if (state === STATE.PLAY) judgeSwipe(lane, dir);
}

cv.addEventListener('pointerdown', (e) => {
  try { cv.setPointerCapture(e.pointerId); } catch (err) { /* pointer sintetis */ }
  const r = cv.getBoundingClientRect();
  const x = e.clientX - r.left, y = e.clientY - r.top;
  pointers.set(e.pointerId, { x, y, lane: x < W / 2 ? 0 : 1, last: 0 });
  if (state === STATE.MENU) menuClick(x, y);
  else if (state === STATE.RESULT || state === STATE.FAIL) quitToMenu();
});

cv.addEventListener('pointermove', (e) => {
  const p = pointers.get(e.pointerId);
  if (!p) return;
  const r = cv.getBoundingClientRect();
  const x = e.clientX - r.left, y = e.clientY - r.top;
  const dx = x - p.x, dy = y - p.y;
  if (Math.hypot(dx, dy) < SWIPE_MIN) return;
  const now = performance.now();
  if (now - p.last < SWIPE_COOLDOWN) { p.x = x; p.y = y; return; }
  const dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up');
  fireSwipe(p.lane, dir);
  p.last = now;
  p.x = x; p.y = y;   // reset titik awal → bisa swipe beruntun tanpa angkat jari
});

function endPointer(e) { pointers.delete(e.pointerId); }
cv.addEventListener('pointerup', endPointer);
cv.addEventListener('pointercancel', endPointer);
cv.addEventListener('contextmenu', (e) => e.preventDefault());

const KEYMAP = {
  w: [0, 'up'], s: [0, 'down'], a: [0, 'left'], d: [0, 'right'],
  arrowup: [1, 'up'], arrowdown: [1, 'down'], arrowleft: [1, 'left'], arrowright: [1, 'right'],
};

window.addEventListener('keydown', (e) => {
  const k = e.key.toLowerCase();

  // tombol rahasia editor
  if (e.ctrlKey && e.altKey && k === 'm') {
    e.preventDefault();
    if (window.SwipeyEditor) window.SwipeyEditor.toggle();
    return;
  }
  if (window.SwipeyEditor && window.SwipeyEditor.isOpen()) return;
  if (e.repeat) return;

  if (KEYMAP[k]) {
    e.preventDefault();
    const [lane, dir] = KEYMAP[k];
    if (state === STATE.PLAY) { fireSwipe(lane, dir); return; }
    if (state === STATE.MENU && (k === 'w' || k === 'arrowup')) { menuIndex = Math.max(0, menuIndex - 1); return; }
    if (state === STATE.MENU && (k === 's' || k === 'arrowdown')) { menuIndex = Math.min(library.length - 1, menuIndex + 1); return; }
    return;
  }
  if (k === 'enter' || k === ' ') {
    e.preventDefault();
    if (state === STATE.MENU) playSelected();
    else if (state === STATE.RESULT || state === STATE.FAIL) quitToMenu();
  }
  if (k === 'escape' && (state === STATE.PLAY || state === STATE.COUNT)) quitToMenu();
  if (k === '[') { approachMs = Math.min(3000, approachMs + 100); showToast(`Kecepatan turun (${approachMs}ms)`); }
  if (k === ']') { approachMs = Math.max(600, approachMs - 100); showToast(`Kecepatan naik (${approachMs}ms)`); }
});

function menuClick(x, y) {
  const rows = menuRows();
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    if (x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h) {
      if (menuIndex === i) playSelected();
      else menuIndex = i;
      return;
    }
  }
}

function playSelected() {
  const lvl = library[menuIndex];
  if (!lvl) return;
  startChart(lvl.chart, { buffer: lvl.buffer || null });
}

function showToast(msg) { toast = { msg, life: 2.2 }; }

// ---------- Drag & drop file .lvl ----------
window.addEventListener('dragover', (e) => { e.preventDefault(); });
window.addEventListener('drop', async (e) => {
  e.preventDefault();
  const f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
  if (!f) return;
  try {
    const chart = parseLvl(await f.text());
    let buffer = null;
    if (chart.audio && chart.audio.data) buffer = await decodeDataUri(chart.audio.data);
    addLevel(chart, buffer, 'Dari file');
    showToast(`Level "${chart.title}" dimuat`);
  } catch (err) {
    showToast('Gagal memuat: ' + err.message);
  }
});

async function decodeDataUri(dataUri) {
  const res = await fetch(dataUri);
  const buf = await res.arrayBuffer();
  return await audio().decodeAudioData(buf);
}

function addLevel(chart, buffer, source) {
  const existing = library.findIndex((l) => l.chart.title === chart.title && l.source === source);
  const entry = { chart, buffer: buffer || null, source: source || 'Bawaan' };
  if (existing >= 0) library[existing] = entry;
  else library.push(entry);
  menuIndex = library.indexOf(entry);
  return entry;
}

// ---------- Update ----------
function update(dt) {
  if (!session) return;

  if (state === STATE.COUNT) {
    beginAudio();
    if (songTime() >= 0) state = STATE.PLAY;
  }

  if (state === STATE.PLAY) {
    const now = songTime();
    for (const n of session.notes) {
      if (n.hit || n.dead) continue;
      if (n.t < now - MISS_WINDOW) missNote(n);
    }
    if (now > session.endsAt) { stopAudio(); state = STATE.RESULT; }
  }

  session.judgeT = Math.max(0, session.judgeT - dt * 1.5);
  for (let i = 0; i < 2; i++) session.lanePulse[i] = Math.max(0, session.lanePulse[i] - dt * 4);
  for (const k in session.recept) {
    session.recept[k] = Math.max(0, session.recept[k] - dt * 5);
    if (session.recept[k] <= 0) delete session.recept[k];
  }
  for (let i = session.sparks.length - 1; i >= 0; i--) {
    const s = session.sparks[i];
    s.x += s.vx * dt; s.y += s.vy * dt;
    s.vy += 520 * dt; s.vx *= 0.94;
    s.life -= dt;
    if (s.life <= 0) session.sparks.splice(i, 1);
  }
  for (let i = session.popups.length - 1; i >= 0; i--) {
    const p = session.popups[i];
    p.y -= 42 * dt; p.life -= dt;
    if (p.life <= 0) session.popups.splice(i, 1);
  }
}

// ---------- Gambar ----------
function arrowPath(size) {
  const s = size, t = size * 0.42;
  cx.beginPath();
  cx.moveTo(0, -s);
  cx.lineTo(s, 0.06 * s);
  cx.lineTo(t, 0.06 * s);
  cx.lineTo(t, s * 0.92);
  cx.lineTo(-t, s * 0.92);
  cx.lineTo(-t, 0.06 * s);
  cx.lineTo(-s, 0.06 * s);
  cx.closePath();
}

function drawArrow(x, y, size, dir, color, opts) {
  const o = opts || {};
  cx.save();
  cx.translate(x, y);
  cx.rotate(DIR_ANGLE[dir]);
  cx.globalAlpha = o.alpha == null ? 1 : o.alpha;
  arrowPath(size);
  if (o.outline) {
    cx.lineWidth = o.lineWidth || 3;
    cx.strokeStyle = color;
    if (o.glow) { cx.shadowColor = color; cx.shadowBlur = o.glow; }
    cx.stroke();
  } else {
    if (o.glow) { cx.shadowColor = color; cx.shadowBlur = o.glow; }
    cx.fillStyle = color;
    cx.fill();
    cx.shadowBlur = 0;
    cx.lineWidth = 2;
    cx.strokeStyle = 'rgba(255,255,255,.55)';
    cx.stroke();
  }
  cx.restore();
}

function drawBackground(t) {
  const g = cx.createLinearGradient(0, 0, 0, H);
  g.addColorStop(0, '#0d0820');
  g.addColorStop(0.55, '#08060f');
  g.addColorStop(1, '#120a1c');
  cx.fillStyle = g;
  cx.fillRect(0, 0, W, H);

  // garis grid perspektif
  cx.strokeStyle = '#ffffff0d';
  cx.lineWidth = 1;
  const horizon = H * 0.22;
  for (let i = 0; i <= 14; i++) {
    const p = i / 14;
    const y = horizon + Math.pow(p, 2.1) * (H - horizon);
    cx.beginPath(); cx.moveTo(0, y); cx.lineTo(W, y); cx.stroke();
  }
  for (let i = -7; i <= 7; i++) {
    cx.beginPath();
    cx.moveTo(W / 2 + i * 26, horizon);
    cx.lineTo(W / 2 + i * (W / 6), H);
    cx.stroke();
  }

  // aura denyut mengikuti combo
  if (session && state === STATE.PLAY && session.combo > 0) {
    const pulse = 0.04 + 0.03 * Math.sin(t * 8);
    cx.fillStyle = `rgba(255,46,136,${pulse * Math.min(1, session.combo / 40)})`;
    cx.fillRect(0, 0, W, H);
  }
}

function drawLanes(t) {
  const ry = receptorY();
  for (let lane = 0; lane < 2; lane++) {
    const L = laneRect(lane);
    const pulse = session ? session.lanePulse[lane] : 0;

    const g = cx.createLinearGradient(0, 0, 0, H);
    g.addColorStop(0, 'rgba(255,255,255,0.01)');
    g.addColorStop(1, `rgba(160,120,255,${0.05 + pulse * 0.12})`);
    cx.fillStyle = g;
    cx.fillRect(L.x, 0, L.w, H);

    cx.strokeStyle = `rgba(255,255,255,${0.09 + pulse * 0.25})`;
    cx.lineWidth = 1.5;
    cx.beginPath(); cx.moveTo(L.x, 0); cx.lineTo(L.x, H); cx.stroke();
    cx.beginPath(); cx.moveTo(L.x + L.w, 0); cx.lineTo(L.x + L.w, H); cx.stroke();
  }

  // garis hit
  const gl = cx.createLinearGradient(0, ry - 30, 0, ry + 30);
  gl.addColorStop(0, 'rgba(255,255,255,0)');
  gl.addColorStop(0.5, 'rgba(255,255,255,.18)');
  gl.addColorStop(1, 'rgba(255,255,255,0)');
  const a = laneRect(0), b = laneRect(1);
  cx.fillStyle = gl;
  cx.fillRect(a.x - 10, ry - 30, (b.x + b.w) - a.x + 20, 60);

  // receptor: 4 arah kecil di tiap lane
  const size = Math.min(26, laneW() * 0.17);
  for (let lane = 0; lane < 2; lane++) {
    const L = laneRect(lane);
    DIRS.forEach((dir, i) => {
      const x = L.x + L.w * (0.16 + i * 0.226);
      const lit = session ? (session.recept[lane + '|' + dir] || 0) : 0;
      drawArrow(x, ry, size * (1 + lit * 0.25), dir, DIR_COLOR[dir], {
        outline: lit < 0.15, alpha: 0.34 + lit * 0.66, glow: lit > 0 ? 18 * lit : 0, lineWidth: 2.5,
      });
    });
  }
}

function drawNotes() {
  if (!session) return;
  const now = songTime();
  const size = Math.min(30, laneW() * 0.2);
  for (const n of session.notes) {
    if (n.hit) continue;
    const dt = n.t - now;
    if (dt > approachMs + 200) break;
    if (dt < -MISS_WINDOW - 260) continue;
    const y = noteY(dt);
    if (y < -60 || y > H + 60) continue;
    const L = laneRect(n.lane);
    const i = DIRS.indexOf(n.dir);
    const x = L.x + L.w * (0.16 + i * 0.226);
    const alpha = n.dead ? Math.max(0, 0.5 + dt / 300) : Math.min(1, (approachMs - dt) / 260 + 0.15);
    drawArrow(x, y, size, n.dir, n.dead ? '#5a5470' : DIR_COLOR[n.dir], {
      alpha, glow: n.dead ? 0 : 14,
    });
  }
}

function drawEffects() {
  if (!session) return;
  for (const s of session.sparks) {
    cx.globalAlpha = Math.min(1, s.life * 3);
    cx.fillStyle = s.color;
    cx.fillRect(s.x - 2.5, s.y - 2.5, 5, 5);
  }
  cx.globalAlpha = 1;
  for (const p of session.popups) {
    const k = Math.min(1, p.life * 2.2);
    drawArrow(p.x, p.y, 34 * (1.35 - k * 0.35), p.dir, p.color, { alpha: k * 0.85, outline: true, lineWidth: 3, glow: 20 });
  }
  cx.globalAlpha = 1;
}

function drawHud(t) {
  if (!session) return;
  const pad = 18;

  // bar HP
  const bw = Math.min(430, W * 0.5), bx = (W - bw) / 2, by = pad + 6;
  cx.fillStyle = '#00000073';
  roundRect(bx - 3, by - 3, bw + 6, 20, 10); cx.fill();
  const hp = Math.max(0, Math.min(100, session.hp)) / 100;
  const hg = cx.createLinearGradient(bx, 0, bx + bw, 0);
  hg.addColorStop(0, '#ff2e88'); hg.addColorStop(1, '#22e0ff');
  cx.fillStyle = hg;
  roundRect(bx, by, bw * hp, 14, 7); cx.fill();
  cx.strokeStyle = '#ffffff33'; cx.lineWidth = 1;
  roundRect(bx, by, bw, 14, 7); cx.stroke();
  cx.font = '600 10px system-ui, sans-serif';
  cx.fillStyle = '#ffffff8c'; cx.textAlign = 'center';
  cx.fillText('HP', bx + bw / 2, by + 25);

  // skor & akurasi
  cx.textAlign = 'right';
  cx.font = '800 30px system-ui, sans-serif';
  cx.fillStyle = '#fff';
  cx.fillText(String(session.score).padStart(6, '0'), W - pad, pad + 28);
  const acc = session.accTotal ? (session.accSum / session.accTotal) * 100 : 100;
  cx.font = '600 13px system-ui, sans-serif';
  cx.fillStyle = '#9a93b8';
  cx.fillText(`${acc.toFixed(2)}%  ·  ${session.counts.MISS} miss`, W - pad, pad + 48);

  // judul lagu
  cx.textAlign = 'left';
  cx.font = '700 15px system-ui, sans-serif';
  cx.fillStyle = '#e9e6ff';
  cx.fillText(session.chart.title, pad, pad + 18);
  cx.font = '500 12px system-ui, sans-serif';
  cx.fillStyle = '#9a93b8';
  cx.fillText(`${session.chart.artist} · ${session.chart.difficulty}`, pad, pad + 36);

  // combo & penilaian — ditaruh di sisi kosong kalau layar lebar,
  // supaya tidak menutupi not yang jatuh
  const gutter = laneRect(0).x;
  const wide = gutter > 170;
  const cxPos = wide ? gutter / 2 : W / 2;
  const comboY = wide ? H * 0.5 : H * 0.34;
  const fade = wide ? 1 : 0.55;

  if (session.combo > 1) {
    const k = 1 + session.judgeT * 0.12;
    cx.save();
    cx.globalAlpha = fade;
    cx.translate(cxPos, comboY);
    cx.scale(k, k);
    cx.textAlign = 'center';
    cx.font = '900 54px system-ui, sans-serif';
    cx.fillStyle = '#ffffff';
    cx.shadowColor = '#ff2e88'; cx.shadowBlur = 24;
    cx.fillText(String(session.combo), 0, 0);
    cx.shadowBlur = 0;
    cx.font = '700 14px system-ui, sans-serif';
    cx.fillStyle = '#ff8fc0';
    cx.fillText('COMBO', 0, 22);
    cx.restore();
  }

  if (session.judge && session.judgeT > 0) {
    const j = JUDGE.find((x) => x.name === session.judge);
    cx.textAlign = 'center';
    cx.globalAlpha = Math.min(1, session.judgeT * 1.6) * fade;
    cx.font = '900 30px system-ui, sans-serif';
    cx.fillStyle = j ? j.color : '#ff5470';
    const jy = wide ? comboY + 62 : receptorY() - 96;
    cx.fillText(session.judge, cxPos, jy - (1 - session.judgeT) * 14);
    cx.globalAlpha = 1;
  }

  // progres lagu
  const prog = Math.max(0, Math.min(1, songTime() / Math.max(1, session.endsAt)));
  cx.fillStyle = '#ffffff14';
  cx.fillRect(0, H - 5, W, 5);
  cx.fillStyle = '#22e0ff';
  cx.fillRect(0, H - 5, W * prog, 5);
}

function drawCountdown() {
  const remain = -songTime();
  if (remain <= 0) return;
  const n = Math.ceil(remain / 700);
  const frac = (remain % 700) / 700;
  cx.save();
  cx.textAlign = 'center';
  cx.globalAlpha = Math.min(1, frac * 2);
  cx.font = '900 110px system-ui, sans-serif';
  cx.fillStyle = '#fff';
  cx.shadowColor = '#ff2e88'; cx.shadowBlur = 40;
  cx.fillText(n > 3 ? 'SIAP' : String(n), W / 2, H / 2);
  cx.restore();
  cx.textAlign = 'center';
  cx.font = '600 13px system-ui, sans-serif';
  cx.fillStyle = '#9a93b8';
  cx.fillText('Swipe ke arah panah · WASD / tombol panah juga bisa', W / 2, H / 2 + 46);
}

function menuRows() {
  const rows = [];
  const w = Math.min(560, W * 0.8);
  const x = (W - w) / 2;
  let y = H * 0.36;
  for (let i = 0; i < library.length; i++) {
    rows.push({ x, y, w, h: 60 });
    y += 70;
  }
  return rows;
}

function drawMenu(t) {
  cx.textAlign = 'center';
  const title = 'SWIPEY SWIPEY';
  cx.font = `900 ${Math.min(66, W * 0.09)}px system-ui, sans-serif`;
  const wob = Math.sin(t * 2) * 3;
  cx.fillStyle = '#fff';
  cx.shadowColor = '#ff2e88'; cx.shadowBlur = 32;
  cx.fillText(title, W / 2, H * 0.19 + wob);
  cx.shadowBlur = 0;
  cx.font = '600 14px system-ui, sans-serif';
  cx.fillStyle = '#22e0ff';
  cx.fillText('rhythm game 2 lane · swipe ke arah panahnya', W / 2, H * 0.19 + 34 + wob);

  const rows = menuRows();
  for (let i = 0; i < rows.length; i++) {
    const r = rows[i], lv = library[i], sel = i === menuIndex;
    cx.fillStyle = sel ? '#ff2e8824' : '#ffffff08';
    roundRect(r.x, r.y, r.w, r.h, 12); cx.fill();
    cx.strokeStyle = sel ? '#ff2e88' : '#ffffff1f';
    cx.lineWidth = sel ? 2 : 1;
    roundRect(r.x, r.y, r.w, r.h, 12); cx.stroke();

    cx.textAlign = 'left';
    cx.font = '700 17px system-ui, sans-serif';
    cx.fillStyle = '#fff';
    cx.fillText(lv.chart.title, r.x + 18, r.y + 26);
    cx.font = '500 12px system-ui, sans-serif';
    cx.fillStyle = '#9a93b8';
    const st = chartStats(lv.chart);
    cx.fillText(
      `${lv.chart.artist} · ${lv.chart.difficulty} · ${st.count} not · ${st.nps.toFixed(1)}/dtk · ${lv.source}`,
      r.x + 18, r.y + 45
    );
    if (sel) {
      cx.textAlign = 'right';
      cx.font = '800 13px system-ui, sans-serif';
      cx.fillStyle = '#ff8fc0';
      cx.fillText('ENTER ▸', r.x + r.w - 18, r.y + 36);
    }
  }

  cx.textAlign = 'center';
  cx.font = '500 12.5px system-ui, sans-serif';
  cx.fillStyle = '#6f6890';
  const y = Math.min(H - 60, rows.length ? rows[rows.length - 1].y + 108 : H * 0.6);
  cx.fillText('Swipe / drag mouse ke ↑ ↓ ← →   ·   WASD = lane kiri, panah = lane kanan', W / 2, y);
  cx.fillText('[ dan ] untuk atur kecepatan   ·   tarik file .lvl ke sini untuk memuat level', W / 2, y + 20);
  cx.fillStyle = '#ff2e88';
  cx.font = '700 12.5px system-ui, sans-serif';
  cx.fillText('Ctrl + Alt + M — Studio Chart: bikin level dari lagumu sendiri', W / 2, y + 44);
}

function rankOf(acc, missed) {
  if (missed === 0 && acc >= 99) return { r: 'S+', c: '#ffd24d' };
  if (acc >= 95) return { r: 'S', c: '#ffd24d' };
  if (acc >= 90) return { r: 'A', c: '#4dff8f' };
  if (acc >= 80) return { r: 'B', c: '#22e0ff' };
  if (acc >= 70) return { r: 'C', c: '#c24bff' };
  return { r: 'D', c: '#ff5470' };
}

function drawResult(failed) {
  cx.fillStyle = '#05040bd9';
  cx.fillRect(0, 0, W, H);
  cx.textAlign = 'center';

  const acc = session.accTotal ? (session.accSum / session.accTotal) * 100 : 0;
  const rk = rankOf(acc, session.counts.MISS);

  cx.font = '900 40px system-ui, sans-serif';
  cx.fillStyle = failed ? '#ff5470' : '#fff';
  cx.fillText(failed ? 'GAGAL — HP HABIS' : 'SELESAI!', W / 2, H * 0.2);

  cx.font = '600 15px system-ui, sans-serif';
  cx.fillStyle = '#9a93b8';
  cx.fillText(`${session.chart.title} — ${session.chart.artist}`, W / 2, H * 0.2 + 28);

  if (!failed) {
    cx.font = '900 96px system-ui, sans-serif';
    cx.fillStyle = rk.c;
    cx.shadowColor = rk.c; cx.shadowBlur = 30;
    cx.fillText(rk.r, W / 2, H * 0.44);
    cx.shadowBlur = 0;
  }

  const rows = [
    ['SKOR', String(session.score)],
    ['AKURASI', acc.toFixed(2) + '%'],
    ['COMBO MAKS', String(session.maxCombo)],
    ['PERFECT', String(session.counts.PERFECT)],
    ['GREAT', String(session.counts.GREAT)],
    ['GOOD', String(session.counts.GOOD)],
    ['MISS', String(session.counts.MISS)],
  ];
  const startY = failed ? H * 0.36 : H * 0.53;
  rows.forEach((r, i) => {
    const y = startY + i * 24;
    cx.textAlign = 'right';
    cx.font = '600 13px system-ui, sans-serif';
    cx.fillStyle = '#9a93b8';
    cx.fillText(r[0], W / 2 - 14, y);
    cx.textAlign = 'left';
    cx.font = '700 14px system-ui, sans-serif';
    cx.fillStyle = '#fff';
    cx.fillText(r[1], W / 2 + 14, y);
  });

  cx.textAlign = 'center';
  cx.font = '600 13px system-ui, sans-serif';
  cx.fillStyle = '#6f6890';
  cx.fillText('Klik atau tekan ENTER untuk kembali', W / 2, H - 42);
}

function drawToast() {
  if (!toast) return;
  const a = Math.min(1, toast.life * 1.6);
  cx.globalAlpha = a;
  cx.font = '600 13px system-ui, sans-serif';
  cx.textAlign = 'center';
  const w = cx.measureText(toast.msg).width + 34;
  cx.fillStyle = '#151027';
  roundRect(W / 2 - w / 2, H - 84, w, 34, 9); cx.fill();
  cx.strokeStyle = '#ff2e8899'; cx.lineWidth = 1;
  roundRect(W / 2 - w / 2, H - 84, w, 34, 9); cx.stroke();
  cx.fillStyle = '#e9e6ff';
  cx.fillText(toast.msg, W / 2, H - 62);
  cx.globalAlpha = 1;
}

function roundRect(x, y, w, h, r) {
  cx.beginPath();
  cx.moveTo(x + r, y);
  cx.arcTo(x + w, y, x + w, y + h, r);
  cx.arcTo(x + w, y + h, x, y + h, r);
  cx.arcTo(x, y + h, x, y, r);
  cx.arcTo(x, y, x + w, y, r);
  cx.closePath();
}

// ---------- Loop ----------
let lastT = 0, clock = 0;
function frame(ts) {
  const dt = Math.min(0.05, (ts - lastT) / 1000 || 0.016);
  lastT = ts; clock += dt;

  update(dt);
  drawBackground(clock);

  if (state === STATE.MENU) {
    drawMenu(clock);
  } else {
    drawLanes(clock);
    drawNotes();
    drawEffects();
    drawHud(clock);
    if (state === STATE.COUNT) drawCountdown();
    if (state === STATE.RESULT) drawResult(false);
    if (state === STATE.FAIL) drawResult(true);
  }

  if (toast) { toast.life -= dt; if (toast.life <= 0) toast = null; else drawToast(); }
  requestAnimationFrame(frame);
}

// ---------- Boot ----------
resize();
addLevel(generateBuiltinChart(), null, 'Bawaan');

// coba muat level tambahan dari folder levels/ (jalan di http, diabaikan di file://)
(async () => {
  try {
    const res = await fetch('levels/index.json', { cache: 'no-store' });
    if (!res.ok) return;
    const list = await res.json();
    for (const file of list.levels || []) {
      try {
        const r = await fetch('levels/' + file, { cache: 'no-store' });
        if (!r.ok) continue;
        const chart = parseLvl(await r.text());
        if (library.some((l) => l.chart.title === chart.title)) continue;
        addLevel(chart, null, 'levels/');
      } catch (e) { /* lewati level rusak */ }
    }
    menuIndex = 0;
  } catch (e) { /* offline / file:// */ }
})();

requestAnimationFrame(frame);

// hook untuk editor & pengujian otomatis
window.Swipey = {
  startChart,
  addLevel,
  quitToMenu,
  audio,
  get masterGain() { return masterGain; },
  get session() { return session; },
  get state() { return state; },
  get library() { return library; },
  get songTime() { return session ? songTime() : 0; },
  setReturnToEditor(v) { returnToEditor = !!v; },
  STATE,
};
