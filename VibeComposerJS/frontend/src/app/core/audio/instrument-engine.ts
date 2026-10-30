export interface InstrumentVoice {
  readonly stopTime: number;
  stop(time?: number): void;
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
    midi: number, velocity: number, start: number, duration: number): InstrumentVoice[];
  dispose(): void;
}
export type InstrumentEngineKind = 'synth' | `sf2:${number}`;
