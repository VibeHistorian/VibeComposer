import type { MelodySettings, MelodyGlobalSettings, PhraseNote } from '../project/project.model';
import { DEFAULT_MELODY_GLOBAL, decodeMelodyGlobal } from './melody-global-settings';
import { getPitchClass, type ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import { swingNotes } from './phrase-swing';
import { partFillMask } from './chord-span-fill';
import { decodePartPatch, velocityBounds } from './part-settings';
import { expandMelody, delayMelodyStarts, type MelodyNote as Note } from './melody-expansion';
import { blockChangeSequence, blockRhythm, blockVariance, cumulativeWeights, directionChanges,
  generateBlock, melodyInt as int, MELODY_EPSILON as EPS, noteRhythm } from './melody-blocks';

const MAJOR = [0, 2, 4, 5, 7, 9, 11];
const MINOR = [0, 2, 3, 5, 7, 8, 10];
const DURATION_MULTIPLIER = 0.95; // MidiGenerator.RunOptions.defaults()
interface Block { readonly notes: readonly number[]; readonly durations: readonly number[] }
interface Pattern { readonly changes: readonly number[]; readonly blocks: readonly Block[] }

/** Still-fixed global inputs for the current block path; remaining P5e slices replace these. */
export const MELODY_CORE_INPUTS = Object.freeze({ newBlocksChance: 100, blockTargetMode: 2,
  emphasizeKey: false,
  chordNoteTarget: 0, tonicNoteTarget: 0, modeNoteTarget: 0 });

function repeated(values: readonly number[], count: number): number[] {
  const result = [...values];
  while (result.length < count) result.push(...result);
  return result;
}

function degreePitch(degree: number): number {
  return Math.floor(degree / 7) * 12 + MAJOR[((degree % 7) + 7) % 7];
}

/** MelodyNoteUtils.applyBadIntervalRemoval, before pause selection/target repair. */
function repairIntervals(notes: Note[]): void {
  let previous = notes[0]?.midi ?? -1;
  for (const note of notes.slice(1)) {
    const pitch = note.midi;
    if (pitch < 0 || previous < 0) { previous = pitch; continue; }
    if (previous % 12 === 11 && Math.abs(pitch - previous) === 6) note.midi--;
    else if (pitch % 12 === 11 && Math.abs(pitch - previous) === 6) note.midi++;
    else if (pitch - previous >= 12) {
      const average = Math.trunc((pitch + previous) / 2), semitone = average % 12;
      note.midi = average - semitone + (semitone > 9 ? 12 : semitone < 3 ? 0 : 7);
    }
    previous = note.midi;
  }
}

function rhythmicImportance(notes: Note[]): Note[] {
  const groups: Note[][] = [[], [], []];
  let time = 0;
  for (const note of notes) {
    const multiple = (unit: number) => Math.abs(time - Math.round(time / unit) * unit) < EPS;
    groups[multiple(0.5) ? 2 : multiple(0.25) ? 1 : 0].push(note);
    time += note.rhythm;
  }
  return groups.flatMap((group) => group.sort((a, b) => a.rhythm - b.rhythm));
}

/**
 * Current MelodyBlockSkeletonGenerator → MelodyExpansion → phrase preparation.
 * P1b supports generated shapes, tonic-relative targets, reusable/inverted blocks,
 * speed, Flex/Max Block Change/Block Jump, doubled rhythm, pitch exceptions,
 * split/lead/start behavior, pauses/fill, accents and shared dynamics.
 * Global inputs still omitted from the inspector are fixed in MELODY_CORE_INPUTS.
 */
export function generateMelody(seed: bigint | number, key: string, scale: ScaleMode,
  progression: readonly number[], settings: MelodySettings, notesSeedOffset = 0,
  publishPattern?: (notes: readonly Pick<PhraseNote, 'midi' | 'startBeat'>[]) => void,
  globalSettings: MelodyGlobalSettings = {}): PhraseNote[] {
  if (!decodeMelodyGlobal(globalSettings)) throw new RangeError('Invalid global Melody inputs.');
  const globals = { ...DEFAULT_MELODY_GLOBAL, ...globalSettings };
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) throw new RangeError('Use bigint for 64-bit melody seeds.');
  const { algorithm, ...fields } = settings;
  if (algorithm !== 'block' || !decodePartPatch('melody', fields) || !Number.isInteger(notesSeedOffset)
    || notesSeedOffset < -2147483648 || notesSeedOffset > 2147483647) throw new RangeError('Unsupported block melody inputs.');
  const tonic = getPitchClass(key);
  const speed = settings.speed ?? 50, maxChange = settings.maxBlockChange ?? 7, jump = settings.blockJump ?? 1;
  const targets = settings.chordNoteChoices ?? [0, 2, 2, 4], offsets = settings.melodyPatternOffsets ?? [1, 2, 1, 3];
  if (settings.algorithm !== 'block' || tonic === undefined || !['major', 'natural-minor'].includes(scale)
    || progression.length < 1 || progression.length > 32 || progression.some((degree) => !Number.isInteger(degree) || degree < 1 || degree > 7)
    || !Number.isInteger(speed) || speed < -100 || speed > 100 || !Number.isInteger(maxChange) || maxChange < 0 || maxChange > 7
    || !Number.isInteger(jump) || jump < 0 || jump > 4 || !targets.length || targets.length > 32 || !offsets.length || offsets.length > 32
    || targets.some((target) => !Number.isInteger(target) || target < -14 || target > 14)
    || offsets.some((offset) => !Number.isInteger(offset) || offset < -16 || offset > 16)) throw new RangeError('Unsupported block melody inputs.');
  if (settings.generationEnabled === false) return [];
  const baseSeed = settings.patternSeed ? int(settings.patternSeed) : Number(BigInt.asIntN(32, BigInt(seed)));
  const blockSeed = int(baseSeed + notesSeedOffset);
  const durationRandom = new JavaRandom(int(blockSeed + 5));
  const [minimum, maximum] = velocityBounds(settings, [80, 105]);
  const accent = settings.accents ?? 100;
  const choices = repeated(targets, progression.length), structure = repeated(offsets, progression.length);
  const cache = new Map<number, Pattern>(), rhythms = new Map<number, readonly number[]>();
  const byChord: Note[][] = [];
  const outerQuick = (speed - 50) * 4;
  const outerWeights = cumulativeWeights([200 + outerQuick, Math.max(1, 200 + outerQuick / 2), 200 + outerQuick, 200 - outerQuick]);
  const starting = (index: number) => 35 + choices[index === choices.length ? index - 1 : index % choices.length];
  for (let chord = 0; chord < progression.length; chord++) {
    const originalOffset = structure[chord], offset = Math.max(0, Math.abs(originalOffset) - 1);
    const doubled = new JavaRandom(int(baseSeed + 3 + offset)).nextInt(100) < (settings.doubledRhythmChance ?? 0);
    let durations = globals.patternEffect !== 1 ? rhythms.get(offset) : undefined;
    if (!durations) {
      durations = blockRhythm(int(blockSeed + offset), doubled ? 2 : 4, outerWeights);
      if (doubled) durations = [...durations, ...durations];
      rhythms.set(offset, durations);
    }
    const first = starting(chord), next = starting(chord + 1), existing = cache.get(offset);
    let changes = existing && globals.patternEffect > 0 ? existing.changes
      : blockChangeSequence(first, next, blockSeed, durations.length, maxChange, globals.maxDirectionChanges);
    // Java stores the sequence's direction count alongside its unadjusted changes.
    // Flex changes the last target without recomputing this remaining budget.
    const remainingDirections = globals.maxDirectionChanges - directionChanges(changes);
    const flexible = (settings.patternFlexible ?? true) && changes.length > 1;
    if (flexible && existing) {
      const adjusted = [...changes];
      adjusted[adjusted.length - 1] += next - first - changes.reduce((sum, value) => sum + value, 0);
      changes = adjusted;
    }
    let blocks = existing && globals.patternEffect > 0 && !flexible ? existing.blocks : undefined;
    if (!blocks) {
      let variance = 4, directions = remainingDirections;
      blocks = durations.map((duration, index) => {
        const shapeSeed = int(blockSeed + offset + index);
        const notes = generateBlock(changes[index], jump, new JavaRandom(shapeSeed),
          globals.patternEffect !== 1 ? existing?.blocks[index]?.notes.length : undefined, variance, directions);
        variance = Math.max(0, variance - blockVariance(notes));
        directions = Math.max(0, directions - directionChanges(notes));
        let noteDurations = noteRhythm(shapeSeed, duration, notes.length, speed);
        if (globals.arpySurprises && notes.length === 4 && (speed < 20 || speed > 80)) {
          const low = speed < 20 ? 0.25 * 0.99 : 1.5 * 0.99;
          const high = speed < 20 ? 0.25 * 1.01 : 4 * 1.01;
          if (noteDurations.some(value => value > low && value < high)) noteDurations = notes.map(() => duration / notes.length);
        }
        return { notes, durations: noteDurations };
      });
      if (flexible && existing && globals.patternEffect > 0) blocks = [...existing.blocks.slice(0, -1), blocks[blocks.length - 1]];
    }
    if (!existing) cache.set(offset, { changes, blocks });
    const notes: Note[] = [];
    const exceptionRandom = new JavaRandom(int(baseSeed + 2 + notesSeedOffset + offset));
    let exceptions = settings.maxNoteExceptions ?? 0;
    let adjustment = 0;
    blocks.forEach((block, index) => {
      if (index > 0) adjustment += changes[index - 1] * (originalOffset < 0 ? -1 : 1);
      block.notes.forEach((note, noteIndex) => {
        const rhythm = existing && globals.patternEffect === 0
          ? existing.blocks[index].durations[noteIndex] : block.durations[noteIndex];
        let pitch = degreePitch(first + note * (originalOffset < 0 ? -1 : 1) + adjustment);
        const exceptionIndexValid = globals.singleNoteExceptions
          ? noteIndex === block.notes.length - 1 && index === blocks.length - 1 : noteIndex > 0;
        if (exceptionIndexValid && exceptions > 0 && exceptionRandom.nextInt(100) < (settings.noteExceptionChance ?? 0)) {
          pitch += (exceptionRandom.nextBoolean() ? 1 : -1) * MAJOR[exceptionRandom.nextInt(globals.singleNoteExceptions ? 7 : 4)];
          const semitone = pitch % 12;
          const closest = MAJOR.reduce((best, value) => Math.abs(semitone - value) < Math.abs(semitone - best) ? value : best, MAJOR[0]);
          pitch += closest - semitone;
          exceptions--;
        }
        notes.push({ midi: pitch + (settings.transpose ?? 0),
          rhythm, duration: rhythm * (0.75 + durationRandom.nextDouble() / 4) * DURATION_MULTIPLIER,
          velocity: 0, offset: 0 });
      });
    });
    byChord.push(notes);
  }
  const expanded = expandMelody(byChord, settings, baseSeed, notesSeedOffset, minimum, maximum);
  byChord.splice(0, byChord.length, ...expanded);
  const all = byChord.flat(), firstPitches = byChord.map((notes) => notes[0].midi);
  repairIntervals(all);
  const fill = partFillMask(progression.length, settings), removed = new Set<Note>();
  byChord.forEach((notes, chord) => {
    const ordered = rhythmicImportance(notes), paused = Math.round(notes.length * (settings.pauseChance ?? 0) / 100);
    ordered.forEach((note, index) => {
      if (index < paused) {
        // jMusic overloads equals(Note): pitch + duration, ignoring velocity/rhythm.
        // Identical split notes can therefore share the protected first-note status.
        if (!(settings.fillPauses && (globals.fillPausesPerChord || chord === 0) && note.midi === notes[0].midi
          && note.duration === notes[0].duration)) note.midi = -1;
      } else if (!fill[chord]) note.midi = -1;
    });
  });
  if (settings.fillPauses) {
    let previous = all[0];
    byChord.forEach((notes, chord) => notes.forEach((note, index) => {
      if (note === all[0]) return;
      if (note.midi < 0 && !(index === 0 && globals.fillPausesPerChord)) {
        previous.rhythm += note.rhythm;
        if (fill[chord]) previous.duration += note.rhythm;
        removed.add(note);
      } else previous = note;
    }));
  }
  byChord.forEach((notes, chord) => {
    const remaining = notes.filter((note) => !removed.has(note)), first = remaining[0];
    if (!first) return;
    if (fill[chord] && (first.midi >= 0 || globals.fillPausesPerChord)) first.midi = firstPitches[chord];
    if (remaining.length >= 3 && first.midi >= 0 && first.duration < 1.1
      && remaining[1].midi !== first.midi && remaining[2].midi !== first.midi) first.duration *= 1 + accent / 200;
  });
  delayMelodyStarts(byChord.map(notes => notes.filter(note => !removed.has(note))), settings, baseSeed);
  const final = all.filter((note) => !removed.has(note));
  if (publishPattern) {
    const patternNotes: Array<Pick<PhraseNote, 'midi' | 'startBeat'>> = [];
    byChord.forEach((notes, chord) => {
      let time = chord * 4;
      for (const note of notes.filter(note => !removed.has(note))) {
        const degree = MAJOR.indexOf(note.midi % 12);
        patternNotes.push({ startBeat: time + note.offset, midi: note.midi < 0 ? -1 : note.midi
          + (scale === 'natural-minor' && degree >= 0 ? MINOR[degree] - MAJOR[degree] : 0) + tonic });
        time += note.rhythm;
      }
    });
    publishPattern(patternNotes);
  }
  swingNotes(final, settings.swingPercent ?? 50);
  const length = settings.noteLengthMultiplier ?? 100;
  let time = 0;
  const starts = final.map((note) => { const start = time; time += note.rhythm; return start; });
  return final.flatMap((note, index) => {
    let duration = note.duration * length / 100;
    if (length > 100) {
      const collision = [index + 1, index + 2].find((next) => next < final.length && note.midi === final[next].midi);
      if (collision !== undefined) duration = Math.min(duration,
        starts[collision] + final[collision].offset - starts[index] - note.offset);
    }
    if (note.midi < 0) return [];
    const degree = MAJOR.indexOf(note.midi % 12);
    const midi = note.midi + (scale === 'natural-minor' && degree >= 0 ? MINOR[degree] - MAJOR[degree] : 0) + tonic;
    return [{ id: `melody-${index}`, midi: Math.max(0, Math.min(127, midi)), startBeat: starts[index] + note.offset, durationBeats: duration, velocity: note.velocity }];
  });
}
