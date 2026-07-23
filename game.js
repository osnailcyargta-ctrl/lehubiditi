// ============================================================
//  PUSAKA NUSANTARA — Top-Down Action RPG
//  Vanilla JS + HTML5 Canvas, tanpa dependency.
//  Jalan langsung dari file:// maupun GitHub Pages.
// ============================================================
'use strict';

const cv = document.getElementById('game');
const cx = cv.getContext('2d');
const W = cv.width, H = cv.height;

const TILE = 32;
const MAPW = 64, MAPH = 48;

// Tile ids
const T = {
  GRASS: 0, TREE: 1, WATER: 2, SAND: 3, PATH: 4,
  FLOWER: 5, ROCK: 6, SHRINE: 7, GRAVE: 8, BRIDGE: 9,
};
const SOLID = new Set([T.TREE, T.WATER, T.ROCK, T.GRAVE]);

// ---------- Seeded RNG (map deterministik) ----------
function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// ---------- Audio kecil (WebAudio beep) ----------
let audioCtx = null;
function beep(freq, dur, type, vol) {
  try {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const o = audioCtx.createOscillator();
    const g = audioCtx.createGain();
    o.type = type || 'square';
    o.frequency.value = freq;
    g.gain.value = vol || 0.04;
    g.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + dur);
    o.connect(g); g.connect(audioCtx.destination);
    o.start(); o.stop(audioCtx.currentTime + dur);
  } catch (e) { /* audio opsional */ }
}
const sfx = {
  hit: () => beep(200, 0.08, 'sawtooth', 0.05),
  hurt: () => beep(110, 0.2, 'sawtooth', 0.06),
  swing: () => beep(500, 0.05, 'triangle', 0.03),
  pickup: () => { beep(660, 0.08); setTimeout(() => beep(880, 0.12), 80); },
  relic: () => { beep(523, 0.12); setTimeout(() => beep(659, 0.12), 120); setTimeout(() => beep(784, 0.25), 240); },
  levelup: () => { beep(440, 0.1); setTimeout(() => beep(554, 0.1), 100); setTimeout(() => beep(659, 0.1), 200); setTimeout(() => beep(880, 0.2), 300); },
  die: () => beep(80, 0.5, 'sawtooth', 0.07),
};

// ---------- Peta ----------
const map = new Uint8Array(MAPW * MAPH);
const mget = (x, y) => (x < 0 || y < 0 || x >= MAPW || y >= MAPH) ? T.TREE : map[y * MAPW + x];
const mset = (x, y, v) => { if (x >= 0 && y >= 0 && x < MAPW && y < MAPH) map[y * MAPW + x] = v; };
const isSolid = (x, y) => SOLID.has(mget(x, y));

// Titik penting (dalam tile)
const VILLAGE = { x: 32, y: 28 };
const SHRINE_N = { x: 31, y: 6 };    // hutan utara  — Keris Sukma
const SHRINE_E = { x: 56, y: 25 };   // gurun timur  — Gelang Naga
const SHRINE_W = { x: 7, y: 41 };    // kuburan barat daya — Mahkota Bayang (boss)

function carvePath(x0, y0, x1, y1) {
  // Jalur bentuk L: horizontal dulu, lalu vertikal. Air jadi jembatan.
  let x = x0, y = y0;
  const put = (tx, ty) => {
    for (let dy = 0; dy <= 1; dy++) for (let dx = 0; dx <= 1; dx++) {
      const t = mget(tx + dx, ty + dy);
      mset(tx + dx, ty + dy, t === T.WATER ? T.BRIDGE : T.PATH);
    }
  };
  while (x !== x1) { put(x, y); x += Math.sign(x1 - x); }
  while (y !== y1) { put(x, y); y += Math.sign(y1 - y); }
  put(x1, y1);
}

function clearArea(cx0, cy0, r, tile) {
  for (let y = cy0 - r; y <= cy0 + r; y++)
    for (let x = cx0 - r; x <= cx0 + r; x++)
      if ((x - cx0) * (x - cx0) + (y - cy0) * (y - cy0) <= r * r) mset(x, y, tile);
}

function genMap() {
  const rng = mulberry32(20260723);
  map.fill(T.GRASS);

  // Danau
  for (let i = 0; i < 7; i++) {
    let x = 4 + Math.floor(rng() * (MAPW - 8));
    let y = 4 + Math.floor(rng() * (MAPH - 8));
    for (let j = 0; j < 40; j++) {
      clearArea(x, y, 1 + Math.floor(rng() * 2), T.WATER);
      x += Math.floor(rng() * 3) - 1;
      y += Math.floor(rng() * 3) - 1;
    }
  }
  // Hutan (gerombolan pohon)
  for (let i = 0; i < 60; i++) {
    const x = 2 + Math.floor(rng() * (MAPW - 4));
    const y = 2 + Math.floor(rng() * (MAPH - 4));
    const n = 3 + Math.floor(rng() * 6);
    for (let j = 0; j < n; j++) {
      const tx = x + Math.floor(rng() * 4) - 2;
      const ty = y + Math.floor(rng() * 4) - 2;
      if (mget(tx, ty) === T.GRASS) mset(tx, ty, T.TREE);
    }
  }
  // Bunga & batu tersebar
  for (let i = 0; i < 220; i++) {
    const x = Math.floor(rng() * MAPW), y = Math.floor(rng() * MAPH);
    if (mget(x, y) === T.GRASS) mset(x, y, rng() < 0.8 ? T.FLOWER : T.ROCK);
  }
  // Hutan utara lebih rapat
  for (let y = 2; y < 12; y++) for (let x = 4; x < MAPW - 4; x++)
    if (mget(x, y) === T.GRASS && rng() < 0.28) mset(x, y, T.TREE);
  // Gurun timur
  clearArea(SHRINE_E.x, SHRINE_E.y, 9, T.SAND);
  for (let i = 0; i < 24; i++) {
    const a = rng() * Math.PI * 2, r = 4 + rng() * 5;
    const x = Math.round(SHRINE_E.x + Math.cos(a) * r);
    const y = Math.round(SHRINE_E.y + Math.sin(a) * r);
    if (mget(x, y) === T.SAND && rng() < 0.5) mset(x, y, T.ROCK);
  }
  // Kuburan barat daya
  clearArea(SHRINE_W.x, SHRINE_W.y, 8, T.GRASS);
  for (let i = 0; i < 26; i++) {
    const a = rng() * Math.PI * 2, r = 3 + rng() * 5;
    const x = Math.round(SHRINE_W.x + Math.cos(a) * r);
    const y = Math.round(SHRINE_W.y + Math.sin(a) * r);
    if (!SOLID.has(mget(x, y))) mset(x, y, T.GRAVE);
  }
  // Desa (lapangan tengah)
  clearArea(VILLAGE.x, VILLAGE.y, 6, T.GRASS);
  clearArea(VILLAGE.x, VILLAGE.y, 3, T.PATH);
  // Altar pusaka
  for (const s of [SHRINE_N, SHRINE_E, SHRINE_W]) {
    clearArea(s.x, s.y, 3, s === SHRINE_E ? T.SAND : T.GRASS);
    for (let y = s.y - 1; y <= s.y + 1; y++)
      for (let x = s.x - 1; x <= s.x + 1; x++) mset(x, y, T.SHRINE);
  }
  // Jalur desa -> tiap altar
  carvePath(VILLAGE.x, VILLAGE.y, SHRINE_N.x, SHRINE_N.y + 2);
  carvePath(VILLAGE.x, VILLAGE.y, SHRINE_E.x - 2, SHRINE_E.y);
  carvePath(VILLAGE.x, VILLAGE.y, SHRINE_W.x, SHRINE_W.y - 2);
  // Pagar pohon keliling pulau
  for (let x = 0; x < MAPW; x++) { mset(x, 0, T.TREE); mset(x, 1, T.TREE); mset(x, MAPH - 1, T.TREE); mset(x, MAPH - 2, T.TREE); }
  for (let y = 0; y < MAPH; y++) { mset(0, y, T.TREE); mset(1, y, T.TREE); mset(MAPW - 1, y, T.TREE); mset(MAPW - 2, y, T.TREE); }
}

