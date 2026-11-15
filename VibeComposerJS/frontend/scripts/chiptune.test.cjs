const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { CHIPTUNE_PATCHES } = loadMusic('../audio/chiptune-patches.ts');
const { CHIPTUNE_LOUDNESS_PROFILE } = loadMusic('../audio/chiptune-loudness.ts');

test('chiptune covers GM and Standard drums with at most four operators and independent bundled gains', () => {
  assert.equal(CHIPTUNE_PATCHES.melodic.length, 128);
  assert.equal(CHIPTUNE_PATCHES.drums.length, 47);
  for (const patch of [...CHIPTUNE_PATCHES.melodic, ...CHIPTUNE_PATCHES.drums]) {
    assert.ok(patch.length >= 1 && patch.length <= 4);
    for (const op of patch) {
      assert.ok(op.v > 0 && Number.isFinite(op.v));
      if (op.w.startsWith('w')) assert.ok(CHIPTUNE_PATCHES.waves[op.w]);
    }
  }
  assert.ok([...CHIPTUNE_LOUDNESS_PROFILE.melodic, ...CHIPTUNE_LOUDNESS_PROFILE.drums]
    .every(g => Number.isFinite(g) && g > 0 && g <= 10 ** (12 / 20)));
  assert.notEqual(CHIPTUNE_LOUDNESS_PROFILE.drums[82], 1, 'shaker fallback has a measured gain');
});

test('derived chip patches preserve reference tuning and more than 98% of loop energy', () => {
  const { Sf2Bank } = loadMusic('../audio/sf2-resolver.ts');
  const bytes = fs.readFileSync(path.join(__dirname, '../../chiptune_soundfont_4.0.sf2'));
  const bank = new Sf2Bank(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
  const derived = bank.presets.filter(p => p.bank === 0 && p.regions.length === 1 && p.regions[0].sampleId < 19);
  for (const preset of derived) {
    const region = preset.regions[0], patch = CHIPTUNE_PATCHES.melodic[preset.program][0];
    assert.ok(Math.abs(1200 * Math.log2(patch.t) - (region.tune * 100 + region.detune)) < 1e-8,
      'preserves source tuning for GM ' + preset.program);
  }
  assert.ok(Math.abs(1200 * Math.log2(CHIPTUNE_PATCHES.melodic[46][0].t) - 7) < 1e-8, 'Harp is +7 cents');
  for (let id = 0; id < 19; id++) {
    const region = bank.presets.flatMap(p => p.regions).find(r => r.sampleId === id);
    const pcm = bank.pcm(region);
    const mean = pcm.reduce((sum, x) => sum + x, 0) / pcm.length;
    const energy = pcm.reduce((sum, x) => sum + (x - mean) ** 2, 0) / pcm.length;
    const wave = CHIPTUNE_PATCHES.waves['wChip' + id];
    // Measure actual residual, checking phase and coefficients, not just magnitude.
    let residual = 0, baselineResidual = 0;
    for (let i = 0; i < pcm.length; i += 7) {
      let synthesized = 0, baseline = 0;
      for (let k = 1; k < wave.real.length; k++) {
        const phase = 2 * Math.PI * 55 * k * i / region.sampleRate;
        const partial = wave.real[k] * Math.cos(phase) + wave.imag[k] * Math.sin(phase);
        synthesized += partial;
        if (k <= 64) baseline += partial;
      }
      residual += (pcm[i] - mean - synthesized) ** 2;
      baselineResidual += (pcm[i] - mean - baseline) ** 2;
    }
    assert.ok(residual / Math.ceil(pcm.length / 7) / energy < .02, 'wave ' + id);
    if (id === 12) assert.ok(residual < baselineResidual / 2, 'extra square-wave harmonics reduce reconstruction error by more than half');
  }
});
