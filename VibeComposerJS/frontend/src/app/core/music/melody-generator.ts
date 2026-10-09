import type { MelodySettings, PhraseNote } from '../project/project.model';
import { getPitchClass, type ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import { partFillMask } from './chord-span-fill';
import { decodePartPatch, velocityBounds } from './part-settings';
import { blockChangeSequence, blockRhythm, blockVariance, cumulativeWeights, directionChanges,
  generateBlock, melodyInt as int, MELODY_EPSILON as EPS, noteRhythm } from './melody-blocks';

const MAJOR = [0, 2, 4, 5, 7, 9, 11];
const MINOR = [0, 2, 3, 5, 7, 8, 10];
const DURATION_MULTIPLIER = 0.95; // MidiGenerator.RunOptions.defaults()
interface Block { readonly notes: readonly number[]; readonly durations: readonly number[] }
interface Pattern { readonly changes: readonly number[]; readonly blocks: readonly Block[] }
interface Note { midi: number; rhythm: number; duration: number; velocity: number }

/** Explicit supported inputs for the current block path. Extended choices are P5. */
export const MELODY_CORE_INPUTS = Object.freeze({ newBlocksChance: 100, blockTargetMode: 2,
  patternEffect: 2, maxDirectionChanges: 2, doubledRhythmChance: 0, emphasizeKey: false,
  noteExceptions: 0, splitChance: 0, leadChordsChance: 0, startNoteChance: 100,
  chordNoteTarget: 0, tonicNoteTarget: 0, modeNoteTarget: 0, fillPausesPerChord: false });

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

/** Port of MidiGenerator.swingPhrase with quarter-note swing units and no global override. */
function swingNotes(notes: Note[], percent: number): void {
  if (percent === 50) return;
  const separators: number[] = [];
  let time = 0;
  notes.forEach((note, index) => {
    time += note.rhythm;
    if (time + EPS > 4) { separators.push(index); time = 0; }
  });
  if (time > EPS) separators.push(notes.length - 1);
  let separator = 0, adjustment = percent / 50 - 1;
  let swung: Note | undefined, suitable: Note | undefined;
  time = 0;
  const multiple = (unit: number) => Math.abs(time - Math.round(time / unit) * unit) < EPS;
  const adjust = (note: Note, minimum = true) => {
    note.rhythm += adjustment;
    note.duration = (minimum ? Math.max(0.125, note.rhythm) : note.rhythm) * DURATION_MULTIPLIER;
  };
  notes.forEach((note, index) => {
    const duration = note.rhythm;
    if (duration < EPS) return;
    if (index > separators[separator]) {
      separator++; adjustment = percent / 50 - 1; time = 0;
      if (swung) { adjustment *= -1; adjust(swung, false); adjustment *= -1; swung = suitable = undefined; }
    }
    time += duration;
    let processed = false;
    if (!swung) { if (duration - Math.abs(adjustment) > EPS) suitable = note; processed = true; }
    else if (duration - Math.abs(adjustment) > EPS && !suitable) { suitable = note; processed = true; }
    if (multiple(1)) {
      if (!swung && multiple(2)) suitable = undefined;
      else if (suitable) {
        adjust(suitable); adjustment *= -1;
        swung = swung ? undefined : suitable; suitable = undefined;
      } else if (swung) { adjust(swung); adjustment *= -1; swung = suitable = undefined; }
    }
    if (!processed && !multiple(2) && swung && duration - Math.abs(adjustment) > EPS && !suitable) suitable = note;
  });
  if (swung) adjust(swung);
}

/**
 * Current MelodyBlockSkeletonGenerator → MelodyExpansion → phrase preparation.
 * P1b supports generated shapes, tonic-relative targets, reusable/inverted blocks,
 * speed, Flex/Max Block Change/Block Jump, pauses/fill, accents and shared dynamics.
 * Inputs omitted from the minimum editor are fixed in MELODY_CORE_INPUTS.
 */
export function generateMelody(seed: bigint | number, key: string, scale: ScaleMode,
  progression: readonly number[], settings: MelodySettings, notesSeedOffset = 0): PhraseNote[] {
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
  const baseSeed = settings.patternSeed ? int(settings.patternSeed) : Number(BigInt.asIntN(32, BigInt(seed)));
  const blockSeed = int(baseSeed + notesSeedOffset);
  const durationRandom = new JavaRandom(int(blockSeed + 5));
  const velocityRandom = new JavaRandom(int(baseSeed + 2 + notesSeedOffset)); // orderOffset = 1
  const accentRandom = new JavaRandom(int(baseSeed + 21));
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
    // Java consumes this stream even when Doubled Rhythm is fixed to zero.
    new JavaRandom(int(baseSeed + 3 + offset)).nextInt(100);
    let durations = rhythms.get(offset);
    if (!durations) { durations = blockRhythm(int(blockSeed + offset), 4, outerWeights); rhythms.set(offset, durations); }
    const first = starting(chord), next = starting(chord + 1), existing = cache.get(offset);
    let changes = existing?.changes ?? blockChangeSequence(first, next, blockSeed, durations.length, maxChange);
    const flexible = (settings.patternFlexible ?? true) && changes.length > 1;
    if (flexible && existing) {
      const adjusted = [...changes];
      adjusted[adjusted.length - 1] += next - first - changes.reduce((sum, value) => sum + value, 0);
      changes = adjusted;
    }
    let blocks = existing && !flexible ? existing.blocks : undefined;
    if (!blocks) {
      let variance = 4, directions = 2 - directionChanges(changes);
      blocks = durations.map((duration, index) => {
        const shapeSeed = int(blockSeed + offset + index);
        const notes = generateBlock(changes[index], jump, new JavaRandom(shapeSeed), existing?.blocks[index]?.notes.length, variance, directions);
        variance = Math.max(0, variance - blockVariance(notes));
        directions = Math.max(0, directions - directionChanges(notes));
        return { notes, durations: noteRhythm(shapeSeed, duration, notes.length, speed) };
      });
      if (flexible && existing) blocks = [...existing.blocks.slice(0, -1), blocks[blocks.length - 1]];
    }
    if (!existing) cache.set(offset, { changes, blocks });
    const notes: Note[] = [];
    let adjustment = 0;
    blocks.forEach((block, index) => {
      if (index > 0) adjustment += changes[index - 1] * (originalOffset < 0 ? -1 : 1);
      block.notes.forEach((note, noteIndex) => {
        notes.push({ midi: degreePitch(first + note * (originalOffset < 0 ? -1 : 1) + adjustment) + (settings.transpose ?? 0),
          rhythm: block.durations[noteIndex], duration: block.durations[noteIndex] * (0.75 + durationRandom.nextDouble() / 4) * DURATION_MULTIPLIER,
          velocity: 0 });
      });
    });
    let position = 0;
    for (const note of notes) {
      note.velocity = velocityRandom.nextInt(Math.max(1, maximum - minimum)) + minimum;
      if (position / 4 < EPS && accentRandom.nextInt(100) < accent) {
        note.velocity = Math.min(127, note.velocity + 15 + accentRandom.nextInt(11) - 5 + Math.trunc(accent / 20));
      }
      position += note.rhythm;
    }
    byChord.push(notes);
  }
  const all = byChord.flat(), firstPitches = byChord.map((notes) => notes[0].midi);
  repairIntervals(all);
  const fill = partFillMask(progression.length, settings), removed = new Set<Note>();
  byChord.forEach((notes, chord) => {
    const ordered = rhythmicImportance(notes), paused = Math.round(notes.length * (settings.pauseChance ?? 0) / 100);
    ordered.forEach((note, index) => {
      if (index < paused) {
        if (!(settings.fillPauses && chord === 0 && note === notes[0])) note.midi = -1;
      } else if (!fill[chord]) note.midi = -1;
    });
  });
  if (settings.fillPauses) {
    let previous = all[0];
    byChord.forEach((notes, chord) => notes.forEach((note) => {
      if (note === all[0]) return;
      if (note.midi < 0) {
        previous.rhythm += note.rhythm;
        if (fill[chord]) previous.duration += note.rhythm;
        removed.add(note);
      } else previous = note;
    }));
  }
  byChord.forEach((notes, chord) => {
    const remaining = notes.filter((note) => !removed.has(note)), first = remaining[0];
    if (!first) return;
    if (fill[chord] && first.midi >= 0) first.midi = firstPitches[chord];
    if (remaining.length >= 3 && first.midi >= 0 && first.duration < 1.1
      && remaining[1].midi !== first.midi && remaining[2].midi !== first.midi) first.duration *= 1 + accent / 200;
  });
  const final = all.filter((note) => !removed.has(note));
  swingNotes(final, settings.swingPercent ?? 50);
  const length = settings.noteLengthMultiplier ?? 100;
  let time = 0;
  const starts = final.map((note) => { const start = time; time += note.rhythm; return start; });
  return final.flatMap((note, index) => {
    let duration = note.duration * length / 100;
    if (length > 100) {
      const collision = [index + 1, index + 2].find((next) => next < final.length && note.midi === final[next].midi);
      if (collision !== undefined) duration = Math.min(duration, starts[collision] - starts[index]);
    }
    if (note.midi < 0) return [];
    const degree = MAJOR.indexOf(note.midi % 12);
    const midi = note.midi + (scale === 'natural-minor' && degree >= 0 ? MINOR[degree] - MAJOR[degree] : 0) + tonic;
    return [{ id: `melody-${index}`, midi: Math.max(0, Math.min(127, midi)), startBeat: starts[index], durationBeats: duration, velocity: note.velocity }];
  });
}
