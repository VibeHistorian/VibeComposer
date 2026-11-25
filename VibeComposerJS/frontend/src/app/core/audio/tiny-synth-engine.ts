import type { InstrumentEngine, InstrumentVoice } from './instrument-engine';
import type { InstrumentTone } from './instrument-tone';
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

const TINY_SYNTH_RELEASE_RATIO = 3.5;

function operatorEnvelope(operator: SynthOperator): SynthEnvelope {
  return operator.envelope ?? {
    delay: 0, attack: operator.a ?? 0, hold: operator.h ?? .01, decay: operator.d ?? .01,
    sustain: operator.s ?? 0, release: (operator.r ?? .05) * TINY_SYNTH_RELEASE_RATIO,
  };
}

let gmEnvelopes: readonly (readonly SynthEnvelope[])[] | undefined;
/** Read the pinned timbres without creating a realtime audio context. */
export function synthInstrumentEnvelopes(program: number, patches?: SynthPatchBank): readonly SynthEnvelope[] {
  const carriers = (operators: readonly SynthOperator[]) => operators.filter(op => !op.g).map(op => {
    const envelope = operatorEnvelope(op);
    // Native exponential decay closes 99% of the gap to sustain after ln(100) time constants.
    return op.envelope ? envelope : { ...envelope, decay: envelope.decay * Math.log(100) };
  });
  if (patches) return carriers(patches.melodic[program] ?? []);
  if (!gmEnvelopes) {
    const synth = new WebAudioTinySynth({ internalcontext: 0 }) as TinySynthInternals;
    clearInterval(synth.timerId);
    gmEnvelopes = synth.program.map(timbre => carriers(timbre.p));
  }
  return gmEnvelopes[program] ?? [];
}

