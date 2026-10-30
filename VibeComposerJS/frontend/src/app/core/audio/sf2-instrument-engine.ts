import { Instrument, Scheduler, type Smplr, type SmplrPreset } from 'smplr';
import type { InstrumentEngine, InstrumentVoice } from './instrument-engine';
import type { Sf2Bank, Sf2Preset } from './sf2-resolver';

const Sf2Instrument = Instrument<{ preset: SmplrPreset; buffers: Map<string, AudioBuffer> }>(
  (_context, options, sampler) => sampler.loadInstrument(options.preset, options.buffers));
/** Experimental GM bank engine. Preset identity is bank/program, never instrument order. */
export class Sf2InstrumentEngine implements InstrumentEngine {
  private readonly buffers = new Map<string, AudioBuffer>();
  private readonly routes = new Map<AudioNode, Map<string, { sampler: Smplr; voices: number }>>();
  private nextId = 0;
  private disposed = false;
  private readonly pending = new Set<Promise<void>>();
  private failure: unknown;
  constructor(readonly context: BaseAudioContext, private readonly bank: Sf2Bank) {}
  async flush(): Promise<void> {
    await Promise.all(this.pending);
    if (this.failure) throw this.failure;
  }
  async prepare(): Promise<void> {}
  private decode(preset: Sf2Preset): Map<string, AudioBuffer> {
    const result = new Map<string, AudioBuffer>();
    for (const region of preset.regions) {
      let buffer = this.buffers.get(region.sample);
      if (!buffer) {
        const pcm = this.bank.pcm(region);
        buffer = this.context.createBuffer(1, pcm.length, region.sampleRate);
        buffer.copyToChannel(pcm, 0);
        this.buffers.set(region.sample, buffer);
      }
      result.set(region.sample, buffer);
    }
    return result;
  }
  schedule(destination: AudioNode, program: number, percussion: boolean, midi: number,
    velocity: number, start: number, duration: number): InstrumentVoice[] {
    if (this.disposed || !this.bank) throw new Error('Soundfont engine is not ready.');
    if (velocity <= 0) return [];
    // This bank supplies only the Standard kit. Percussion program changes use that kit.
    const bank = percussion ? 128 : 0, preset = this.bank.resolve(bank, percussion ? 0 : program);
    const matching = preset.regions.filter(r => midi >= r.keyRange![0] && midi <= r.keyRange![1]
      && velocity >= r.velRange![0] && velocity <= r.velRange![1]);
    if (!matching.length) return [];
    const key = bank + ':' + preset.program;
    let routes = this.routes.get(destination);
    if (!routes) { routes = new Map(); this.routes.set(destination, routes); }
    let route = routes.get(key);
    if (!route) {
      const sampler = Sf2Instrument(this.context, { preset: this.bank.toSmplr(preset), buffers: this.decode(preset),
        destination, volume: 127, scheduler: Scheduler(this.context, { lookaheadMs: Infinity }) });
      route = { sampler, voices: 0 };
      routes.set(key, route);
    }
    const activeRoute = route, activeRoutes = routes, sampler = route.sampler;
    activeRoute.voices++;
    let ended = false, released = false, remaining = matching.length;
    let callback = () => {};
    const finish = () => { if (!ended) { ended = true; callback(); } };
    let stopNote: ((time?: number) => void) | undefined;
    let cancelled = false, stopAt: number | undefined;
    const id = ++this.nextId;
    const pending = sampler.ready.then(() => {
      if (cancelled || this.disposed) { finish(); return; }
      stopNote = sampler.start({ note: midi, velocity, time: start, duration, stopId: id,
        onEnded: () => { if (--remaining === 0) finish(); } });
      if (stopAt !== undefined) stopNote(stopAt);
    }).catch(error => { this.failure = error; finish(); });
    this.pending.add(pending);
    void pending.then(() => this.pending.delete(pending));
    const stop = (time?: number) => {
      if (stopNote) stopNote(time);
      else if (time === undefined || time <= start) cancelled = true;
      else stopAt = time;
    };
    return [{ stopTime: start + duration + Math.max(...matching.map(r => r.ampRelease ?? 0)), stop,
      onEnded: next => { callback = next; if (ended) next(); },
      dispose: () => {
        if (released) return;
        released = true;
        if (--activeRoute.voices === 0) {
          sampler.dispose(); activeRoutes.delete(key);
          if (!activeRoutes.size) this.routes.delete(destination);
        }
      } }];
  }
  dispose(): void {
    this.disposed = true;
    for (const routes of this.routes.values()) for (const { sampler } of routes.values()) sampler.dispose();
    this.routes.clear(); this.buffers.clear();
  }
}