// ---------- State ----------
const STATE = { TITLE: 0, PLAY: 1, DIALOG: 2, DEAD: 3, WIN: 4 };
let state = STATE.TITLE;
let time = 0;

const player = {
  x: 0, y: 0, w: 18, h: 22,
  speed: 150, dir: 'down',
  hp: 50, maxHp: 50, atk: 10,
  lvl: 1, xp: 0,
  attackT: 0, attackCd: 0, invuln: 0, attackAngle: Math.PI / 2,
  vx: 0, vy: 0, walkT: 0,
};
const xpNeed = () => player.lvl * 20;

let enemies = [];
let drops = [];      // hati penyembuh
let particles = [];
let floaters = [];   // teks damage melayang
let relics = [];     // {id,name,x,y,taken}
let npcs = [];
let dialog = null;   // {lines:[], i, name}
let deathT = 0, winT = 0;
let hint = '';

const ENEMY_TYPES = {
  slime:  { hp: 20,  dmg: 8,  spd: 40,  xp: 8,   aggro: 140, size: 20, color: '#7a4fd0', ghost: false },
  scorp:  { hp: 30,  dmg: 10, spd: 75,  xp: 12,  aggro: 170, size: 20, color: '#c97b2a', ghost: false },
  ghost:  { hp: 40,  dmg: 12, spd: 55,  xp: 16,  aggro: 190, size: 22, color: '#9fd8e8', ghost: true  },
  boss:   { hp: 300, dmg: 20, spd: 60,  xp: 120, aggro: 260, size: 40, color: '#3a2b5c', ghost: false },
};

function spawnEnemy(type, tx, ty) {
  const t = ENEMY_TYPES[type];
  enemies.push({
    type, x: tx * TILE + TILE / 2, y: ty * TILE + TILE / 2,
    hp: t.hp, maxHp: t.hp, dmg: t.dmg, spd: t.spd, xp: t.xp,
    aggro: t.aggro, size: t.size, ghost: t.ghost,
    homeX: tx * TILE + TILE / 2, homeY: ty * TILE + TILE / 2,
    wanderT: 0, wx: 0, wy: 0, hitT: 0, kbx: 0, kby: 0, bob: Math.random() * 6,
  });
}

function populate() {
  enemies = []; drops = []; particles = []; floaters = [];
  const rng = mulberry32(777);
  const near = (s, type, n, r) => {
    for (let i = 0; i < n; i++) {
      let tx, ty, tries = 0;
      do {
        const a = rng() * Math.PI * 2, d = 3 + rng() * r;
        tx = Math.round(s.x + Math.cos(a) * d);
        ty = Math.round(s.y + Math.sin(a) * d);
        tries++;
      } while (isSolid(tx, ty) && tries < 30);
      if (!isSolid(tx, ty)) spawnEnemy(type, tx, ty);
    }
  };
  near(SHRINE_N, 'slime', 7, 6);
  near(SHRINE_E, 'scorp', 6, 6);
  near(SHRINE_W, 'ghost', 5, 6);
  // Boss penjaga Mahkota Bayang
  if (!relics[2].taken) spawnEnemy('boss', SHRINE_W.x + 2, SHRINE_W.y + 2);
  // Slime liar di antara desa dan altar
  const wilds = [[32, 16], [26, 20], [40, 22], [46, 25], [20, 33], [14, 36], [38, 34], [24, 12]];
  for (const [tx, ty] of wilds) if (!isSolid(tx, ty)) spawnEnemy('slime', tx, ty);
}

function setupWorld() {
  genMap();
  relics = [
    { id: 0, name: 'Keris Sukma',    x: SHRINE_N.x * TILE + TILE / 2, y: SHRINE_N.y * TILE + TILE / 2, taken: false },
    { id: 1, name: 'Gelang Naga',    x: SHRINE_E.x * TILE + TILE / 2, y: SHRINE_E.y * TILE + TILE / 2, taken: false },
    { id: 2, name: 'Mahkota Bayang', x: SHRINE_W.x * TILE + TILE / 2, y: SHRINE_W.y * TILE + TILE / 2, taken: false },
  ];
  npcs = [
    {
      name: 'Ki Jaga', x: (VILLAGE.x - 2) * TILE, y: (VILLAGE.y - 2) * TILE, color: '#d8c9a3',
      talk() {
        const n = relics.filter(r => r.taken).length;
        if (n >= 3) return null; // menang, ditangani khusus
        if (n === 0) return [
          'Raka... roh bayangan mencuri tiga pusaka desa kita.',
          'Keris Sukma dibawa ke hutan utara. Gelang Naga ke gurun timur.',
          'Dan Mahkota Bayang... dijaga Raja Bayangan di kuburan barat daya.',
          'Ikuti jalan setapak. Kembalilah padaku jika ketiganya sudah di tanganmu!',
        ];
        if (n < 3) return [
          `Bagus! Sudah ${n} pusaka kau selamatkan.`,
          'Sisanya menunggumu. Hati-hati, Raja Bayangan tidak kenal ampun.',
        ];
      },
    },
    {
      name: 'Mbok Sari', x: (VILLAGE.x + 2) * TILE, y: (VILLAGE.y + 1) * TILE, color: '#e8a7b0',
      talk: () => [
        'Kalau terluka, kalahkan monster — kadang mereka menjatuhkan hati penyembuh.',
        'Naik level juga memulihkan tenagamu sepenuhnya, lho.',
      ],
    },
    {
      name: 'Bocah Udin', x: (VILLAGE.x) * TILE, y: (VILLAGE.y + 3) * TILE, color: '#a7c8e8',
      talk: () => [
        'Kak Raka! Katanya hantu di kuburan bisa nembus pohon dan air!',
        'Serem... tapi kakak pasti bisa. Tebas pakai klik kiri ya kak!',
      ],
    },
  ];
  player.x = VILLAGE.x * TILE + TILE / 2;
  player.y = (VILLAGE.y + 1) * TILE + TILE / 2;
}

