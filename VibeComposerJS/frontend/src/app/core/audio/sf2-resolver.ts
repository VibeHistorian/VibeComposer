import type { SmplrPreset, SmplrRegion } from 'smplr';

type Generators = Map<number, number>;
interface Chunk { offset: number; size: number; }
export interface Sf2Region extends SmplrRegion {
  sampleId: number;
  sampleStart: number;
  sampleEnd: number;
  sampleRate: number;
  pan: number;
  ampDelay: number;
  ampHold: number;
  ampDecay: number;
  ampSustain: number;
  scaleTuning: number;
  loopUntilRelease: boolean;
}
export interface Sf2Preset { name: string; bank: number; program: number; regions: Sf2Region[]; }

/** SF2 Hydra resolver. Presets select instruments, which select samples; neither
 * instrument order nor sample names carry MIDI program identity. No Angular/DOM. */
export class Sf2Bank {
  readonly presets: Sf2Preset[] = [];
  private readonly view: DataView;
  private readonly chunks = new Map<string, Chunk>();
  constructor(data: ArrayBuffer) {
    this.view = new DataView(data);
    const tag = (p: number) => String.fromCharCode(...new Uint8Array(data, p, 4));
    if (data.byteLength < 12 || tag(0) !== 'RIFF' || tag(8) !== 'sfbk') throw new Error('Invalid SF2 bank.');
    const scan = (begin: number, end: number) => {
      for (let p = begin; p + 8 <= end;) {
        const size = this.view.getUint32(p + 4, true), offset = p + 8;
        if (offset + size > end) throw new Error('Truncated SF2 chunk.');
        if (tag(p) === 'LIST' && size >= 4) scan(offset + 4, offset + size);
        else this.chunks.set(tag(p), { offset, size });
        p = offset + size + size % 2;
      }
    };
    const riffEnd = this.view.getUint32(4, true) + 8;
    if (riffEnd > data.byteLength) throw new Error('Truncated SF2 bank.');
    scan(12, riffEnd);
    for (const [name, width] of [['phdr', 38], ['pbag', 4], ['pgen', 4], ['inst', 22], ['ibag', 4], ['igen', 4], ['shdr', 46]] as const) {
      const chunk = this.chunk(name);
      if (chunk.size < width || chunk.size % width) throw new Error(`Invalid SF2 ${name} table.`);
    }
    this.chunk('smpl');
    const count = this.chunk('phdr').size / 38 - 1;
    for (let i = 0; i < count; i++) {
      const p = this.chunk('phdr').offset + i * 38;
      const preset: Sf2Preset = { name: this.name(p), program: this.u16(p + 20), bank: this.u16(p + 22), regions: [] };
      for (const pg of this.zones('phdr', 38, 24, 'pbag', 'pgen', i, 41)) {
        const instrument = pg.get(41)!;
        if (instrument >= this.chunk('inst').size / 22 - 1) throw new Error('Invalid SF2 instrument reference.');
        for (const ig of this.zones('inst', 22, 20, 'ibag', 'igen', instrument, 53)) {
          const region = this.region(pg, ig);
          if (region) preset.regions.push(region);
        }
      }
      this.presets.push(preset);
    }
  }
  resolve(bank: number, program: number): Sf2Preset {
    const preset = this.presets.find(p => p.bank === bank && p.program === program);
    if (!preset) throw new Error(`SoundFont has no preset ${bank}:${program}.`);
    return preset;
  }
  /** Stable IDs avoid collisions between samples with identical display names. */
  pcm(region: Sf2Region): Float32Array<ArrayBuffer> {
    const pcm = new Float32Array(region.sampleEnd - region.sampleStart);
    const start = this.chunk('smpl').offset + region.sampleStart * 2;
    for (let i = 0; i < pcm.length; i++) pcm[i] = this.view.getInt16(start + i * 2, true) / 32768;
    return pcm;
  }
  toSmplr(preset: Sf2Preset): SmplrPreset {
    return { samples: { baseUrl: '', formats: [] }, groups: [{ regions: preset.regions }] };
  }
  private chunk(name: string): Chunk {
    const value = this.chunks.get(name);
    if (!value) throw new Error(`Missing SF2 ${name} chunk.`);
    return value;
  }
  private u16(p: number): number { return this.view.getUint16(p, true); }
  private name(p: number): string {
    return String.fromCharCode(...new Uint8Array(this.view.buffer, p, 20)).replace(/\0.*$/, '');
  }
  private zones(header: string, width: number, bagField: number, bag: string, gen: string, index: number, terminal: number): Generators[] {
    const h = this.chunk(header).offset + index * width;
    const begin = this.u16(h + bagField), end = this.u16(h + width + bagField);
    const bags = this.chunk(bag), gens = this.chunk(gen);
    if (end < begin || (end + 1) * 4 > bags.size) throw new Error('Invalid SF2 zone indices.');
    const result: Generators[] = [];
    let global: Generators = new Map();
    for (let i = begin; i < end; i++) {
      const first = this.u16(bags.offset + i * 4), last = this.u16(bags.offset + (i + 1) * 4);
      if (last < first || last * 4 > gens.size) throw new Error('Invalid SF2 generator indices.');
      const zone: Generators = new Map();
      for (let j = first; j < last; j++) {
        const p = gens.offset + j * 4, op = this.u16(p);
        zone.set(op, [41, 43, 44, 53].includes(op) ? this.u16(p + 2) : this.view.getInt16(p + 2, true));
      }
      if (!zone.has(terminal)) { if (i === begin) global = zone; continue; }
      result.push(new Map([...global, ...zone]));
    }
    return result;
  }
  private region(pg: Generators, ig: Generators): Sf2Region | undefined {
    const id = ig.get(53)!;
    const headers = this.chunk('shdr');
    if (id >= headers.size / 46 - 1) throw new Error('Invalid SF2 sample reference.');
    const h = headers.offset + id * 46;
    const value = (op: number, fallback = 0) => (ig.get(op) ?? fallback) + (pg.get(op) ?? 0);
    const range = (op: number): [number, number] => {
      const a = pg.get(op) ?? 0x7f00, b = ig.get(op) ?? 0x7f00;
      return [Math.max(a & 255, b & 255), Math.min(a >> 8, b >> 8)];
    };
    const keyRange = range(43), velRange = range(44);
    if (keyRange[0] > keyRange[1] || velRange[0] > velRange[1]) return undefined;
    const start = this.view.getUint32(h + 20, true) + value(0) + value(4) * 32768;
    const end = this.view.getUint32(h + 24, true) + value(1) + value(12) * 32768;
    const rate = this.view.getUint32(h + 36, true);
    if (start < 0 || end <= start || end * 2 > this.chunk('smpl').size || !rate) throw new Error('Invalid SF2 sample bounds.');
    const time = (op: number) => Math.min(40, Math.max(0, 2 ** (value(op, -12000) / 1200)));
    const root = ig.get(58) ?? this.view.getUint8(h + 40);
    const mode = (ig.get(54) ?? 0) & 3;
    const loopStart = this.view.getUint32(h + 28, true) + value(2) + value(45) * 32768 - start;
    const loopEnd = this.view.getUint32(h + 32, true) + value(3) + value(50) * 32768 - start;
    const loop = (mode === 1 || mode === 3) && loopStart >= 0 && loopEnd > loopStart && loopEnd <= end - start;
    const exclusive = ig.get(57) ?? 0;
    return {
      sample: `${id}:${start}:${end}`, sampleId: id, sampleStart: start, sampleEnd: end, sampleRate: rate,
      keyRange, velRange, pitch: root >= 0 && root <= 127 ? root : 60,
      tune: value(51), detune: value(52) + this.view.getInt8(h + 41), scaleTuning: ig.get(56) ?? 100,
      volume: -Math.max(0, value(48)) / 10, pan: Math.max(-1, Math.min(1, value(17) / 500)),
      ampDelay: time(33), ampAttack: time(34), ampHold: time(35), ampDecay: time(36),
      ampSustain: 10 ** (-Math.max(0, value(37)) / 200), ampRelease: time(38),
      loop, loopStart: loopStart / rate, loopEnd: loopEnd / rate, loopUntilRelease: mode === 3,
      lpfCutoffHz: Math.min(20000, 8.176 * 2 ** (value(8, 13500) / 1200)),
      ...(exclusive ? { group: exclusive, offBy: exclusive } : {}),
    };
  }
}
