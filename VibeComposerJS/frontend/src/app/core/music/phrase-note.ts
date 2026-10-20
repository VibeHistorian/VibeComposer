export interface PhraseNote {
  readonly id: string;
  readonly midi: number;
  readonly startBeat: number;
  readonly durationBeats: number;
  readonly velocity: number;
}
