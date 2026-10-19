import type { PhraseNote } from '../project/project.model';
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
const pc = (pitch: number) => (pitch % 12 + 12) % 12;
const names = ['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'];
const degrees = (mode: ScaleMode) => [...new Set(scaleNotes(mode).map(pc))].sort((a, b) => a - b);

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
  const target = degrees(mode), targetPcs = target.map(degree => pc(root + degree));
  if (!notes.some(note => !targetPcs.includes(pc(note.midi)))) return [];
  const candidates: (MidiScaleChoice & { fit: number; movement: number })[] = [];
  for (const sourceMode of SCALE_MODES) for (let sourceRoot = 0; sourceRoot < 12; sourceRoot++) {
    const source = degrees(sourceMode);
    let fit = 0, movement = 0;
    const pitches = notes.map(note => {
      const relative = pc(note.midi - sourceRoot);
      if (source.includes(relative)) fit++;
      const sourcePitch = snapMidiToScale(note.midi, source.map(degree => pc(sourceRoot + degree)));
      const degree = source.indexOf(pc(sourcePitch - sourceRoot));
      const targetDegree = target[Math.min(target.length - 1, Math.round(degree * (target.length - 1) / Math.max(1, source.length - 1)))];
      // Use the closest tonic shift, retaining the source octave/degree contour.
      const shift = pc(root - sourceRoot + 6) - 6;
      const pitch = snapMidiToScale(sourcePitch - source[degree] + shift + targetDegree, targetPcs);
      movement += Math.abs(pitch - note.midi);
      return pitch;
    });
    if (pitches.every((pitch, index) => pitch === notes[index].midi)) continue;
    candidates.push({ id: `${sourceRoot}:${sourceMode}`, label: `${names[sourceRoot]} ${SCALE_DEFINITIONS[sourceMode].label} → ${names[pc(root)]} ${SCALE_DEFINITIONS[mode].label} (${Math.round(fit / notes.length * 100)}% source match)`, pitches, fit, movement });
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