// ---------- Save ----------
const SAVE_KEY = 'pusaka-nusantara-save';
function save() {
  try {
    localStorage.setItem(SAVE_KEY, JSON.stringify({
      lvl: player.lvl, xp: player.xp, maxHp: player.maxHp, atk: player.atk,
      relics: relics.map(r => r.taken),
    }));
  } catch (e) { /* mode privat dsb. */ }
}
function loadSave() {
  try {
    const s = JSON.parse(localStorage.getItem(SAVE_KEY));
    if (!s) return false;
    player.lvl = s.lvl; player.xp = s.xp; player.maxHp = s.maxHp;
    player.atk = s.atk; player.hp = s.maxHp;
    s.relics.forEach((t, i) => relics[i].taken = t);
    return true;
  } catch (e) { return false; }
}
function clearSave() { try { localStorage.removeItem(SAVE_KEY); } catch (e) {} }
function hasSave() { try { return !!localStorage.getItem(SAVE_KEY); } catch (e) { return false; } }

// ---------- Input ----------
const keys = {};
let interactPressed = false, attackPressed = false, confirmPressed = false;
window.addEventListener('keydown', (e) => {
  if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', ' '].includes(e.key)) e.preventDefault();
  if (e.repeat) return;
  keys[e.key.toLowerCase()] = true;
  if (e.key === ' ' || e.key.toLowerCase() === 'j') attackPressed = true;
  if (e.key.toLowerCase() === 'e' || e.key === 'Enter') { interactPressed = true; confirmPressed = true; }
  if (e.key === 'Backspace' && state === STATE.TITLE) { clearSave(); }
});
window.addEventListener('keyup', (e) => { keys[e.key.toLowerCase()] = false; });

// Mouse: klik kiri = serang ke arah kursor
const mouse = { x: W / 2, y: H / 2 };
let mouseAttack = false;
function canvasPos(e) {
  const r = cv.getBoundingClientRect();
  return { x: (e.clientX - r.left) / r.width * W, y: (e.clientY - r.top) / r.height * H };
}
cv.addEventListener('mousemove', (e) => {
  const p = canvasPos(e);
  mouse.x = p.x; mouse.y = p.y;
});
cv.addEventListener('mousedown', (e) => {
  if (e.button !== 0) return;
  if (state === STATE.PLAY) {
    attackPressed = true;
    mouseAttack = true;
  } else {
    confirmPressed = true; interactPressed = true;
  }
});
cv.addEventListener('contextmenu', (e) => e.preventDefault());

// ---------- Tabrakan ----------
function collideMove(ent, dx, dy) {
  const hw = ent.w / 2, hh = ent.h / 2;
  const free = (x, y) =>
    !isSolid(Math.floor((x - hw) / TILE), Math.floor((y - hh) / TILE)) &&
    !isSolid(Math.floor((x + hw) / TILE), Math.floor((y - hh) / TILE)) &&
    !isSolid(Math.floor((x - hw) / TILE), Math.floor((y + hh) / TILE)) &&
    !isSolid(Math.floor((x + hw) / TILE), Math.floor((y + hh) / TILE));
  if (free(ent.x + dx, ent.y)) ent.x += dx;
  if (free(ent.x, ent.y + dy)) ent.y += dy;
}

// ---------- Partikel & teks ----------
function burst(x, y, color, n) {
  for (let i = 0; i < n; i++) {
    const a = Math.random() * Math.PI * 2, s = 30 + Math.random() * 90;
    particles.push({ x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s, life: 0.4 + Math.random() * 0.3, color });
  }
}
function floatText(x, y, text, color) {
  floaters.push({ x, y, text, color, life: 0.9 });
}

// ---------- Update ----------
function gainXp(n) {
  player.xp += n;
  floatText(player.x, player.y - 24, `+${n} XP`, '#ffe066');
  while (player.xp >= xpNeed()) {
    player.xp -= xpNeed();
    player.lvl++;
    player.maxHp += 10;
    player.atk += 2;
    player.hp = player.maxHp;
    floatText(player.x, player.y - 40, `LEVEL ${player.lvl}!`, '#7CFC9A');
    burst(player.x, player.y, '#7CFC9A', 20);
    sfx.levelup();
  }
  save();
}

function hurtPlayer(dmg, fromX, fromY) {
  if (player.invuln > 0) return;
  player.hp -= dmg;
  player.invuln = 1.0;
  const a = Math.atan2(player.y - fromY, player.x - fromX);
  collideMove(player, Math.cos(a) * 20, Math.sin(a) * 20);
  floatText(player.x, player.y - 24, `-${dmg}`, '#ff6b6b');
  burst(player.x, player.y, '#ff6b6b', 8);
  sfx.hurt();
  if (player.hp <= 0) { player.hp = 0; state = STATE.DEAD; deathT = 0; sfx.die(); }
}

function tryInteract() {
  for (const n of npcs) {
    const d = Math.hypot(n.x + 16 - player.x, n.y + 16 - player.y);
    if (d < 52) {
      const taken = relics.filter(r => r.taken).length;
      if (n.name === 'Ki Jaga' && taken >= 3) {
        state = STATE.WIN; winT = 0; clearSave(); sfx.relic();
        return true;
      }
      const lines = n.talk();
      if (lines) { dialog = { lines, i: 0, name: n.name }; state = STATE.DIALOG; }
      return true;
    }
  }
  return false;
}

