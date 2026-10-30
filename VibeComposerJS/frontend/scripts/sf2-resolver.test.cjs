const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { loadMusic } = require('./music-loader.cjs');
const { Sf2Bank } = loadMusic('../audio/sf2-resolver.ts');
const file = path.join(__dirname, '../../Super_Nintendo_Unofficial_update.sf2');
const bytes = fs.readFileSync(file);
const data = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
test('SNES bank resolves every GM program and the bank-128 Standard kit', () => {
  const bank = new Sf2Bank(data);
  for (let program = 0; program < 128; program++) {
    const preset = bank.resolve(0, program);
    assert.ok(preset.name);
    assert.ok(preset.regions.length, 'program ' + program);
    for (const region of preset.regions) {
      assert.ok(region.keyRange[0] <= region.keyRange[1]);
      assert.ok(region.velRange[0] <= region.velRange[1]);
      assert.ok(region.pitch >= 0 && region.pitch <= 127);
      assert.ok(region.sampleEnd > region.sampleStart);
      assert.equal(bank.pcm(region).length, region.sampleEnd - region.sampleStart);
    }
  }
  const drums = bank.resolve(128, 0);
  assert.equal(drums.name, 'Standard');
  for (let note = 35; note <= 81; note++) assert.ok(drums.regions.some(r => note >= r.keyRange[0] && note <= r.keyRange[1]), 'drum ' + note);
  assert.throws(() => bank.resolve(7, 0), /no preset/);
});
test('malformed SF2 banks are rejected before reading outside their tables', () => {
  assert.throws(() => new Sf2Bank(new ArrayBuffer(4)), /Invalid/);
  assert.throws(() => new Sf2Bank(data.slice(0, 100)), /Truncated/);
});

// A deliberately tiny bank distinguishes preset zones from instrument zones,
// and sample-header pitch from a global overridingRootKey generator.
function syntheticBank() {
  const chunk = (name, data) => {
    const header = Buffer.alloc(8); header.write(name); header.writeUInt32LE(data.length, 4);
    return Buffer.concat([header, data, Buffer.alloc(data.length % 2)]);
  };
  const gens = entries => Buffer.concat(entries.map(([op, value]) => {
    const b = Buffer.alloc(4); b.writeUInt16LE(op); b.writeUInt16LE(value & 65535, 2); return b;
  }));
  const bags = values => Buffer.concat(values.map(value => { const b = Buffer.alloc(4); b.writeUInt16LE(value); return b; }));
  const phdr = Buffer.alloc(76); phdr.write('Synthetic'); phdr.writeUInt16LE(7, 20); phdr.writeUInt16LE(2, 24 + 38);
  const inst = Buffer.alloc(44); inst.write('Not a GM name'); inst.writeUInt16LE(2, 20 + 22);
  const shdr = Buffer.alloc(92); shdr.write('duplicate names safe'); shdr.writeUInt32LE(128, 24);
  shdr.writeUInt32LE(16, 28); shdr.writeUInt32LE(96, 32); shdr.writeUInt32LE(8000, 36);
  shdr[40] = 0; shdr.writeUInt16LE(1, 44);
  const pdta = Buffer.concat([
    chunk('phdr', phdr), chunk('pbag', bags([0, 1, 3])),
    chunk('pgen', gens([[48, 50], [43, 40 | (80 << 8)], [41, 0]])),
    chunk('inst', inst), chunk('ibag', bags([0, 2, 6])),
    chunk('igen', gens([[48, 100], [58, 60], [48, 200], [43, 50 | (90 << 8)], [54, 1], [53, 0]])),
    chunk('shdr', shdr),
  ]);
  const body = Buffer.concat([Buffer.from('sfbk'), chunk('LIST', Buffer.concat([Buffer.from('sdta'), chunk('smpl', Buffer.alloc(256))])),
    chunk('LIST', Buffer.concat([Buffer.from('pdta'), pdta]))]);
  const riff = chunk('RIFF', body);
  return riff.buffer.slice(riff.byteOffset, riff.byteOffset + riff.byteLength);
}
test('preset and instrument globals resolve with local overrides, additive offsets and range intersection', () => {
  const bank = new Sf2Bank(syntheticBank());
  const preset = bank.resolve(0, 7);
  assert.equal(preset.name, 'Synthetic');
  assert.throws(() => bank.resolve(0, 0), /no preset/);
  const [region] = preset.regions;
  assert.equal(preset.regions.length, 1);
  assert.deepEqual(region.keyRange, [50, 80]);
  assert.equal(region.pitch, 60, 'global root override replaces sample-header root');
  assert.equal(region.volume, -25, 'local instrument attenuation plus preset global offset');
  assert.equal(region.loop, true);
  assert.equal(region.loopStart, 16 / 8000);
  assert.equal(region.loopEnd, 96 / 8000);
});
