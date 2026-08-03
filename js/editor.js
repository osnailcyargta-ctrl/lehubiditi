// ============================================================
//  SWIPEY SWIPEY — Studio Chart (Ctrl + Alt + M)
//  Rekam gameplay sendiri → hasilkan file .lvl
// ============================================================
'use strict';

(function () {
  const $ = (id) => document.getElementById(id);
  const el = {
    root: $('editor'), close: $('edClose'),
    audio: $('edAudio'), builtin: $('edBuiltin'), audioInfo: $('edAudioInfo'),
    title: $('edTitle'), artist: $('edArtist'), bpm: $('edBpm'), diff: $('edDiff'),
    offset: $('edOffset'), calib: $('edCalib'), snap: $('edSnap'), calMsg: $('calMsg'),
    rec: $('edRec'), stop: $('edStop'), undo: $('edUndo'), clear: $('edClear'),
    pad: $('recPad'), strip: $('recStrip'),
    time: $('edTime'), count: $('edCount'), nps: $('edNps'), stateLbl: $('edState'),
    test: $('edTest'), save: $('edSave'), load: $('edLoad'), embed: $('edEmbed'),
    saveInfo: $('edSaveInfo'),
  };

  const REC_LEAD_MS = 3000;
  const ARROW_CHAR = { up: '▲', down: '▼', left: '◀', right: '▶' };
  const DIR_COLOR2 = { left: '#c24bff', down: '#22e0ff', up: '#4dff8f', right: '#ff2e88' };

  const S = {
    open: false,
    buffer: null,          // AudioBuffer lagu
    file: null,            // File asli (untuk disematkan)
    useBuiltin: false,
    notes: [],
    recording: false,
    startCtxTime: 0,
    source: null,
    builtinSong: null,
    rafId: 0,
    calib: null,
  };

  // ---------- utilitas ----------
  const A = () => window.Swipey.audio();
  function beatMs() { return 60000 / Math.max(1, parseFloat(el.bpm.value) || 120); }
  function snapGrid() {
    const v = parseInt(el.snap.value, 10);
    return v > 0 ? (beatMs() * 4) / v : 0;
  }
  function offsetMs() { return parseFloat(el.offset.value) || 0; }
  function durationMs() {
    if (S.buffer) return S.buffer.duration * 1000;
    if (S.useBuiltin) return SONG_SPEC.durationMs;
    return 0;
  }
  function recTime() {
    return (A().currentTime - S.startCtxTime) * 1000;
  }
  function fmt(ms) { return (Math.max(0, ms) / 1000).toFixed(2) + 's'; }

  // ---------- buka / tutup ----------
  function open() {
    S.open = true;
    el.root.classList.add('open');
    drawStrip();
  }
  function close() {
    stopRecording();
    stopCalibration();
    S.open = false;
    el.root.classList.remove('open');
  }
  function toggle() { S.open ? close() : open(); }

  el.close.addEventListener('click', close);

  // ---------- pilih lagu ----------
  el.audio.addEventListener('change', async (e) => {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    el.audioInfo.textContent = 'Memproses audio…';
    try {
      const buf = await f.arrayBuffer();
      S.buffer = await A().decodeAudioData(buf.slice(0));
      S.file = f;
      S.useBuiltin = false;
      if (!el.title.value) el.title.value = f.name.replace(/\.[^.]+$/, '');
      el.audioInfo.innerHTML =
        `<b>${f.name}</b> — ${(f.size / 1048576).toFixed(2)} MB, durasi ${fmt(S.buffer.duration * 1000)}. Siap direkam.`;
      el.rec.disabled = false;
      drawStrip();
    } catch (err) {
      el.audioInfo.innerHTML = `<span class="warn">Gagal membaca audio: ${err.message}</span>`;
    }
  });

  el.builtin.addEventListener('click', () => {
    S.buffer = null; S.file = null; S.useBuiltin = true;
    if (!el.title.value) el.title.value = SONG_SPEC.title + ' (chart-ku)';
    if (!el.artist.value) el.artist.value = SONG_SPEC.artist;
    el.bpm.value = SONG_SPEC.bpm;
    el.audioInfo.innerHTML =
      `Memakai lagu bawaan <b>${SONG_SPEC.title}</b> (${SONG_SPEC.bpm} BPM, ${fmt(SONG_SPEC.durationMs)}). Siap direkam.`;
    el.rec.disabled = false;
    drawStrip();
  });

  // ---------- kalibrasi otomatis ----------
  function stopCalibration() {
    if (S.calib) {
      clearInterval(S.calib.timer);
      S.calib = null;
    }
  }

  el.calib.addEventListener('click', () => {
    if (S.recording) return;
    stopCalibration();
    const a = A();
    const period = 500;                       // 120 BPM
    const total = 16;
    const start = a.currentTime + 1.0;
    for (let i = 0; i < total; i++) click(a, start + (i * period) / 1000, i % 4 === 0);
    S.calib = { start, period, total, taps: [], timer: 0 };
    el.calMsg.textContent = 'Dengar metronom, lalu ketuk/swipe mengikuti bunyinya…';
    S.calib.timer = setInterval(() => {
      if (!S.calib) return;
      const elapsed = (a.currentTime - S.calib.start) * 1000;
      if (elapsed > total * period + 600) finishCalibration();
    }, 120);
  });

  function click(a, t, accent) {
    const o = a.createOscillator();
    const g = a.createGain();
    o.type = 'square';
    o.frequency.value = accent ? 1500 : 1000;
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(accent ? 0.16 : 0.1, t + 0.002);
    g.gain.exponentialRampToValueAtTime(0.0001, t + 0.05);
    o.connect(g); g.connect(window.Swipey.masterGain);
    o.start(t); o.stop(t + 0.06);
  }

  function calibTap() {
    if (!S.calib) return false;
    const elapsed = (A().currentTime - S.calib.start) * 1000;
    if (elapsed < -100) return true;
    const nearest = Math.round(elapsed / S.calib.period) * S.calib.period;
    const delta = elapsed - nearest;
    if (Math.abs(delta) < 260) S.calib.taps.push(delta);
    el.calMsg.textContent = `Terekam ${S.calib.taps.length} ketukan…`;
    return true;
  }

  function finishCalibration() {
    if (!S.calib) return;
    const taps = S.calib.taps.slice().sort((a, b) => a - b);
    stopCalibration();
    if (taps.length < 4) {
      el.calMsg.innerHTML = '<span class="warn">Ketukan terlalu sedikit — coba lagi.</span>';
      return;
    }
    const mid = taps.slice(Math.floor(taps.length * 0.15), Math.ceil(taps.length * 0.85));
    const mean = mid.reduce((a, b) => a + b, 0) / mid.length;
    const off = Math.round(Math.max(-200, Math.min(500, mean)));
    el.offset.value = off;
    el.calMsg.textContent =
      `Selesai — rata-rata kamu ${mean >= 0 ? 'telat' : 'cepat'} ${Math.abs(mean).toFixed(0)} ms. Offset diset ke ${off} ms.`;
  }

  // ---------- perekaman ----------
  function startRecording() {
    if (!S.buffer && !S.useBuiltin) return;
    stopCalibration();
    const a = A();
    S.notes = [];
    S.recording = true;
    S.startCtxTime = a.currentTime + REC_LEAD_MS / 1000;

    if (S.buffer) {
      const src = a.createBufferSource();
      src.buffer = S.buffer;
      src.connect(window.Swipey.masterGain);
      src.start(S.startCtxTime);
      S.source = src;
    } else {
      S.builtinSong = createBuiltinSong(a, window.Swipey.masterGain, S.startCtxTime);
    }

    el.rec.disabled = true;
    el.stop.disabled = false;
    el.stateLbl.textContent = 'Bersiap…';
    tickRecording();
  }

  function stopRecording() {
    if (!S.recording) return;
    S.recording = false;
    if (S.source) { try { S.source.stop(); } catch (e) {} S.source = null; }
    if (S.builtinSong) { S.builtinSong.stop(); S.builtinSong = null; }
    cancelAnimationFrame(S.rafId);
    el.rec.disabled = false;
    el.stop.disabled = true;
    el.stateLbl.textContent = 'Selesai';
    S.notes.sort((a, b) => a.t - b.t);
    updateStats();
    drawStrip();
  }

  function tickRecording() {
    if (!S.recording) return;
    const t = recTime();
    if (t < 0) el.stateLbl.textContent = 'Mulai dalam ' + Math.ceil(-t / 1000);
    else el.stateLbl.textContent = '● MEREKAM';
    el.time.textContent = fmt(t);
    updateStats();
    drawStrip(t);
    if (t > durationMs() + 300) { stopRecording(); return; }
    S.rafId = requestAnimationFrame(tickRecording);
  }

  function recordNote(lane, dir) {
    if (!S.recording) return;
    const raw = recTime();
    if (raw < 0) return;
    // Inti kalibrasi: waktu not = waktu swipe − offset, lalu (opsional) dirapikan ke grid.
    let t = raw - offsetMs();
    const grid = snapGrid();
    if (grid > 0) t = Math.round(t / grid) * grid;
    t = Math.max(0, Math.round(t));
    // cegah dobel di lane & waktu yang sama
    if (S.notes.some((n) => n.lane === lane && Math.abs(n.t - t) < 12 && n.dir === dir)) return;
    S.notes.push({ t, lane, dir });
    updateStats();
  }

  function updateStats() {
    el.count.textContent = String(S.notes.length);
    const dur = S.notes.length ? Math.max(...S.notes.map((n) => n.t)) : 0;
    el.nps.textContent = dur > 0 ? (S.notes.length / (dur / 1000)).toFixed(1) : '0.0';
  }

  el.rec.addEventListener('click', startRecording);
  el.stop.addEventListener('click', stopRecording);
  el.undo.addEventListener('click', () => {
    if (!S.notes.length) return;
    S.notes.sort((a, b) => a.t - b.t);
    S.notes.pop();
    updateStats(); drawStrip();
  });
  el.clear.addEventListener('click', () => {
    if (!S.notes.length) return;
    S.notes = [];
    updateStats(); drawStrip();
  });

  // ---------- input pad (swipe / drag) ----------
  const pads = Array.from(el.pad.querySelectorAll('.pad'));
  const ptr = new Map();
  const MIN = 24, COOL = 70;

  pads.forEach((pad) => {
    const lane = parseInt(pad.dataset.lane, 10);
    pad.addEventListener('pointerdown', (e) => {
      try { pad.setPointerCapture(e.pointerId); } catch (err) { /* pointer sintetis */ }
      ptr.set(e.pointerId, { x: e.clientX, y: e.clientY, lane, last: 0 });
      if (calibTap()) return;
    });
    pad.addEventListener('pointermove', (e) => {
      const p = ptr.get(e.pointerId);
      if (!p) return;
      const dx = e.clientX - p.x, dy = e.clientY - p.y;
      if (Math.hypot(dx, dy) < MIN) return;
      const now = performance.now();
      if (now - p.last < COOL) { p.x = e.clientX; p.y = e.clientY; return; }
      const dir = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up');
      flash(pad, dir);
      recordNote(p.lane, dir);
      p.last = now; p.x = e.clientX; p.y = e.clientY;
    });
    const end = (e) => ptr.delete(e.pointerId);
    pad.addEventListener('pointerup', end);
    pad.addEventListener('pointercancel', end);
  });

  function flash(pad, dir) {
    const a = pad.querySelector('.arrow');
    a.textContent = ARROW_CHAR[dir];
    a.style.color = DIR_COLOR2[dir];
    a.style.transition = 'none';
    a.style.opacity = '1';
    a.style.transform = 'scale(1)';
    pad.style.borderColor = DIR_COLOR2[dir];
    pad.classList.add('lit');
    requestAnimationFrame(() => {
      a.style.transition = 'opacity .28s, transform .28s';
      a.style.opacity = '0';
      a.style.transform = 'scale(1.5)';
      setTimeout(() => { pad.classList.remove('lit'); pad.style.borderColor = ''; }, 140);
    });
  }

  const EDKEYS = {
    w: [0, 'up'], s: [0, 'down'], a: [0, 'left'], d: [0, 'right'],
    arrowup: [1, 'up'], arrowdown: [1, 'down'], arrowleft: [1, 'left'], arrowright: [1, 'right'],
  };
  window.addEventListener('keydown', (e) => {
    if (!S.open) return;
    const tag = (e.target && e.target.tagName) || '';
    if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') return;
    const k = e.key.toLowerCase();
    if (k === 'escape') { e.preventDefault(); close(); return; }
    if (calibTap()) { e.preventDefault(); return; }
    const m = EDKEYS[k];
    if (!m) return;
    e.preventDefault();
    flash(pads[m[0]], m[1]);
    recordNote(m[0], m[1]);
  });

  // ---------- timeline ----------
  function drawStrip(playhead) {
    const c = el.strip;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = c.clientWidth || 800, h = c.clientHeight || 66;
    if (c.width !== Math.round(w * dpr)) { c.width = Math.round(w * dpr); c.height = Math.round(h * dpr); }
    const g = c.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    g.clearRect(0, 0, w, h);

    const dur = Math.max(1000, durationMs());
    g.fillStyle = '#0a0812';
    g.fillRect(0, 0, w, h);

    // garis bar berdasarkan BPM
    const bar = beatMs() * 4;
    if (bar > 40) {
      g.strokeStyle = '#ffffff10'; g.lineWidth = 1;
      for (let t = 0; t < dur; t += bar) {
        const x = (t / dur) * w;
        g.beginPath(); g.moveTo(x, 0); g.lineTo(x, h); g.stroke();
      }
    }
    g.strokeStyle = '#ffffff1a';
    g.beginPath(); g.moveTo(0, h / 2); g.lineTo(w, h / 2); g.stroke();

    for (const n of S.notes) {
      const x = (n.t / dur) * w;
      const y0 = n.lane === 0 ? 6 : h / 2 + 4;
      g.fillStyle = DIR_COLOR2[n.dir];
      g.fillRect(x - 1, y0, 2.5, h / 2 - 10);
    }

    if (playhead != null && playhead >= 0) {
      const x = (playhead / dur) * w;
      g.strokeStyle = '#fff'; g.lineWidth = 1.5;
      g.beginPath(); g.moveTo(x, 0); g.lineTo(x, h); g.stroke();
    }

    g.fillStyle = '#6f6890';
    g.font = '10px system-ui, sans-serif';
    g.fillText('lane kiri', 6, 13);
    g.fillText('lane kanan', 6, h / 2 + 15);
  }
  window.addEventListener('resize', () => { if (S.open) drawStrip(); });

  // ---------- bangun chart ----------
  function buildChart(audioSpec) {
    const notes = S.notes.slice().sort((a, b) => a.t - b.t);
    return {
      format: 'swipey-lvl',
      version: 1,
      title: el.title.value.trim() || 'Level Tanpa Judul',
      artist: el.artist.value.trim() || 'Tidak diketahui',
      difficulty: el.diff.value,
      bpm: parseFloat(el.bpm.value) || 120,
      offset: 0,
      audio: audioSpec || currentAudioSpec(),
      notes,
    };
  }

  function currentAudioSpec() {
    if (S.useBuiltin) return { kind: 'builtin', id: SONG_SPEC.id };
    if (S.file) return { kind: 'file', name: S.file.name };
    return { kind: 'none' };
  }

  // ---------- tes level ----------
  el.test.addEventListener('click', () => {
    if (!S.notes.length) { el.saveInfo.innerHTML = '<span class="warn">Belum ada not untuk dites.</span>'; return; }
    stopRecording();
    const chart = buildChart();
    window.Swipey.setReturnToEditor(true);
    close();
    window.Swipey.startChart(chart, { buffer: S.buffer });
  });

  // ---------- simpan .lvl ----------
  el.save.addEventListener('click', async () => {
    if (!S.notes.length) { el.saveInfo.innerHTML = '<span class="warn">Belum ada not untuk disimpan.</span>'; return; }
    stopRecording();
    let spec = currentAudioSpec();

    if (el.embed.checked && S.file) {
      el.saveInfo.textContent = 'Menyematkan audio…';
      try {
        const dataUri = await fileToDataUri(S.file);
        spec = { kind: 'embedded', name: S.file.name, data: dataUri };
      } catch (e) {
        el.saveInfo.innerHTML = '<span class="warn">Gagal menyematkan audio, disimpan tanpa audio.</span>';
      }
    } else if (el.embed.checked && S.useBuiltin) {
      el.saveInfo.innerHTML = 'Lagu bawaan tidak perlu disematkan — sudah ada di dalam game.';
    }

    const chart = buildChart(spec);
    const text = serializeLvl(chart);
    const name = chart.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'level';
    downloadText(name + '.lvl', text);

    const kb = (new Blob([text]).size / 1024);
    el.saveInfo.innerHTML =
      `Tersimpan sebagai <b>${name}.lvl</b> (${kb > 1024 ? (kb / 1024).toFixed(2) + ' MB' : kb.toFixed(1) + ' KB'}, ` +
      `${chart.notes.length} not). Taruh di folder <b>levels/</b> lalu daftarkan namanya di ` +
      `<b>levels/index.json</b> agar muncul di menu — atau tarik filenya langsung ke jendela game.`;
  });

  function fileToDataUri(file) {
    return new Promise((res, rej) => {
      const r = new FileReader();
      r.onload = () => res(r.result);
      r.onerror = () => rej(new Error('baca gagal'));
      r.readAsDataURL(file);
    });
  }

  function downloadText(filename, text) {
    const blob = new Blob([text], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = filename;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => { URL.revokeObjectURL(url); a.remove(); }, 1000);
  }

  // ---------- muat .lvl ----------
  el.load.addEventListener('change', async (e) => {
    const f = e.target.files && e.target.files[0];
    if (!f) return;
    try {
      const chart = parseLvl(await f.text());
      S.notes = chart.notes.map((n) => ({ ...n }));
      el.title.value = chart.title;
      el.artist.value = chart.artist;
      el.bpm.value = chart.bpm;
      el.diff.value = ['Mudah', 'Normal', 'Susah', 'Gila'].includes(chart.difficulty) ? chart.difficulty : 'Normal';

      if (chart.audio && chart.audio.kind === 'builtin') {
        S.useBuiltin = true; S.buffer = null; S.file = null;
        el.audioInfo.innerHTML = `Level ini memakai lagu bawaan <b>${SONG_SPEC.title}</b>.`;
        el.rec.disabled = false;
      } else if (chart.audio && chart.audio.data) {
        const res = await fetch(chart.audio.data);
        S.buffer = await A().decodeAudioData(await res.arrayBuffer());
        S.useBuiltin = false;
        el.audioInfo.innerHTML = `Audio tersemat <b>${chart.audio.name || 'lagu'}</b> dimuat.`;
        el.rec.disabled = false;
      } else {
        el.audioInfo.innerHTML =
          `<span class="warn">Level ini butuh file audio terpisah${chart.audio && chart.audio.name ? ` (<b>${chart.audio.name}</b>)` : ''}. Pilih lagunya untuk merekam ulang atau menguji.</span>`;
      }
      updateStats(); drawStrip();
      el.saveInfo.innerHTML = `Level <b>${chart.title}</b> dimuat — ${chart.notes.length} not.`;
    } catch (err) {
      el.saveInfo.innerHTML = `<span class="warn">Gagal memuat: ${err.message}</span>`;
    }
  });

  // ---------- API ----------
  window.SwipeyEditor = {
    open, close, toggle,
    isOpen: () => S.open,
    _state: S,
    _recordNote: recordNote,
    _buildChart: buildChart,
  };
})();