function updatePlay(dt) {
  // --- gerak pemain ---
  let mx = 0, my = 0;
  if (keys['w'] || keys['arrowup']) my -= 1;
  if (keys['s'] || keys['arrowdown']) my += 1;
  if (keys['a'] || keys['arrowleft']) mx -= 1;
  if (keys['d'] || keys['arrowright']) mx += 1;
  const ml = Math.hypot(mx, my);
  if (ml > 0) {
    mx /= ml; my /= ml;
    player.walkT += dt * 8;
    if (Math.abs(mx) > Math.abs(my)) player.dir = mx > 0 ? 'right' : 'left';
    else player.dir = my > 0 ? 'down' : 'up';
    collideMove(player, mx * player.speed * dt, my * player.speed * dt);
  }
  player.vx = mx; player.vy = my;
  player.invuln = Math.max(0, player.invuln - dt);
  player.attackT = Math.max(0, player.attackT - dt);
  player.attackCd = Math.max(0, player.attackCd - dt);

  // --- interaksi NPC ---
  if (interactPressed) {
    const did = tryInteract();
    if (did) { interactPressed = false; attackPressed = false; confirmPressed = false; }
  }

  // --- serangan ---
  if (attackPressed && player.attackCd <= 0) {
    player.attackT = 0.18;
    player.attackCd = 0.35;
    sfx.swing();
    let dirA;
    if (mouseAttack) {
      // serang ke arah kursor mouse (posisi dunia)
      dirA = Math.atan2(mouse.y + cam.y - player.y, mouse.x + cam.x - player.x);
      player.dir = Math.abs(Math.cos(dirA)) > Math.abs(Math.sin(dirA))
        ? (Math.cos(dirA) > 0 ? 'right' : 'left')
        : (Math.sin(dirA) > 0 ? 'down' : 'up');
    } else {
      dirA = { up: -Math.PI / 2, down: Math.PI / 2, left: Math.PI, right: 0 }[player.dir];
    }
    player.attackAngle = dirA;
    for (const e of enemies) {
      const dx = e.x - player.x, dy = e.y - player.y;
      const d = Math.hypot(dx, dy);
      if (d < 54 + e.size / 2) {
        const da = Math.atan2(dy, dx);
        let diff = Math.abs(da - dirA);
        if (diff > Math.PI) diff = Math.PI * 2 - diff;
        if (diff < 1.3) {
          e.hp -= player.atk;
          e.hitT = 0.15;
          const kb = e.type === 'boss' ? 60 : 160;
          e.kbx = Math.cos(da) * kb; e.kby = Math.sin(da) * kb;
          floatText(e.x, e.y - e.size, `${player.atk}`, '#fff');
          burst(e.x, e.y, ENEMY_TYPES[e.type].color, 6);
          sfx.hit();
        }
      }
    }
  }
  attackPressed = false; interactPressed = false; mouseAttack = false;

  // --- musuh ---
  for (const e of enemies) {
    e.hitT = Math.max(0, e.hitT - dt);
    e.bob += dt * 4;
    // knockback
    if (Math.abs(e.kbx) > 1 || Math.abs(e.kby) > 1) {
      if (e.ghost) { e.x += e.kbx * dt; e.y += e.kby * dt; }
      else collideMove(e, e.kbx * dt, e.kby * dt);
      e.kbx *= 0.8; e.kby *= 0.8;
    }
    const dx = player.x - e.x, dy = player.y - e.y;
    const d = Math.hypot(dx, dy);
    let vx = 0, vy = 0;
    if (d < e.aggro && d > 1) {
      vx = dx / d; vy = dy / d;
      // boss ngamuk kalau darahnya tipis
      if (e.type === 'boss' && e.hp < e.maxHp * 0.35) { vx *= 1.6; vy *= 1.6; }
    } else {
      e.wanderT -= dt;
      if (e.wanderT <= 0) {
        e.wanderT = 1 + Math.random() * 2;
        const a = Math.random() * Math.PI * 2;
        const roam = Math.random() < 0.3 ? 0 : 1;
        e.wx = Math.cos(a) * roam; e.wy = Math.sin(a) * roam;
        // balik ke rumah kalau kejauhan
        const hd = Math.hypot(e.homeX - e.x, e.homeY - e.y);
        if (hd > 160) { e.wx = (e.homeX - e.x) / hd; e.wy = (e.homeY - e.y) / hd; }
      }
      vx = e.wx * 0.5; vy = e.wy * 0.5;
    }
    if (e.ghost) { e.x += vx * e.spd * dt; e.y += vy * e.spd * dt; }
    else collideMove(e, vx * e.spd * dt, vy * e.spd * dt);
    // hantu jangan keluar peta
    e.x = Math.max(TILE * 2, Math.min(TILE * (MAPW - 2), e.x));
    e.y = Math.max(TILE * 2, Math.min(TILE * (MAPH - 2), e.y));

    if (d < (e.size + player.w) / 2 + 2) hurtPlayer(e.dmg, e.x, e.y);
  }
  // musuh mati
  for (let i = enemies.length - 1; i >= 0; i--) {
    const e = enemies[i];
    if (e.hp <= 0) {
      burst(e.x, e.y, ENEMY_TYPES[e.type].color, 16);
      gainXp(e.xp);
      if (Math.random() < 0.3 || e.type === 'boss') drops.push({ x: e.x, y: e.y, t: 0 });
      if (e.type === 'boss') {
        floatText(e.x, e.y - 40, 'RAJA BAYANGAN TUMBANG!', '#ffd700');
      }
      enemies.splice(i, 1);
    }
  }

  // --- drop hati ---
  for (let i = drops.length - 1; i >= 0; i--) {
    const dr = drops[i];
    dr.t += dt;
    if (Math.hypot(dr.x - player.x, dr.y - player.y) < 20) {
      player.hp = Math.min(player.maxHp, player.hp + 20);
      floatText(player.x, player.y - 24, '+20 HP', '#7CFC9A');
      sfx.pickup();
      drops.splice(i, 1);
    } else if (dr.t > 25) drops.splice(i, 1);
  }

  // --- pusaka ---
  for (const r of relics) {
    if (r.taken) continue;
    if (r.id === 2 && enemies.some(e => e.type === 'boss')) continue; // dijaga boss
    if (Math.hypot(r.x - player.x, r.y - player.y) < 26) {
      r.taken = true;
      floatText(player.x, player.y - 30, `${r.name} DIDAPAT!`, '#ffd700');
      burst(r.x, r.y, '#ffd700', 24);
      sfx.relic();
      save();
    }
  }

  // --- partikel & teks ---
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.x += p.vx * dt; p.y += p.vy * dt;
    p.vx *= 0.92; p.vy *= 0.92;
    p.life -= dt;
    if (p.life <= 0) particles.splice(i, 1);
  }
  for (let i = floaters.length - 1; i >= 0; i--) {
    const f = floaters[i];
    f.y -= 30 * dt; f.life -= dt;
    if (f.life <= 0) floaters.splice(i, 1);
  }

  // --- petunjuk ---
  const taken = relics.filter(r => r.taken).length;
  if (taken >= 3) hint = 'Semua pusaka terkumpul! Kembali ke Ki Jaga di desa.';
  else {
    const next = relics.find(r => !r.taken);
    const where = ['hutan utara', 'gurun timur', 'kuburan barat daya'][next.id];
    hint = `Cari ${next.name} di ${where}  (${taken}/3)`;
  }
  const nearNpc = npcs.some(n => Math.hypot(n.x + 16 - player.x, n.y + 16 - player.y) < 52);
  if (nearNpc) hint = 'Tekan [E] untuk bicara';
}

// ---------- Kamera ----------
const cam = { x: 0, y: 0 };
function updateCam() {
  cam.x = Math.max(0, Math.min(MAPW * TILE - W, player.x - W / 2));
  cam.y = Math.max(0, Math.min(MAPH * TILE - H, player.y - H / 2));
}

