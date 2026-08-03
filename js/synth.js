// ============================================================
//  SWIPEY SWIPEY — lagu bawaan (disintesis, tanpa file audio)
//  Dipakai supaya game langsung bisa dimainkan tanpa upload apa pun.
// ============================================================
'use strict';

const SONG_SPEC = {
  id: 'neon-pasar-malam',
  title: 'Neon Pasar Malam',
  artist: 'Swipey Synth',
  bpm: 140,
  bars: 32,
  stepsPerBar: 16,
  // progresi akor per bar (diulang): Am - F - C - G
  roots: [110.00, 87.31, 130.81, 98.00],
  chords: [
    [220.00, 261.63, 329.63], // Am
    [174.61, 220.00, 261.63], // F
    [261.63, 329.63, 392.00], // C
    [196.00, 246.94, 293.66], // G
  ],
  sections: [
    { from: 0,  to: 4,  name: 'intro',   steps: [0, 8] },
    { from: 4,  to: 12, name: 'verse',   steps: [0, 4, 8, 12] },
    { from: 12, to: 16, name: 'build',   steps: [0, 4, 6, 8, 12, 14] },
    { from: 16, to: 24, name: 'chorus',  steps: [0, 2, 4, 6, 8, 10, 12, 14] },
    { from: 24, to: 28, name: 'break',   steps: [0, 6, 12] },
    { from: 28, to: 32, name: 'finale',  steps: [0, 2, 4, 6, 8, 10, 12, 14] },
  ],
};

SONG_SPEC.beatMs = 60000 / SONG_SPEC.bpm;
SONG_SPEC.stepMs = SONG_SPEC.beatMs / 4;
SONG_SPEC.durationMs = SONG_SPEC.bars * SONG_SPEC.stepsPerBar * SONG_SPEC.stepMs;

// ------------------------------------------------------------
// Pemutar: menjadwalkan nada lewat WebAudio, sinkron ke startTime
// ------------------------------------------------------------
function createBuiltinSong(ctx, destination, startTime) {
  const spec = SONG_SPEC;
  const stepSec = spec.stepMs / 1000;
  const totalSteps = spec.bars * spec.stepsPerBar;
  let nextStep = 0;
  let timer = null;
  let stopped = false;

  const bus = ctx.createGain();
  bus.gain.value = 0.9;
  bus.connect(destination);

  function env(node, t, attack, decay, peak) {
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + attack);
    g.gain.exponentialRampToValueAtTime(0.0001, t + attack + decay);
    node.connect(g);
    g.connect(bus);
    return g;
  }

  function kick(t) {
    const o = ctx.createOscillator();
    o.type = 'sine';
    o.frequency.setValueAtTime(150, t);
    o.frequency.exponentialRampToValueAtTime(45, t + 0.11);
    env(o, t, 0.004, 0.22, 0.9);
    o.start(t); o.stop(t + 0.3);
  }

  function snare(t) {
    const len = Math.floor(ctx.sampleRate * 0.2);
    const buf = ctx.createBuffer(1, len, ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
    const s = ctx.createBufferSource();
    s.buffer = buf;
    const bp = ctx.createBiquadFilter();
    bp.type = 'bandpass'; bp.frequency.value = 1900; bp.Q.value = 0.8;
    s.connect(bp);
    env(bp, t, 0.003, 0.15, 0.42);
    s.start(t); s.stop(t + 0.2);
  }

  function hat(t, open) {
    const len = Math.floor(ctx.sampleRate * (open ? 0.13 : 0.045));
    const buf = ctx.createBuffer(1, len, ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / len);
    const s = ctx.createBufferSource();
    s.buffer = buf;
    const hp = ctx.createBiquadFilter();
    hp.type = 'highpass'; hp.frequency.value = 7200;
    s.connect(hp);
    env(hp, t, 0.002, open ? 0.11 : 0.04, 0.14);
    s.start(t); s.stop(t + 0.15);
  }

  function bass(t, freq, dur) {
    const o = ctx.createOscillator();
    o.type = 'sawtooth';
    o.frequency.setValueAtTime(freq, t);
    const lp = ctx.createBiquadFilter();
    lp.type = 'lowpass';
    lp.frequency.setValueAtTime(420, t);
    lp.frequency.exponentialRampToValueAtTime(180, t + dur);
    o.connect(lp);
    env(lp, t, 0.01, dur, 0.32);
    o.start(t); o.stop(t + dur + 0.05);
  }

  function lead(t, freq, dur, vol) {
    const o = ctx.createOscillator();
    o.type = 'square';
    o.frequency.setValueAtTime(freq, t);
    const lp = ctx.createBiquadFilter();
    lp.type = 'lowpass'; lp.frequency.value = 2600;
    o.connect(lp);
    env(lp, t, 0.006, dur, vol);
    o.start(t); o.stop(t + dur + 0.05);
  }

  function pad(t, freqs, dur) {
    for (const f of freqs) {
      const o = ctx.createOscillator();
      o.type = 'triangle';
      o.frequency.setValueAtTime(f / 2, t);
      env(o, t, 0.09, dur, 0.055);
      o.start(t); o.stop(t + dur + 0.1);
    }
  }

  function scheduleStep(i, t) {
    const bar = Math.floor(i / spec.stepsPerBar);
    const step = i % spec.stepsPerBar;
    const chordIx = bar % 4;
    const sec = spec.sections.find((s) => bar >= s.from && bar < s.to) || spec.sections[0];
    const busy = sec.name === 'chorus' || sec.name === 'finale';
    const quiet = sec.name === 'intro' || sec.name === 'break';

    // drum
    if (step === 0 || step === 8) kick(t);
    if (!quiet && (step === 4 || step === 12)) snare(t);
    if (busy && step === 14) kick(t);
    if (!quiet && step % 2 === 0) hat(t, step === 6 || step === 14);

    // pad tiap awal bar
    if (step === 0) pad(t, spec.chords[chordIx], spec.beatMs * 3.6 / 1000);

    // bass
    if (step % 4 === 0) bass(t, spec.roots[chordIx], stepSec * 3.2);
    if (busy && (step === 6 || step === 14)) bass(t, spec.roots[chordIx] * 1.5, stepSec * 1.4);

    // lead arp
    if (!quiet) {
      const arp = spec.chords[chordIx];
      if (step % 2 === 0) {
        const n = arp[(step / 2) % arp.length];
        lead(t, busy ? n * 2 : n, stepSec * 1.5, busy ? 0.14 : 0.09);
      }
    }
  }

  function tick() {
    if (stopped) return;
    const horizon = ctx.currentTime + 0.35;
    while (nextStep < totalSteps && startTime + nextStep * stepSec < horizon) {
      scheduleStep(nextStep, startTime + nextStep * stepSec);
      nextStep++;
    }
    if (nextStep >= totalSteps) { clearInterval(timer); timer = null; }
  }

  tick();
  timer = setInterval(tick, 60);

  return {
    durationMs: spec.durationMs,
    stop() {
      stopped = true;
      if (timer) { clearInterval(timer); timer = null; }
      try {
        bus.gain.cancelScheduledValues(ctx.currentTime);
        bus.gain.setTargetAtTime(0.0001, ctx.currentTime, 0.02);
      } catch (e) { /* abaikan */ }
    },
  };
}
