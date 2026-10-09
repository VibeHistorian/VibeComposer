import type { CommonPartSettings, PhraseNote, RhythmSettings } from '../project/project.model';
import { partRhythmMask } from './rhythm-pattern';
import { partVelocityPattern } from './velocity-pattern';

/** Exact Java int narrowing; an explicit part seed bypasses project/track seed derivation. */
export function effectivePartSeed(seed: bigint | number, settings: CommonPartSettings): bigint {
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) throw new RangeError('Use bigint for 64-bit seeds.');
  return BigInt.asIntN(32, BigInt(settings.patternSeed || seed));
}

/** Bass/Chords use inserted -1 rests; Arpeggio uses zero and repeats before slicing. */
export function spannedPattern(settings: RhythmSettings & CommonPartSettings, chordIndex: number, arp = false, source?: readonly number[]): number[] {
  const span = settings.chordSpan ?? 1;
  const repeats = arp ? settings.patternRepeat ?? 1 : 1;
  const pattern = source ?? partRhythmMask({ ...settings, patternFlip: false });
  const expanded = Array.from({ length: repeats }, () => pattern.flatMap(value =>
    [value, ...Array(span - 1).fill(arp ? 0 : -1)])).flat();
  const size = expanded.length / span;
  const slice = expanded.slice(Math.round((chordIndex % span) * size), Math.round((chordIndex % span + 1) * size));
  return slice.map(value => value < 0 ? -1 : settings.patternFlip ? 1 - value : value);
}

/** Velocity cells do not rotate with rhythm, and span repeats a velocity into inserted subdivisions. */
export function spannedVelocities(settings: RhythmSettings & CommonPartSettings, chordIndex: number, arp = false): readonly number[] | undefined {
  const pattern = partVelocityPattern(settings);
  if (!pattern) return undefined;
  const span = settings.chordSpan ?? 1;
  const expanded = pattern.flatMap(value => Array(span).fill(value) as number[]);
  const start = (chordIndex % span) * pattern.length;
  // Java's bass/chord partOfList uses an inclusive end; the first Hits entries are consumed.
  return expanded.slice(start, start + pattern.length + (arp || span === 1 ? 0 : 1));
}

/** MidiUtils.convertChordToLength, including alternating front/back reduction. */
export function expandedVoices(pitches: readonly number[], settings: CommonPartSettings): number[] {
  const length = settings.stretchEnabled ? settings.chordNotesStretch ?? 3 : pitches.length;
  if (length >= pitches.length) {
    const octave = 1 + Math.floor(Math.abs(pitches[pitches.length - 1] - pitches[0]) / 12);
    return Array.from({ length }, (_, index) => pitches[index % pitches.length] + 12 * Math.floor(index / pitches.length) * octave);
  }
  const result = Array<number>(length);
  let front = 0, back = 0;
  for (let index = 0; index < length; index++) {
    if (index % 2 === 0) result[front] = pitches[front++];
    else { result[length - back - 1] = pitches[pitches.length - back - 1]; back++; }
  }
  return result;
}

/** Java offsets and MIDI-note feedback use thousandths of a beat, not milliseconds. */
export function applyPartTiming(notes: readonly PhraseNote[], settings: CommonPartSettings): PhraseNote[] {
  const offset = (settings.offset ?? 0) / 1000;
  const count = settings.feedbackCount ?? 0;
  if (offset === 0 && count === 0) return [...notes];
  const delay = (settings.feedbackDuration ?? 750) / 1000;
  const multiplier = (settings.feedbackVol ?? 65) / 100;
  return notes.flatMap(note => Array.from({ length: count + 1 }, (_, index) => ({
    ...note, id: index === 0 ? note.id : `${note.id}-delay-${index}`,
    startBeat: Math.max(0, note.startBeat + offset + index * delay),
    velocity: index === 0 ? note.velocity : Math.max(0, Math.min(127, Math.trunc(note.velocity * multiplier ** index))),
  }))).filter(note => note.velocity > 0).sort((left, right) => left.startBeat - right.startBeat || left.midi - right.midi);
}