// ---------- Gambar ----------
function drawTile(x, y, t) {
  const px = x * TILE - cam.x, py = y * TILE - cam.y;
  const alt = (x * 7 + y * 13) % 3; // variasi warna
  switch (t) {
    case T.GRASS:
      cx.fillStyle = ['#3e8948', '#3a8344', '#428e4c'][alt];
      cx.fillRect(px, py, TILE, TILE);
      break;
    case T.FLOWER:
      cx.fillStyle = '#3e8948'; cx.fillRect(px, py, TILE, TILE);
      cx.fillStyle = ['#ff90c8', '#ffd257', '#ffffff'][alt];
      cx.fillRect(px + 8, py + 10, 4, 4); cx.fillRect(px + 20, py + 20, 4, 4);
      break;
    case T.TREE:
      cx.fillStyle = '#3e8948'; cx.fillRect(px, py, TILE, TILE);
      cx.fillStyle = '#5a3d24'; cx.fillRect(px + 12, py + 18, 8, 12);
      cx.fillStyle = ['#1e5e2e', '#236634', '#1a5629'][alt];
      cx.beginPath(); cx.arc(px + 16, py + 12, 13, 0, Math.PI * 2); cx.fill();
      cx.fillStyle = 'rgba(255,255,255,0.08)';
      cx.beginPath(); cx.arc(px + 12, py + 8, 6, 0, Math.PI * 2); cx.fill();
      break;
    case T.WATER: {
      cx.fillStyle = '#2b6cb8'; cx.fillRect(px, py, TILE, TILE);
      cx.fillStyle = 'rgba(255,255,255,0.15)';
      const wv = Math.sin(time * 2 + x * 1.7 + y * 2.3) * 3;
      cx.fillRect(px + 4, py + 12 + wv, 10, 2);
      cx.fillRect(px + 18, py + 22 - wv, 8, 2);
      break;
    }
    case T.SAND:
      cx.fillStyle = ['#dbc37a', '#d6bd72', '#e0c982'][alt];
      cx.fillRect(px, py, TILE, TILE);
      cx.fillStyle = 'rgba(0,0,0,0.05)'; cx.fillRect(px + 6 + alt * 6, py + 8 + alt * 4, 3, 3);
      break;
    case T.PATH:
      cx.fillStyle = ['#b59a6a', '#b09565', '#ba9f6f'][alt];
      cx.fillRect(px, py, TILE, TILE);
      cx.fillStyle = 'rgba(0,0,0,0.07)'; cx.fillRect(px + 4 + alt * 8, py + 6 + alt * 6, 4, 3);
      break;
    case T.ROCK:
      cx.fillStyle = mget(x, y + 1) === T.SAND || mget(x, y - 1) === T.SAND ? '#dbc37a' : '#3e8948';
      cx.fillRect(px, py, TILE, TILE);
      cx.fillStyle = '#8a8a92';
      cx.beginPath(); cx.arc(px + 16, py + 18, 11, 0, Math.PI * 2); cx.fill();
      cx.fillStyle = '#a5a5ad';
      cx.beginPath(); cx.arc(px + 13, py + 15, 5, 0, Math.PI * 2); cx.fill();
      break;
    case T.SHRINE:
      cx.fillStyle = '#c9c2b2'; cx.fillRect(px, py, TILE, TILE);
      cx.strokeStyle = 'rgba(0,0,0,0.15)'; cx.strokeRect(px + 2, py + 2, TILE - 4, TILE - 4);
      break;
    case T.GRAVE:
      cx.fillStyle = '#35533c'; cx.fillRect(px, py, TILE, TILE);
      cx.fillStyle = '#7d7d86'; cx.fillRect(px + 9, py + 8, 14, 18);
      cx.fillStyle = '#94949c'; cx.fillRect(px + 9, py + 4, 14, 6);
      cx.fillStyle = '#5c5c64'; cx.fillRect(px + 14, py + 12, 4, 8);
      break;
    case T.BRIDGE:
      cx.fillStyle = '#2b6cb8'; cx.fillRect(px, py, TILE, TILE);
      cx.fillStyle = '#8a6238'; cx.fillRect(px, py + 2, TILE, TILE - 4);
      cx.fillStyle = 'rgba(0,0,0,0.15)';
      cx.fillRect(px, py + 10, TILE, 2); cx.fillRect(px, py + 20, TILE, 2);
      break;
  }
}

function drawMap() {
  const x0 = Math.floor(cam.x / TILE), y0 = Math.floor(cam.y / TILE);
  const x1 = Math.ceil((cam.x + W) / TILE), y1 = Math.ceil((cam.y + H) / TILE);
  for (let y = y0; y <= y1; y++)
    for (let x = x0; x <= x1; x++) drawTile(x, y, mget(x, y));
}

function drawShadow(x, y, r) {
  cx.fillStyle = 'rgba(0,0,0,0.25)';
  cx.beginPath(); cx.ellipse(x - cam.x, y - cam.y, r, r * 0.4, 0, 0, Math.PI * 2); cx.fill();
}

function drawPlayer() {
  const px = player.x - cam.x, py = player.y - cam.y;
  const bob = Math.abs(Math.sin(player.walkT)) * ((player.vx || player.vy) ? 2 : 0);
  drawShadow(player.x, player.y + 10, 10);
  if (player.invuln > 0 && Math.floor(time * 12) % 2 === 0) return; // kedip
  // badan
  cx.fillStyle = '#b33939'; // baju merah
  cx.fillRect(px - 8, py - 6 - bob, 16, 14);
  // sarung
  cx.fillStyle = '#4a69bd';
  cx.fillRect(px - 8, py + 4 - bob, 16, 7);
  // kepala
  cx.fillStyle = '#e8b98a';
  cx.fillRect(px - 7, py - 18 - bob, 14, 12);
  // ikat kepala
  cx.fillStyle = '#2c2c54';
  cx.fillRect(px - 7, py - 18 - bob, 14, 4);
  // mata
  cx.fillStyle = '#222';
  if (player.dir !== 'up') {
    const off = player.dir === 'left' ? -3 : player.dir === 'right' ? 3 : 0;
    cx.fillRect(px - 4 + off, py - 12 - bob, 2, 3);
    cx.fillRect(px + 2 + off, py - 12 - bob, 2, 3);
  }
  // pedang saat menyerang
  if (player.attackT > 0) {
    const dirA = player.attackAngle;
    const prog = 1 - player.attackT / 0.18;
    const a = dirA - 1.0 + prog * 2.0;
    cx.save();
    cx.translate(px, py - 4);
    cx.rotate(a);
    cx.fillStyle = '#e8e8f0';
    cx.fillRect(12, -2, 22, 4);
    cx.fillStyle = '#8a6238';
    cx.fillRect(8, -3, 5, 6);
    cx.restore();
    // jejak ayunan
    cx.strokeStyle = 'rgba(255,255,255,0.35)';
    cx.lineWidth = 3;
    cx.beginPath();
    cx.arc(px, py - 4, 30, dirA - 1.0, a);
    cx.stroke();
  }
}

