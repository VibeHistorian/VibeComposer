export const DEFAULT_MIDI_PPQ = 960;
export const MIDI_PPQ_CHOICES = [96, 120, 192, 240, 384, 480, 768, 960, 1920, 3840, 9600] as const;
export function validMidiPpq(value: unknown): value is number {
  return typeof value === 'number' && MIDI_PPQ_CHOICES.some(ppq => ppq === value);
}
