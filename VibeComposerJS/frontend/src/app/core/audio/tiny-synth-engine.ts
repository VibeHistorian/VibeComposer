import type { InstrumentEngine, InstrumentVoice } from './instrument-engine';
import WebAudioTinySynth from 'webaudio-tinysynth';

/** Matches this application's SF2 sampler's linear volume envelope, in seconds. */
export interface SynthEnvelope {
  readonly delay: number; readonly attack: number; readonly hold: number;
  readonly decay: number; readonly sustain: number; readonly release: number;
}
interface TimbreOperator extends SynthOperator { readonly d: number; }
export interface SynthOperator {
  w: string; v: number; g?: number; t?: number; f?: number;
  a?: number; h?: number; d?: number; s?: number; r?: number; p?: number; q?: number; k?: number;
  envelope?: SynthEnvelope;
  /** For gain routing (g > 10), v is a fraction of the target carrier's peak gain. */
  relativeAmplitude?: boolean;
}
export interface SynthPatchBank {
  readonly melodic: readonly (readonly SynthOperator[])[];
  readonly drums: readonly (readonly SynthOperator[])[];
  readonly waves?: Readonly<Record<string, { real: readonly number[]; imag: readonly number[] }>>;
}
interface TinyVoice {
  readonly o: AudioScheduledSourceNode[];
  readonly g: GainNode[];
  readonly r: number[];
  readonly v: number[];
  e: number;
}
interface TinySynthInternals extends WebAudioTinySynth {
  readonly program: { readonly p: TimbreOperator[] }[];
  readonly drummap: { readonly p: TimbreOperator[] }[];
  readonly chvol: AudioNode[];
  readonly chmod: GainNode[];
  readonly notetab: TinyVoice[];
  readonly out: GainNode;
  readonly releaseRatio: number;
  readonly timerId: ReturnType<typeof setInterval>;
  readonly lfo: OscillatorNode;
  readonly comp: DynamicsCompressorNode;
  readonly wave: Record<string, PeriodicWave>;
  setTimbre(mode: number, note: number, operators: SynthOperator[]): void;
  _note(start: number, channel: number, midi: number, velocity: number, timbre: TimbreOperator[]): void;
  _releaseNote(voice: TinyVoice, time: number): void;
}


/** Voice adapter for pinned TinySynth 1.1.3. Its public MIDI noteOff releases
 * every matching channel/pitch. Use its voice renderer to retain independent
 * durations and track routing, including when tracks share MIDI channels.
 * Detach rendered voices from its sequencer; our scheduler owns their lifetime.
 */
export class TinySynthEngine implements InstrumentEngine {
  async prepare(): Promise<void> {}
  async flush(): Promise<void> {}
  private readonly synth: TinySynthInternals;
  readonly context: BaseAudioContext;

  constructor(context?: BaseAudioContext, patches?: SynthPatchBank) {
    this.synth = new WebAudioTinySynth({ quality: 1, useReverb: 0,
      internalcontext: context ? 0 : 1 }) as TinySynthInternals;
    if (context) this.synth.setAudioContext(context);
    this.context = context ?? this.synth.getAudioContext();
    if (patches) {
      for (const [name, wave] of Object.entries(patches.waves ?? {})) {
        this.synth.wave[name] = this.context.createPeriodicWave(
          new Float32Array(wave.real), new Float32Array(wave.imag), { disableNormalization: true });
      }
      // setTimbre fills defaults in place. Keep the shipped bank immutable across engines.
      patches.melodic.forEach((patch, program) => this.synth.setTimbre(0, program, patch.map(op => ({ ...op }))));
      patches.drums.forEach((patch, index) => this.synth.setTimbre(1, index + 35, patch.map(op => ({ ...op }))));
    }
    // All rendered operators go straight to our track buses, bypassing its mixer.
    this.synth.out.disconnect();
  }

  /** The pinned dependency patch exposes its timer so offline exports can release it. */
  dispose(): void {
    clearInterval(this.synth.timerId);
    this.synth.lfo.stop();
    this.synth.lfo.disconnect();
    this.synth.out.disconnect();
    this.synth.comp.disconnect();
  }