function drawEnemy(e) {
  const px = e.x - cam.x, py = e.y - cam.y;
  const s = e.size;
  const bob = Math.sin(e.bob) * 2;
  if (!e.ghost) drawShadow(e.x, e.y + s / 2, s / 2.2);
  const flash = e.hitT > 0;
  if (e.type === 'slime') {
    cx.fillStyle = flash ? '#fff' : '#7a4fd0';
    cx.beginPath();
    cx.ellipse(px, py + bob / 2, s / 2, s / 2.4 - bob / 2, 0, 0, Math.PI * 2);
    cx.fill();
    cx.fillStyle = flash ? '#fff' : '#9b7ae0';
    cx.beginPath(); cx.ellipse(px - 3, py - 3 + bob / 2, s / 5, s / 6, 0, 0, Math.PI * 2); cx.fill();
    cx.fillStyle = '#fff';
    cx.fillRect(px - 6, py - 3, 4, 4); cx.fillRect(px + 2, py - 3, 4, 4);
    cx.fillStyle = '#222';
    cx.fillRect(px - 5, py - 2, 2, 2); cx.fillRect(px + 3, py - 2, 2, 2);
  } else if (e.type === 'scorp') {
    cx.fillStyle = flash ? '#fff' : '#c97b2a';
    cx.beginPath(); cx.ellipse(px, py, s / 2, s / 2.8, 0, 0, Math.PI * 2); cx.fill();
    // capit
    cx.fillStyle = flash ? '#fff' : '#a5601c';
    cx.beginPath(); cx.arc(px - s / 2, py - 4 + bob, 4, 0, Math.PI * 2); cx.fill();
    cx.beginPath(); cx.arc(px + s / 2, py - 4 - bob, 4, 0, Math.PI * 2); cx.fill();
    // ekor
    cx.strokeStyle = flash ? '#fff' : '#a5601c'; cx.lineWidth = 3;
    cx.beginPath(); cx.moveTo(px, py + s / 3);
    cx.quadraticCurveTo(px + 6, py + s / 2 + 6, px + bob, py + s / 2 + 10);
    cx.stroke();
    cx.fillStyle = '#222';
    cx.fillRect(px - 4, py - 3, 2, 2); cx.fillRect(px + 2, py - 3, 2, 2);
  } else if (e.type === 'ghost') {
    cx.globalAlpha = 0.8;
    cx.fillStyle = flash ? '#fff' : '#9fd8e8';
    cx.beginPath();
    cx.arc(px, py - 4 + bob, s / 2, Math.PI, 0);
    cx.lineTo(px + s / 2, py + s / 2 + bob);
    for (let i = 2; i >= -2; i--)
      cx.lineTo(px + (i * s) / 5, py + s / 2 + bob + (Math.abs(i) % 2 === 0 ? 0 : -4));
    cx.closePath(); cx.fill();
    cx.fillStyle = '#1a3a44';
    cx.fillRect(px - 6, py - 6 + bob, 4, 6); cx.fillRect(px + 2, py - 6 + bob, 4, 6);
    cx.globalAlpha = 1;
  } else if (e.type === 'boss') {
    const rage = e.hp < e.maxHp * 0.35;
    cx.fillStyle = flash ? '#fff' : (rage ? '#5c2b3a' : '#3a2b5c');
    cx.beginPath(); cx.ellipse(px, py + bob, s / 2, s / 1.8, 0, 0, Math.PI * 2); cx.fill();
    // mahkota di kepala boss
    cx.fillStyle = '#ffd700';
    cx.fillRect(px - 12, py - s / 2 - 8 + bob, 24, 6);
    cx.fillRect(px - 12, py - s / 2 - 14 + bob, 5, 6);
    cx.fillRect(px - 2, py - s / 2 - 16 + bob, 5, 8);
    cx.fillRect(px + 7, py - s / 2 - 14 + bob, 5, 6);
    // mata menyala
    cx.fillStyle = rage ? '#ff3030' : '#c060ff';
    cx.fillRect(px - 10, py - 8 + bob, 6, 8); cx.fillRect(px + 4, py - 8 + bob, 6, 8);
    // bar darah boss
    cx.fillStyle = 'rgba(0,0,0,0.5)';
    cx.fillRect(px - 30, py - s - 4, 60, 7);
    cx.fillStyle = rage ? '#ff3030' : '#c060ff';
    cx.fillRect(px - 29, py - s - 3, 58 * (e.hp / e.maxHp), 5);
  }
  // bar darah musuh biasa (kalau terluka)
  if (e.type !== 'boss' && e.hp < e.maxHp) {
    cx.fillStyle = 'rgba(0,0,0,0.5)';
    cx.fillRect(px - 12, py - s - 2, 24, 4);
    cx.fillStyle = '#ff6b6b';
    cx.fillRect(px - 11, py - s - 1, 22 * (e.hp / e.maxHp), 2);
  }
}

function drawNpc(n) {
  const px = n.x + 16 - cam.x, py = n.y + 16 - cam.y;
  drawShadow(n.x + 16, n.y + 26, 9);
  cx.fillStyle = n.color;
  cx.fillRect(px - 7, py - 4, 14, 13);
  cx.fillStyle = '#e8b98a';
  cx.fillRect(px - 6, py - 15, 12, 11);
  cx.fillStyle = '#333';
  cx.fillRect(px - 4, py - 10, 2, 3); cx.fillRect(px + 2, py - 10, 2, 3);
  // tanda seru kalau quest utama belum selesai
  if (n.name === 'Ki Jaga') {
    const taken = relics.filter(r => r.taken).length;
    cx.fillStyle = taken >= 3 ? '#7CFC9A' : '#ffd700';
    const bb = Math.sin(time * 4) * 3;
    cx.font = 'bold 16px monospace';
    cx.textAlign = 'center';
    cx.fillText(taken >= 3 ? '✓' : '!', px, py - 24 + bb);
  }
}

function drawRelic(r) {
  if (r.taken) return;
  const px = r.x - cam.x, py = r.y - cam.y + Math.sin(time * 3) * 4;
  // cahaya
  const g = cx.createRadialGradient(px, py, 2, px, py, 26);
  g.addColorStop(0, 'rgba(255,215,0,0.5)');
  g.addColorStop(1, 'rgba(255,215,0,0)');
  cx.fillStyle = g;
  cx.beginPath(); cx.arc(px, py, 26, 0, Math.PI * 2); cx.fill();
  cx.save();
  cx.translate(px, py);
  cx.rotate(Math.PI / 4);
  cx.fillStyle = '#ffd700';
  cx.fillRect(-7, -7, 14, 14);
  cx.fillStyle = '#fff3b0';
  cx.fillRect(-3, -3, 6, 6);
  cx.restore();
}

