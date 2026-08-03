// ============================================================
//  SWIPEY SWIPEY — format chart (.lvl) + generator level bawaan
// ============================================================
'use strict';

const DIRS = ['left', 'down', 'up', 'right'];
const LVL_FORMAT = 'swipey-lvl';
const LVL_VERSION = 1;

// ------------------------------------------------------------
//  Level bawaan, diturunkan dari pola SONG_SPEC supaya pasti sinkron
// ------------------------------------------------------------
function generateBuiltinChart() {
  const spec = SONG_SPEC;
  const notes = [];
  // urutan arah yang enak dibaca: kiri-bawah-atas-kanan lalu variasinya
  const dirCycles = [
    [0, 1, 2, 3],
    [3, 2, 1, 0],
    [0, 2, 1, 3],
    [1, 3, 0, 2],
  ];
  let seq = 0;

  for (let bar = 0; bar < spec.bars; bar++) {
    const sec = spec.sections.find((s) => bar >= s.from && bar < s.to);
    if (!sec) continue;
    const cyc = dirCycles[bar % dirCycles.length];

    for (const step of sec.steps) {
      const t = (bar * spec.stepsPerBar + step) * spec.stepMs;
      const dir = DIRS[cyc[seq % cyc.length]];
      // lane bergantian, tapi ketukan kuat cenderung menetap sebentar
      const lane = (step === 0 || step === 8) ? (bar % 2) : ((seq >> 1) & 1);
      notes.push({ t: Math.round(t), lane, dir });
      seq++;

      // "jump": dua lane sekaligus di ketukan kuat bagian ramai
      const heavy = sec.name === 'chorus' || sec.name === 'finale';
      if (heavy && step === 0 && bar % 4 === 0) {
        notes.push({ t: Math.round(t), lane: 1 - lane, dir: DIRS[cyc[(seq + 2) % cyc.length]] });
      }
    }

    // pamungkas: 16th beruntun di bar terakhir
    if (bar === spec.bars - 1) {
      for (let s = 0; s < 8; s++) {
        const t = (bar * spec.stepsPerBar + 8 + s) * spec.stepMs;
        notes.push({ t: Math.round(t), lane: s & 1, dir: DIRS[s % 4] });
      }
    }
  }

  notes.sort((a, b) => a.t - b.t);
  return {
    format: LVL_FORMAT,
    version: LVL_VERSION,
    title: spec.title,
    artist: spec.artist,
    difficulty: 'Normal',
    bpm: spec.bpm,
    offset: 0,
    audio: { kind: 'builtin', id: spec.id },
    notes,
  };
}

// ------------------------------------------------------------
//  Validasi & normalisasi file .lvl yang dimuat
// ------------------------------------------------------------
function parseLvl(text) {
  let data;
  try {
    data = JSON.parse(text);
  } catch (e) {
    throw new Error('File .lvl rusak — bukan JSON yang valid.');
  }
  if (!data || typeof data !== 'object') throw new Error('Isi .lvl tidak dikenali.');
  if (data.format && data.format !== LVL_FORMAT) {
    throw new Error(`Format "${data.format}" bukan level Swipey Swipey.`);
  }
  if (!Array.isArray(data.notes)) throw new Error('Level ini tidak punya daftar not.');

  const notes = data.notes
    .map((n) => ({
      t: Number(n.t),
      lane: Number(n.lane) === 1 ? 1 : 0,
      dir: DIRS.includes(n.dir) ? n.dir : 'up',
    }))
    .filter((n) => Number.isFinite(n.t) && n.t >= 0)
    .sort((a, b) => a.t - b.t);

  if (!notes.length) throw new Error('Level ini kosong, tidak ada not sama sekali.');

  return {
    format: LVL_FORMAT,
    version: data.version || LVL_VERSION,
    title: String(data.title || 'Tanpa Judul'),
    artist: String(data.artist || 'Tidak diketahui'),
    difficulty: String(data.difficulty || 'Normal'),
    bpm: Number(data.bpm) > 0 ? Number(data.bpm) : 120,
    offset: Number(data.offset) || 0,
    audio: data.audio && typeof data.audio === 'object' ? data.audio : { kind: 'none' },
    notes,
  };
}

function serializeLvl(chart) {
  return JSON.stringify(
    {
      format: LVL_FORMAT,
      version: LVL_VERSION,
      title: chart.title,
      artist: chart.artist,
      difficulty: chart.difficulty,
      bpm: chart.bpm,
      offset: chart.offset || 0,
      audio: chart.audio,
      notes: chart.notes.map((n) => ({ t: n.t, lane: n.lane, dir: n.dir })),
    },
    null,
    chart.audio && chart.audio.data ? 0 : 1
  );
}

function chartStats(chart) {
  const n = chart.notes.length;
  if (!n) return { count: 0, durationMs: 0, nps: 0 };
  const durationMs = chart.notes[n - 1].t;
  return {
    count: n,
    durationMs,
    nps: durationMs > 0 ? n / (durationMs / 1000) : 0,
  };
}
