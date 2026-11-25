import type { InstrumentTone } from './instrument-tone';

export interface InstrumentVoice {
  readonly stopTime: number;
  stop(time?: number): void;
  /** Release a held audition note when a custom release is in use. */
  readonly release?: (time: number) => void;
  onEnded(callback: () => void): void;
  dispose(): void;
}
/** Shared by real-time playback, audition and offline export. Times are context seconds. */
export interface InstrumentEngine {
  readonly context: BaseAudioContext;
  prepare(): Promise<void>;
  /** Await pending sample routing before starting an offline render. */
  flush(): Promise<void>;
  schedule(destination: AudioNode, program: number, percussion: boolean,
    midi: number, velocity: number, start: number, duration: number, pitchBend?: AudioNode,
    tone?: InstrumentTone, tremoloSignal?: AudioNode): InstrumentVoice[];
  dispose(): void;
}
export type InstrumentEngineKind = 'synth' | 'chiptune' | `sf2:${number}`;
