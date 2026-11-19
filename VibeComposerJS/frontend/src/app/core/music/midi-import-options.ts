import type { PhraseNote } from './phrase-note';
import { SCALE_DEFINITIONS, SCALE_MODES, scaleNotes, type ScaleMode } from './scales';
import { snapMidiToScale } from './phrase-edit';

export interface ImportedMidiTrack {
  readonly notes: PhraseNote[];
  readonly durationBeats: number;
}
export interface MidiScaleChoice {
  readonly id: string;
  readonly label: string;
  readonly pitches: readonly number[];
}
export interface NoteHarmony {
  readonly tonic: number;
  readonly scale: ScaleMode;
}

const pc = (pitch: number) => (pitch % 12 + 12) % 12;
const names = ['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'];

export function scaleTranslation(notes: readonly PhraseNote[], from: NoteHarmony, to: NoteHarmony): MidiScaleChoice {
  const source = scaleNotes(from.scale), target = scaleNotes(to.scale);
  const sourcePcs = source.map(degree => pc(from.tonic + degree));
  const targetPcs = target.map(degree => pc(to.tonic + degree));
  // Use the closest tonic shift, retaining the source octave/degree contour.
  const shift = pc(to.tonic - from.tonic + 6) - 6;
  const pitches = notes.map(note => {
    const sourcePitch = snapMidiToScale(note.midi, sourcePcs);
    const degree = source.indexOf(pc(sourcePitch - from.tonic));
    const targetDegree = target[Math.round(degree * (target.length - 1) / Math.max(1, source.length - 1))];
    return snapMidiToScale(sourcePitch - source[degree] + shift + targetDegree, targetPcs);
  });
  const label = (harmony: NoteHarmony) => `${names[pc(harmony.tonic)]} ${SCALE_DEFINITIONS[harmony.scale].label}`;
  return { id: `${pc(from.tonic)}:${from.scale}`, label: `${label(from)} → ${label(to)}`, pitches };
}

export function midiDurationError(text: string, phraseBeats: number): string | null {
  if (!text.trim()) return null;
  const value = Number(text);
  return Number.isFinite(value) && value > 0 && value < phraseBeats ? null
    : `Enter a positive number of beats less than ${phraseBeats}.`;
}

/** Infer likely source scales, then map their degrees to the destination scale.
 * Ambiguous source keys remain explicit choices; never silently snap the import.
 */
export function midiScaleChoices(notes: readonly PhraseNote[], root: number, mode: ScaleMode): MidiScaleChoice[] {
  const targetPcs = scaleNotes(mode).map(degree => pc(root + degree));
  if (!notes.some(note => !targetPcs.includes(pc(note.midi)))) return [];
  const candidates: (MidiScaleChoice & { fit: number; movement: number })[] = [];
  for (const sourceMode of SCALE_MODES) for (let sourceRoot = 0; sourceRoot < 12; sourceRoot++) {
    const source = scaleNotes(sourceMode);
    const choice = scaleTranslation(notes, { tonic: sourceRoot, scale: sourceMode }, { tonic: root, scale: mode });
    const movement = choice.pitches.reduce((sum, pitch, index) => sum + Math.abs(pitch - notes[index].midi), 0);
    if (!movement) continue;
    const fit = notes.filter(note => source.includes(pc(note.midi - sourceRoot))).length;
    candidates.push({ ...choice, label: `${choice.label} (${Math.round(fit / notes.length * 100)}% source match)`, fit, movement });
  }
  candidates.sort((a, b) => b.fit - a.fit || a.movement - b.movement || a.id.localeCompare(b.id));
  const seen = new Set<string>();
  return candidates.filter(choice => {
    const key = choice.pitches.join(',');
    if (seen.has(key)) return false;
    seen.add(key); return true;
  }).slice(0, 8).map(({ id, label, pitches }) => ({ id, label, pitches }));
}

/** Rescale before trimming so late notes can be brought into the phrase. */
export function fitImportedMidi(track: ImportedMidiTrack, phraseBeats: number, durationText = '', pitches?: readonly number[]): PhraseNote[] {
  const error = midiDurationError(durationText, phraseBeats);
  if (error) throw new Error(error);
  const factor = durationText.trim() ? Number(durationText) / track.durationBeats : 1;
  return track.notes.map((note, index) => ({ ...note, midi: pitches?.[index] ?? note.midi,
    startBeat: note.startBeat * factor, durationBeats: note.durationBeats * factor }))
    .filter(note => note.startBeat < phraseBeats)
    .map(note => ({ ...note, durationBeats: Math.min(note.durationBeats, phraseBeats - note.startBeat) }));
}