function drawDrop(d) {
  const px = d.x - cam.x, py = d.y - cam.y + Math.sin(time * 4 + d.x) * 3;
  const blink = d.t > 20 && Math.floor(time * 6) % 2 === 0;
  if (blink) return;
  cx.fillStyle = '#ff5b7f';
  cx.beginPath();
  cx.arc(px - 4, py - 2, 5, 0, Math.PI * 2);
  cx.arc(px + 4, py - 2, 5, 0, Math.PI * 2);
  cx.fill();
  cx.beginPath();
  cx.moveTo(px - 8, py); cx.lineTo(px, py + 10); cx.lineTo(px + 8, py);
  cx.closePath(); cx.fill();
}

function drawCompass() {
  // panah penunjuk objektif berikutnya
  let target = null;
  const taken = relics.filter(r => r.taken).length;
  if (taken >= 3) target = { x: npcs[0].x + 16, y: npcs[0].y + 16 };
  else { const r = relics.find(r => !r.taken); target = { x: r.x, y: r.y }; }
  const dx = target.x - player.x, dy = target.y - player.y;
  if (Math.hypot(dx, dy) < 200) return;
  const a = Math.atan2(dy, dx);
  const px = player.x - cam.x + Math.cos(a) * 60;
  const py = player.y - cam.y - 4 + Math.sin(a) * 60;
  cx.save();
  cx.translate(px, py);
  cx.rotate(a);
  cx.globalAlpha = 0.7 + Math.sin(time * 4) * 0.2;
  cx.fillStyle = '#ffd700';
  cx.beginPath();
  cx.moveTo(10, 0); cx.lineTo(-6, -6); cx.lineTo(-2, 0); cx.lineTo(-6, 6);
  cx.closePath(); cx.fill();
  cx.restore();
  cx.globalAlpha = 1;
}

function drawHud() {
  // panel kiri atas
  cx.fillStyle = 'rgba(10,14,25,0.75)';
  cx.fillRect(10, 10, 230, 74);
  cx.strokeStyle = 'rgba(255,255,255,0.2)';
  cx.strokeRect(10.5, 10.5, 229, 73);
  // HP
  cx.fillStyle = '#fff'; cx.font = 'bold 12px monospace'; cx.textAlign = 'left';
  cx.fillText(`RAKA  Lv.${player.lvl}`, 20, 28);
  cx.fillStyle = '#3a1020'; cx.fillRect(20, 36, 180, 12);
  cx.fillStyle = player.hp / player.maxHp > 0.3 ? '#ff5b7f' : '#ff3030';
  cx.fillRect(21, 37, 178 * Math.max(0, player.hp / player.maxHp), 10);
  cx.fillStyle = '#fff'; cx.font = '10px monospace';
  cx.fillText(`${Math.ceil(player.hp)}/${player.maxHp}`, 22, 46);
  // XP
  cx.fillStyle = '#2a2a10'; cx.fillRect(20, 54, 180, 8);
  cx.fillStyle = '#ffe066';
  cx.fillRect(21, 55, 178 * Math.min(1, player.xp / xpNeed()), 6);
  cx.fillStyle = '#aaa'; cx.font = '10px monospace';
  cx.fillText(`XP ${player.xp}/${xpNeed()}   ATK ${player.atk}`, 20, 76);
  // slot pusaka kanan atas
  for (let i = 0; i < 3; i++) {
    const x = W - 130 + i * 40, y = 14;
    cx.fillStyle = 'rgba(10,14,25,0.75)';
    cx.fillRect(x, y, 34, 34);
    cx.strokeStyle = relics[i].taken ? '#ffd700' : 'rgba(255,255,255,0.2)';
    cx.strokeRect(x + 0.5, y + 0.5, 33, 33);
    if (relics[i].taken) {
      cx.save();
      cx.translate(x + 17, y + 17);
      cx.rotate(Math.PI / 4);
      cx.fillStyle = '#ffd700'; cx.fillRect(-6, -6, 12, 12);
      cx.fillStyle = '#fff3b0'; cx.fillRect(-2.5, -2.5, 5, 5);
      cx.restore();
    } else {
      cx.fillStyle = 'rgba(255,255,255,0.15)';
      cx.font = 'bold 18px monospace'; cx.textAlign = 'center';
      cx.fillText('?', x + 17, y + 24);
    }
  }
  // petunjuk bawah
  if (hint) {
    cx.font = '13px monospace';
    const tw = cx.measureText(hint).width + 24;
    cx.fillStyle = 'rgba(10,14,25,0.75)';
    cx.fillRect(W / 2 - tw / 2, H - 34, tw, 24);
    cx.fillStyle = '#ffe066';
    cx.textAlign = 'center';
    cx.fillText(hint, W / 2, H - 18);
  }
}

function drawDialog() {
  const d = dialog;
  cx.fillStyle = 'rgba(8,10,20,0.88)';
  cx.fillRect(60, H - 150, W - 120, 110);
  cx.strokeStyle = '#ffd700'; cx.lineWidth = 2;
  cx.strokeRect(60, H - 150, W - 120, 110);
  cx.fillStyle = '#ffd700'; cx.font = 'bold 15px monospace'; cx.textAlign = 'left';
  cx.fillText(d.name, 80, H - 126);
  cx.fillStyle = '#fff'; cx.font = '14px monospace';
  // word-wrap sederhana
  const words = d.lines[d.i].split(' ');
  let line = '', ly = H - 100;
  for (const w2 of words) {
    if (cx.measureText(line + w2).width > W - 190) {
      cx.fillText(line, 80, ly); ly += 20; line = '';
    }
    line += w2 + ' ';
  }
  cx.fillText(line, 80, ly);
  cx.fillStyle = '#aaa'; cx.font = '11px monospace'; cx.textAlign = 'right';
  const more = d.i < d.lines.length - 1;
  cx.fillText(
    '[E]' + (more ? ' lanjut ▸' : ' tutup ✕'),
    W - 80, H - 52
  );
}

function drawVignette() {
  const g = cx.createRadialGradient(W / 2, H / 2, H / 2.4, W / 2, H / 2, H);
  g.addColorStop(0, 'rgba(0,0,0,0)');
  g.addColorStop(1, 'rgba(0,0,0,0.45)');
  cx.fillStyle = g;
  cx.fillRect(0, 0, W, H);
}

