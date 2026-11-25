import type { InstrumentEngine, InstrumentVoice } from './instrument-engine';
import type { InstrumentTone } from './instrument-tone';

export interface LoudnessProfile {
  readonly melodic: readonly number[];
  readonly drums: readonly number[];
}

/** Fixed audio gains preserve MIDI velocity/layer selection and user mix values. */
export class NormalizedInstrumentEngine implements InstrumentEngine {
  readonly context: BaseAudioContext;
  private readonly routes = new Map<AudioNode, Map<number, { gain: GainNode; voices: number }>>();
  constructor(private readonly engine: InstrumentEngine, private readonly profile: LoudnessProfile) {
    this.context = engine.context;
  }
  prepare(): Promise<void> { return this.engine.prepare(); }
  flush(): Promise<void> { return this.engine.flush(); }
  schedule(destination: AudioNode, program: number, percussion: boolean, midi: number,
    velocity: number, start: number, duration: number, pitchBend?: AudioNode, tone?: InstrumentTone, tremoloSignal?: AudioNode): InstrumentVoice[] {
    const correction = (percussion ? this.profile.drums[midi] : this.profile.melodic[program]) ?? 1;
    if (velocity <= 0 || correction === 1) return this.engine.schedule(destination, program, percussion, midi, velocity, start, duration, pitchBend, tone, tremoloSignal);
    const key = percussion ? 128 + midi : program;
    let routes = this.routes.get(destination);
    if (!routes) { routes = new Map(); this.routes.set(destination, routes); }
    let route = routes.get(key);
    if (!route) {
      const gain = this.context.createGain();
      gain.gain.value = correction;
      gain.connect(destination);
      route = { gain, voices: 0 };
      routes.set(key, route);
    }
    const active = route, activeRoutes = routes;
    const releaseRoute = () => {
      if (active.voices !== 0) return;
      active.gain.disconnect();
      activeRoutes.delete(key);
      if (!activeRoutes.size) this.routes.delete(destination);
    };
    let voices: InstrumentVoice[];
    try { voices = this.engine.schedule(active.gain, program, percussion, midi, velocity, start, duration, pitchBend, tone, tremoloSignal); }
    catch (error) { releaseRoute(); throw error; }
    active.voices += voices.length;
    if (!voices.length) releaseRoute();
    return voices.map(voice => {
      let disposed = false;
      return { stopTime: voice.stopTime, stop: time => voice.stop(time), release: voice.release, onEnded: callback => voice.onEnded(callback),
        dispose: () => {
          if (disposed) return;
          disposed = true;
          try { voice.dispose(); } finally { active.voices--; releaseRoute(); }
        } };
    });
  }
  dispose(): void {
    try { this.engine.dispose(); } finally {
      for (const routes of this.routes.values()) for (const route of routes.values()) route.gain.disconnect();
      this.routes.clear();
    }
  }
}
