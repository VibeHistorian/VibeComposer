import { BASE_KEY, chordIntervals, progressionChords, type ChordSpice, type ProgressionChord } from '../music/harmony';
import type { NoteHarmony } from '../music/midi-import-options';
import { midiPointIndex, type MidiPoint, type MidiSourceNote } from '../music/midi-project-source';
import type { ArrangedPart } from '../music/part-role';
import { scaleNotes } from '../music/scales';

interface HarmonyTrack { readonly role: ArrangedPart; readonly notes: readonly MidiSourceNote[]; }
interface Evidence { readonly pitches: number[]; readonly bass: number[]; }
interface InferredChord { readonly degree: number; readonly custom: ProgressionChord | null; }

const DEGREES = [1, 2, 3, 4, 5, 6, 7];
const TRIADS_AND_SEVENTHS: readonly ChordSpice[] = ['', 'm', 'dim', '7', 'maj7', 'm7', 'sus2', 'sus4'];
const pc = (pitch: number) => (pitch + 24) % 12;
const emptyEvidence = (): Evidence => ({ pitches: Array(12).fill(0), bass: Array(12).fill(0) });

export function inferMidiHarmony(tracks: readonly HarmonyTrack[], boundaries: readonly number[],
  keys: readonly MidiPoint<NoteHarmony>[], ppq: number, warnings: string[]) {
  const evidence = boundaries.slice(1).map(emptyEvidence);
  const total = emptyEvidence();
  let minPitch = 127, maxPitch = 0, firstTick = Infinity, lastTick = 0;
  for (const track of tracks) {
    if (track.role === 'drums') continue;
    let measure = 0;
    const prominence = track.role === 'chords' ? 1.5 : 1;
    for (const note of track.notes) {
      const pitch = pc(note.midi);
      minPitch = Math.min(minPitch, note.midi);
      maxPitch = Math.max(maxPitch, note.midi);
      firstTick = Math.min(firstTick, note.tick);
      lastTick = Math.max(lastTick, note.endTick);
      while (note.tick >= boundaries[measure + 1]) measure++;
      for (let index = measure; index < evidence.length && boundaries[index] < note.endTick; index++) {
        const duration = (Math.min(note.endTick, boundaries[index + 1]) - Math.max(note.tick, boundaries[index])) / ppq;
        const weight = duration * prominence * (0.5 + note.velocity / 254);
        evidence[index].pitches[pitch] += weight;
        total.pitches[pitch] += weight;
        if (track.role === 'bass') {
          evidence[index].bass[pitch] += weight;
          total.bass[pitch] += weight;
        }
      }
    }
  }
  const keyIndex = midiPointIndex(keys, firstTick);
  const key = firstTick === Infinity ? { tonic: 0, scale: 'major' as const } : keys[keyIndex]?.value ?? estimateKey(total);
  if (firstTick !== Infinity && keys.slice(keyIndex + 1).some(point =>
    point.tick < lastTick && (point.value.tonic !== key.tonic || point.value.scale !== key.scale))) {
    warnings.push('Used one project key for the MIDI key changes.');
  }
  const shifts = [key.tonic, key.tonic - 12].sort((a, b) => Math.abs(a) - Math.abs(b));
  const transpose = shifts.find(shift => minPitch - shift >= 0 && maxPitch - shift <= 127);
  if (transpose === undefined) warnings.push('Kept the original pitches across the full MIDI range with no project transpose.');
  return { scale: key.scale, transpose: transpose ?? 0, chords: estimateChords(evidence, key.scale, transpose ?? 0) };
}

function estimateKey({ pitches, bass }: Evidence): NoteHarmony {
  let best: NoteHarmony = { tonic: 0, scale: 'major' }, strength = -Infinity;
  for (const scale of ['major', 'natural-minor', 'harmonic-minor'] as const) {
    for (let tonic = 0; tonic < 12; tonic++) {
      const third = pc(tonic + (scale === 'major' ? 4 : 3)), fifth = pc(tonic + 7);
      const fit = scaleNotes(scale).reduce((sum, interval) => sum + pitches[pc(tonic + interval)], 0);
      const score = fit + 0.25 * pitches[tonic] + 0.15 * pitches[third] + 0.1 * pitches[fifth]
        + 0.7 * bass[tonic] + 0.25 * bass[fifth];
      if (score > strength + 1e-9) { best = { tonic, scale }; strength = score; }
    }
  }
  return best;
}

function estimateChords(measures: readonly Evidence[], scale: NoteHarmony['scale'], transpose: number): InferredChord[] {
  const diatonic = progressionChords(BASE_KEY, scale, DEGREES);
  const candidates = diatonic.map((chord, index) => ({ chord, degree: index + 1, custom: null as ProgressionChord | null }));
  for (let root = 0; root < 12; root++) for (const spice of TRIADS_AND_SEVENTHS) {
    const chord: ProgressionChord = { root, spice, inversion: 0 };
    const rootDegree = diatonic.findIndex(candidate => candidate.root === root) + 1;
    candidates.push({ chord, degree: rootDegree || 1, custom: chord });
  }
  const sounding = candidates.map(({ chord, ...choice }) => ({ ...choice, root: pc(chord.root + transpose),
    pitches: chordIntervals(chord).map(interval => pc(chord.root + interval + transpose)) }));
  let previous: InferredChord = { degree: 1, custom: null };
  return measures.map(({ pitches, bass }) => {
    const mass = pitches.reduce((sum, value) => sum + value, 0);
    if (mass === 0) return previous;
    let best = -Infinity;
    for (const chord of sounding) {
      const fit = chord.pitches.reduce((sum, pitch) => sum + pitches[pitch], 0);
      const missing = chord.pitches.filter(pitch => pitches[pitch] < mass * 0.04).length;
      const score = fit + 0.75 * bass[chord.root] + 0.12 * pitches[chord.root]
        - mass * (0.1 * missing + 0.12 * (chord.pitches.length - 3) + (chord.custom ? 0.05 : 0));
      if (score > best + 1e-9) { previous = { degree: chord.degree, custom: chord.custom }; best = score; }
    }
    return previous;
  });
}