/** Keep the patch's decay curve and compute the release level when a note ends mid-stage. */
function automateEnvelope(gain: AudioParam, peak: number, start: number, envelope: SynthEnvelope,
  exponentialRelease: boolean, exponentialDecay = exponentialRelease): (end: number) => void {
  const { attack, delay, hold, decay, sustain, release } = envelope;
  const attackStart = start + delay, attackEnd = attackStart + attack;
  const holdEnd = attackEnd + hold, decayEnd = holdEnd + decay;
  gain.cancelScheduledValues(start);
  gain.setValueAtTime(0, start);
  if (attack > 0) {
    gain.setValueAtTime(0, attackStart);
    gain.linearRampToValueAtTime(peak, attackEnd);
  } else gain.setValueAtTime(peak, attackStart);
  if (hold > 0) gain.setValueAtTime(peak, holdEnd);
  if (!decay) gain.setValueAtTime(peak * sustain, holdEnd);
  else if (exponentialDecay) gain.setTargetAtTime(peak * sustain, holdEnd, decay);
  else gain.linearRampToValueAtTime(peak * sustain, decayEnd);

  return end => {
    gain.cancelScheduledValues(end);
    let level = 0;
    if (end > attackStart && end <= attackEnd) {
      level = (end - attackStart) / attack;
      gain.linearRampToValueAtTime(peak * level, end);
    } else if (end > attackEnd) {
      level = end < holdEnd ? 1 : exponentialDecay && decay > 0 ? sustain + (1 - sustain) * Math.exp(-(end - holdEnd) / decay)
        : end < decayEnd ? 1 + (sustain - 1) * (end - holdEnd) / decay : sustain;
      if (!exponentialDecay && end > holdEnd && end <= decayEnd) gain.linearRampToValueAtTime(peak * level, end);
      else gain.setValueAtTime(peak * level, end);
    } else gain.setValueAtTime(0, end);
    if (release === 0) gain.setValueAtTime(0, end);
    else if (exponentialRelease) gain.setTargetAtTime(0, end, release / TINY_SYNTH_RELEASE_RATIO);
    else gain.linearRampToValueAtTime(0, end + release);
  };
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
    midi: number, velocity: number, start: number, duration: number, pitchBend?: AudioNode,
    tone?: InstrumentTone, tremoloSignal?: AudioNode): InstrumentVoice[] {
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
    const tremoloDepth = percussion ? 0 : tone?.tremoloPercent ?? 0;
    const tremolo = tremoloSignal && tremoloDepth ? this.context.createGain() : undefined;
    if (tremolo && tremoloSignal) {
      tremolo.gain.value = 1 - tremoloDepth / 200;
      tremoloSignal.connect(tremolo.gain);
      tremolo.connect(destination);
    }
    tail.connect(tremolo ?? destination);
    const disconnect = () => {
      tail.disconnect();
      if (tremolo) {
        try { tremoloSignal?.disconnect(tremolo.gain); } catch { /* The modulation route may already be disconnected. */ }
        tremolo.disconnect();
      }
    };
    const previous = this.synth.chvol[0];
    this.synth.chvol[0] = tail;
    try {
      // Channel zero remains melodic internally so drums also get cancellable voice handles.
      this.synth._note(start, 0, midi, velocity, timbre);
    } catch (error) {
      disconnect();
      throw error;
    } finally {
      this.synth.chvol[0] = previous;
    }
    const voice = this.synth.notetab.pop();
    if (!voice) { disconnect(); return []; }
    const fadeTail = (end: number) => {
      if (end <= start) return;
      tail.gain.setValueAtTime(1, Math.max(start, end - 0.005));
      tail.gain.linearRampToValueAtTime(0, end);
    };
    let stopTime: number;
    let release: InstrumentVoice['release'];
    if (percussion) {
      stopTime = start + Math.max(0.035, timbre[0].d * this.synth.releaseRatio);
    } else {
      const end = start + duration;
      const adjusted = tone && (tone.attackMs !== undefined || tone.decayMs !== undefined
        || tone.sustainPercent !== undefined || tone.releaseMs !== undefined);
      if (!adjusted) {
        this.synth._releaseNote(voice, end);
        stopTime = end + Math.max(...voice.r) * this.synth.releaseRatio;
        timbre.forEach(({ envelope }, index) => {
          if (!envelope) return;
          automateEnvelope(voice.g[index].gain, voice.v[index], start, envelope, false)(end);
          stopTime = Math.max(stopTime, end + envelope.release);
        });
      } else {
        const releases = timbre.map((operator, index) => {
          const original = operatorEnvelope(operator);
          const carrier = !operator.g;
          const decayMs = carrier ? tone.decayMs : undefined;
          const envelope = {
            ...original,
            decay: decayMs === undefined ? original.decay : decayMs / 1000,
            attack: carrier && tone.attackMs !== undefined ? tone.attackMs / 1000 : original.attack,
            sustain: carrier && tone.sustainPercent !== undefined ? tone.sustainPercent / 100 : original.sustain,
            release: carrier && tone.releaseMs !== undefined ? tone.releaseMs / 1000 : original.release,
          };
          const finish = automateEnvelope(voice.g[index].gain, voice.v[index], start, envelope, !operator.envelope,
            !operator.envelope && decayMs === undefined);
          finish(end);
          return { duration: envelope.release, finish };
        });
        const releaseLength = Math.max(...releases.map(stage => stage.duration));
        stopTime = end + releaseLength;
        if (tone.releaseMs !== undefined) {
          let releaseAt = end;
          release = time => {
            if (time >= releaseAt) return;
            releaseAt = time;
            const stop = time <= start ? time : time + releaseLength;
            if (time > start) for (const stage of releases) stage.finish(time);
            tail.gain.cancelScheduledValues(time);
            fadeTail(stop);
            for (const source of voice.o) source.stop(stop);
          };
        }
      }
    }
    // Exponential releases retain ~3% of their release level at the native
    // stop boundary. Fade that residual instead of abruptly cutting the wave.
    fadeTail(stopTime);
    let remaining = voice.o.length;
    for (const source of voice.o) {
      const detune = (source as OscillatorNode | AudioBufferSourceNode).detune;
      if (detune) pitchBend?.connect(detune);
      source.stop(stopTime);
    }
    return voice.o.map((source, index) => ({ stopTime,
      stop: (time?: number) => source.stop(time),
      release,
      onEnded: (callback: () => void) => { source.onended = callback; },
      dispose: () => {
        if (--remaining === 0) disconnect();
        source.disconnect();
        voice.g[index].disconnect();
        const detune = (source as OscillatorNode | AudioBufferSourceNode).detune;
        if (detune) {
          try { this.synth.chmod[0].disconnect(detune); } catch { /* Already detached. */ }
          try { pitchBend?.disconnect(detune); } catch { /* Channel was already disconnected. */ }
        }
      },
    }));
  }
}
