import WebAudioTinySynth from 'webaudio-tinysynth';

interface TimbreOperator { readonly d: number; }
interface TinyVoice {
  readonly o: AudioScheduledSourceNode[];
  readonly g: GainNode[];
  readonly r: number[];
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
  _note(start: number, channel: number, midi: number, velocity: number, timbre: TimbreOperator[]): void;
  _releaseNote(voice: TinyVoice, time: number): void;
}

export interface SynthVoice {
  readonly source: AudioScheduledSourceNode;
  readonly envelope: GainNode;
  readonly stopTime: number;
  disconnect(): void;
}

/** Voice adapter for pinned TinySynth 1.1.3. Its public MIDI noteOff releases
 * every matching channel/pitch. Use its voice renderer to retain independent
 * durations and track routing, including when tracks share MIDI channels.
 * Detach rendered voices from its sequencer; our scheduler owns their lifetime.
 */
export class TinySynthEngine {
  private readonly synth: TinySynthInternals;
  readonly context: BaseAudioContext;

  constructor(context?: BaseAudioContext) {
    this.synth = new WebAudioTinySynth({ quality: 1, useReverb: 0,
      internalcontext: context ? 0 : 1 }) as TinySynthInternals;
    if (context) this.synth.setAudioContext(context);
    this.context = context ?? this.synth.getAudioContext();
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
    midi: number, velocity: number, start: number, duration: number): SynthVoice[] {
    if (velocity <= 0) return [];
    const timbre = percussion ? this.synth.drummap[midi - 35]?.p : this.synth.program[program]?.p;
    if (!timbre) return [];
    const previous = this.synth.chvol[0];
    this.synth.chvol[0] = destination;
    try {
      // Channel zero remains melodic internally so drums also get cancellable voice handles.
      this.synth._note(start, 0, midi, velocity, timbre);
    } finally {
      this.synth.chvol[0] = previous;
    }
    const voice = this.synth.notetab.pop();
    if (!voice) return [];
    let stopTime: number;
    if (percussion) {
      stopTime = start + Math.max(0.035, timbre[0].d * this.synth.releaseRatio);
    } else {
      this.synth._releaseNote(voice, start + duration);
      stopTime = start + duration + Math.max(...voice.r) * this.synth.releaseRatio;
    }
    return voice.o.map((source, index) => ({ source, envelope: voice.g[index], stopTime,
      disconnect: () => {
        const detune = (source as OscillatorNode | AudioBufferSourceNode).detune;
        if (detune) {
          try { this.synth.chmod[0].disconnect(detune); } catch { /* Already detached. */ }
        }
      },
    }));
  }
}