function drawTitle() {
  cx.fillStyle = '#0b0f1a';
  cx.fillRect(0, 0, W, H);
  // bintang latar
  const rng = mulberry32(42);
  for (let i = 0; i < 80; i++) {
    const x = rng() * W, y = rng() * H;
    cx.globalAlpha = 0.3 + Math.sin(time * 2 + i) * 0.25;
    cx.fillStyle = '#fff';
    cx.fillRect(x, y, 2, 2);
  }
  cx.globalAlpha = 1;
  cx.textAlign = 'center';
  cx.fillStyle = '#ffd700';
  cx.font = 'bold 52px monospace';
  cx.fillText('PUSAKA NUSANTARA', W / 2, H / 2 - 90);
  cx.fillStyle = '#9fd8e8';
  cx.font = '16px monospace';
  cx.fillText('Tiga pusaka dicuri roh bayangan. Rebut kembali!', W / 2, H / 2 - 50);
  // pusaka berputar
  cx.save();
  cx.translate(W / 2, H / 2 + 10);
  cx.rotate(time);
  cx.fillStyle = '#ffd700'; cx.fillRect(-12, -12, 24, 24);
  cx.fillStyle = '#fff3b0'; cx.fillRect(-5, -5, 10, 10);
  cx.restore();
  cx.fillStyle = Math.floor(time * 2) % 2 === 0 ? '#fff' : '#888';
  cx.font = 'bold 18px monospace';
  cx.fillText('TEKAN [ENTER] ATAU KLIK UNTUK MULAI', W / 2, H / 2 + 80);
  cx.fillStyle = '#888';
  cx.font = '13px monospace';
  cx.fillText('WASD/panah: gerak    KLIK KIRI: serang ke arah kursor', W / 2, H / 2 + 120);
  cx.fillText('SPASI/J: serang ke arah hadap    E: bicara dengan NPC', W / 2, H / 2 + 142);
  if (hasSave()) {
    cx.fillStyle = '#7CFC9A';
    cx.fillText('Save ditemukan — progres dilanjutkan. [Backspace] hapus save.', W / 2, H / 2 + 175);
  }
}

function drawDead(dt) {
  deathT += dt;
  cx.fillStyle = `rgba(60,0,10,${Math.min(0.75, deathT)})`;
  cx.fillRect(0, 0, W, H);
  cx.textAlign = 'center';
  cx.fillStyle = '#ff5b7f';
  cx.font = 'bold 44px monospace';
  cx.fillText('KAU GUGUR...', W / 2, H / 2 - 20);
  if (deathT > 1) {
    cx.fillStyle = '#fff';
    cx.font = '16px monospace';
    cx.fillText(
      'Tekan [ENTER] atau klik untuk bangkit di desa (progres aman)',
      W / 2, H / 2 + 30
    );
    if (confirmPressed) {
      player.hp = player.maxHp;
      player.x = VILLAGE.x * TILE + TILE / 2;
      player.y = (VILLAGE.y + 1) * TILE + TILE / 2;
      player.invuln = 2;
      populate();
      state = STATE.PLAY;
    }
  }
}

function drawWin(dt) {
  winT += dt;
  cx.fillStyle = '#0b0f1a';
  cx.fillRect(0, 0, W, H);
  const rng = mulberry32(99);
  for (let i = 0; i < 120; i++) {
    const x = rng() * W;
    const y = (rng() * H + time * 40 * (0.5 + rng())) % H;
    cx.fillStyle = ['#ffd700', '#ff90c8', '#7CFC9A', '#9fd8e8'][i % 4];
    cx.globalAlpha = 0.8;
    cx.fillRect(x, y, 3, 6);
  }
  cx.globalAlpha = 1;
  cx.textAlign = 'center';
  cx.fillStyle = '#ffd700';
  cx.font = 'bold 46px monospace';
  cx.fillText('PUSAKA KEMBALI!', W / 2, H / 2 - 70);
  cx.fillStyle = '#fff';
  cx.font = '16px monospace';
  cx.fillText('Ketiga pusaka telah pulang ke desa.', W / 2, H / 2 - 25);
  cx.fillText(`Raka sang penjaga mencapai Level ${player.lvl}. Desa pun aman kembali.`, W / 2, H / 2);
  // tiga pusaka
  for (let i = 0; i < 3; i++) {
    cx.save();
    cx.translate(W / 2 - 70 + i * 70, H / 2 + 60 + Math.sin(time * 3 + i) * 6);
    cx.rotate(Math.PI / 4);
    cx.fillStyle = '#ffd700'; cx.fillRect(-11, -11, 22, 22);
    cx.fillStyle = '#fff3b0'; cx.fillRect(-4, -4, 8, 8);
    cx.restore();
  }
  if (winT > 1.5) {
    cx.fillStyle = Math.floor(time * 2) % 2 === 0 ? '#fff' : '#888';
    cx.font = 'bold 15px monospace';
    cx.fillText(
      '[ENTER] atau klik untuk main lagi dari awal',
      W / 2, H / 2 + 130
    );
    if (confirmPressed) {
      clearSave();
      resetGame();
      state = STATE.TITLE;
    }
  }
}

function resetGame() {
  player.hp = 50; player.maxHp = 50; player.atk = 10;
  player.lvl = 1; player.xp = 0; player.invuln = 0;
  setupWorld();
  populate();
}

// ---------- Loop utama ----------
let lastT = 0;
function frame(t) {
  const dt = Math.min(0.05, (t - lastT) / 1000 || 0.016);
  lastT = t;
  time += dt;

  if (state === STATE.TITLE) {
    drawTitle();
    if (confirmPressed) {
      resetGame();
      loadSave();
      populate(); // ulang setelah load agar boss/relic konsisten dengan save
      state = STATE.PLAY;
    }
  } else if (state === STATE.PLAY || state === STATE.DIALOG || state === STATE.DEAD) {
    if (state === STATE.PLAY) updatePlay(dt);
    updateCam();
    drawMap();
    for (const r of relics) drawRelic(r);
    for (const d of drops) drawDrop(d);
    for (const n of npcs) drawNpc(n);
    for (const e of enemies) drawEnemy(e);
    drawPlayer();
    for (const p of particles) {
      cx.globalAlpha = Math.min(1, p.life * 2.5);
      cx.fillStyle = p.color;
      cx.fillRect(p.x - cam.x - 2, p.y - cam.y - 2, 4, 4);
    }
    cx.globalAlpha = 1;
    for (const f of floaters) {
      cx.globalAlpha = Math.min(1, f.life * 2);
      cx.fillStyle = f.color;
      cx.font = 'bold 13px monospace';
      cx.textAlign = 'center';
      cx.fillText(f.text, f.x - cam.x, f.y - cam.y);
    }
    cx.globalAlpha = 1;
    drawVignette();
    if (state === STATE.PLAY) drawCompass();
    drawHud();
    if (state === STATE.DIALOG) {
      drawDialog();
      if (interactPressed || confirmPressed) {
        dialog.i++;
        if (dialog.i >= dialog.lines.length) { dialog = null; state = STATE.PLAY; }
      }
    }
    if (state === STATE.DEAD) drawDead(dt);
  } else if (state === STATE.WIN) {
    drawWin(dt);
  }

  interactPressed = false; attackPressed = false; confirmPressed = false;
  requestAnimationFrame(frame);
}

setupWorld();
requestAnimationFrame(frame);

// hook debug untuk pengujian otomatis (tidak dipakai gameplay)
window.__pusaka = {
  player,
  get enemies() { return enemies; },
  get state() { return state; },
};