  schedule(destination: AudioNode, program: number, percussion: boolean,
    midi: number, velocity: number, start: number, duration: number): InstrumentVoice[] {
    if (velocity <= 0) return [];
    // TinySynth has no shaker (82); approximate it with maracas (70) for internal audio only.
    if (percussion && midi === 82) midi = 70;
    let timbre = percussion ? this.synth.drummap[midi - 35]?.p : this.synth.program[program]?.p;
    if (!timbre) return [];
    if (timbre.some(op => op.relativeAmplitude)) {
      const operators = timbre;
      timbre = operators.map(op => {
        const target = op.g !== undefined && op.g > 10 ? operators[op.g - 11] : undefined;
        if (!op.relativeAmplitude || !target) return op;
        // Native TinySynth gain modulators use an absolute gain, independent of
        // velocity. Scale relative depth per voice to avoid overmodulating quiet notes.
        return { ...op, v: op.v * target.v * velocity * velocity / 16384
          * 2 ** ((midi - 60) / 12 * (target.k ?? 0)), k: 0 };
      });
    }
    const tail = this.context.createGain();
    tail.connect(destination);
    const previous = this.synth.chvol[0];
    this.synth.chvol[0] = tail;
    try {
      // Channel zero remains melodic internally so drums also get cancellable voice handles.
      this.synth._note(start, 0, midi, velocity, timbre);
    } catch (error) {
      tail.disconnect();
      throw error;
    } finally {
      this.synth.chvol[0] = previous;
    }
    const voice = this.synth.notetab.pop();
    if (!voice) { tail.disconnect(); return []; }
    let stopTime: number;
    if (percussion) {
      stopTime = start + Math.max(0.035, timbre[0].d * this.synth.releaseRatio);
    } else {
      this.synth._releaseNote(voice, start + duration);
      stopTime = start + duration + Math.max(...voice.r) * this.synth.releaseRatio;
      timbre.forEach((operator, index) => {
        const envelope = operator.envelope;
        if (!envelope) return;
        const gain = voice.g[index].gain, peak = voice.v[index];
        const attackStart = start + envelope.delay, attackEnd = attackStart + envelope.attack;
        const holdEnd = attackEnd + envelope.hold, decayEnd = holdEnd + envelope.decay;
        const end = start + duration;
        const levelAt = (time: number) => time < attackStart ? 0 : time < attackEnd
          ? (time - attackStart) / Math.max(envelope.attack, 1e-9) : time < holdEnd ? 1
          : time < decayEnd ? 1 + (envelope.sustain - 1) * (time - holdEnd) / Math.max(envelope.decay, 1e-9)
          : envelope.sustain;
        // TinySynth still renders the oscillator and velocity gain. Replace only
        // this operator's automation, clipping stages when note-off interrupts one.
        gain.cancelScheduledValues(start);
        gain.setValueAtTime(0, start);
        if (attackStart < end) gain.setValueAtTime(0, attackStart);
        if (attackEnd < end) gain.linearRampToValueAtTime(peak, attackEnd);
        if (holdEnd < end) gain.setValueAtTime(peak, holdEnd);
        if (decayEnd < end) gain.linearRampToValueAtTime(peak * envelope.sustain, decayEnd);
        if (end > attackStart && (end <= attackEnd || end > holdEnd && end <= decayEnd)) {
          gain.linearRampToValueAtTime(peak * levelAt(end), end);
        } else gain.setValueAtTime(peak * levelAt(end), end);
        gain.linearRampToValueAtTime(0, end + envelope.release);
        stopTime = Math.max(stopTime, end + envelope.release);
      });
    }
    // Exponential releases retain ~3% of their release level at the native
    // stop boundary. Fade that residual instead of abruptly cutting the wave.
    tail.gain.setValueAtTime(1, Math.max(start, stopTime - 0.005));
    tail.gain.linearRampToValueAtTime(0, stopTime);
    let remaining = voice.o.length;
    for (const source of voice.o) source.stop(stopTime);
    return voice.o.map((source, index) => ({ stopTime,
      stop: (time?: number) => source.stop(time),
      onEnded: (callback: () => void) => { source.onended = callback; },
      dispose: () => {
        if (--remaining === 0) tail.disconnect();
        source.disconnect();
        voice.g[index].disconnect();
        const detune = (source as OscillatorNode | AudioBufferSourceNode).detune;
        if (detune) {
          try { this.synth.chmod[0].disconnect(detune); } catch { /* Already detached. */ }
        }
      },
    }));
  }
}
