import { isScaleMode } from './scales';
import { convertScalePitch, modeTargetPitch, scaleNotes } from './scales';
import type { MelodySettings, MelodyGlobalSettings } from './part-settings.model';
import type { PhraseNote } from './phrase-note';
import { DEFAULT_MELODY_GLOBAL, decodeMelodyGlobal } from './melody-global-settings';
import { getPitchClass, validProgressionChords, type ProgressionChord, type ScaleMode } from './harmony';
import { JavaRandom } from './java-random';
import { swingNotes } from './phrase-swing';
import { partFillMask } from './chord-span-fill';
import { decodePartPatch, velocityBounds } from './part-settings';
import { expandMelody, delayMelodyStarts, type MelodyNote as Note } from './melody-expansion';
import { blockChangeSequence, blockRhythm, blockVariance, cumulativeWeights, directionChanges,
  melodyInt as int, MELODY_EPSILON as EPS, noteRhythm } from './melody-blocks';
import { selectMelodyBlock } from './melody-catalogue';
import { applyMelodyTonalTargets } from './melody-tonal-targets';
import { adjustMelodyMinorChords, applyMelodyChordTargets, melodyRepairChords, melodyRootChords, replaceMelodyAvoidNotes } from './melody-chord-targets';
import { startingMelodyTarget } from './melody-targets';
import type { GenerationHarmony } from './harmony-voicing';
import type { MelodySkeleton } from './melody-chord-inference';
import { generateContextualMelody } from './contextual-melody';
import { durationBlocks, durationError } from './durations';

const MAJOR = [0, 2, 4, 5, 7, 9, 11];
const DURATION_MULTIPLIER = 0.95; // MidiGenerator.RunOptions.defaults()
interface Block { readonly notes: readonly number[]; readonly durations: readonly number[] }
interface Pattern { readonly changes: readonly number[]; readonly blocks: readonly Block[] }

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
 * speed, Flex/Max Block Change/Block Jump, split/start behavior, pauses/fill, accents and shared dynamics.
 * Global inputs share the project-level Melody musical settings.
 */
export function generateMelody(seed: bigint | number, key: string, scale: ScaleMode,
  progression: readonly number[], settings: MelodySettings, notesSeedOffset = 0,
  publishPattern?: (notes: readonly Pick<PhraseNote, 'midi' | 'startBeat'>[]) => void,
  globalSettings: MelodyGlobalSettings = {}, customChords?: readonly (ProgressionChord | null)[],
  harmony?: GenerationHarmony, publishSkeleton?: (notes: MelodySkeleton) => void): PhraseNote[] {
  if (!decodeMelodyGlobal(globalSettings)) throw new RangeError('Invalid global Melody inputs.');
  const globals = { ...DEFAULT_MELODY_GLOBAL, ...globalSettings };
  if (!validProgressionChords(customChords, progression.length)) throw new RangeError('Invalid Melody harmony.');
  if (typeof seed === 'number' && !Number.isSafeInteger(seed)) throw new RangeError('Use bigint for 64-bit melody seeds.');
  const { algorithm, ...fields } = settings;
  if (algorithm !== 'block' || !decodePartPatch('melody', fields) || !Number.isInteger(notesSeedOffset)
    || notesSeedOffset < -2147483648 || notesSeedOffset > 2147483647) throw new RangeError('Unsupported block melody inputs.');
  const tonic = getPitchClass(key);
  const speed = settings.speed ?? 50, maxChange = settings.maxBlockChange ?? 7, jump = settings.blockJump ?? 1;
  const targets = settings.chordNoteChoices ?? [0, 2, 2, 4], offsets = settings.melodyPatternOffsets ?? [1, 2, 1, 3];
  if (settings.algorithm !== 'block' || tonic === undefined || !isScaleMode(scale)
    || progression.length < 1 || progression.length > 32 || progression.some((degree) => !Number.isInteger(degree) || degree < 1 || degree > 7)
    || !Number.isInteger(speed) || speed < -100 || speed > 100 || !Number.isInteger(maxChange) || maxChange < 0 || maxChange > 7
    || !Number.isInteger(jump) || jump < 0 || jump > 4 || !targets.length || targets.length > 32 || !offsets.length || offsets.length > 32
    || targets.some((target) => !Number.isInteger(target) || target < -24 || target > 24)
    || offsets.some((offset) => !Number.isInteger(offset) || offset < -16 || offset > 16)) throw new RangeError('Unsupported block melody inputs.');
  if (globals.contextualMelody) {
    const result = generateContextualMelody(seed, key, scale, progression, settings, notesSeedOffset, globals, customChords, harmony);
    publishPattern?.(result.pattern);
    publishSkeleton?.(result.skeleton);
    return result.notes;
  }
  const usesDurations = settings.rhythm === 'durations';
  if (usesDurations) {
    const error = durationError(settings);
    if (error) throw new RangeError(error);
    // Supplied durations own their note/rest boundaries, including the first and last pauses.
    settings = { ...settings, splitChance: 0, startNoteChance: 100, pauseChance: 0, fillPauses: false };
  }
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
  const roots = harmony?.roots ?? melodyRootChords(key, scale, progression, customChords);
  const starting = (index: number) => startingMelodyTarget(roots, choices, index, globals.blockTargetMode);
  for (let chord = 0; chord < progression.length; chord++) {
    const originalOffset = structure[chord], offset = Math.max(0, Math.abs(originalOffset) - 1);
    const customBlocks = usesDurations ? durationBlocks(settings, blockSeed, chord, globals.patternEffect !== 1 ? offset : -1) : undefined;
    let durations = customBlocks?.map(block => block.reduce((sum, value) => sum + Math.abs(value), 0))
      ?? (globals.patternEffect !== 1 ? rhythms.get(offset) : undefined);
    if (!durations) {
      durations = blockRhythm(int(blockSeed + offset), 4, outerWeights);
      rhythms.set(offset, durations);
    }
    const first = starting(chord), next = starting(chord + 1), existing = usesDurations ? undefined : cache.get(offset);
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
      let previousType = -2147483648, startingDegree = first % 7;
      blocks = durations.map((duration, index) => {
        const shapeSeed = int(blockSeed + offset + index);
        if (index > 0) startingDegree += changes[index - 1];
        const shape = selectMelodyBlock(changes[index], jump, new JavaRandom(shapeSeed),
          new JavaRandom(shapeSeed).nextInt(100) < globals.newBlocksChance,
          customBlocks ? customBlocks[index].filter(value => value > 0).length
            : globals.patternEffect !== 1 ? existing?.blocks[index]?.notes.length : undefined, variance, directions,
          globals.blockChangePreference, globals.blockTypeWeights, startingDegree, previousType);
        const notes = shape.notes;
        if (customBlocks && notes.length !== customBlocks[index].filter(value => value > 0).length)
          throw new RangeError('No melody block fits this number of notes. Split a note or change the block settings.');
        previousType = shape.type;
        variance = Math.max(0, variance - blockVariance(notes));
        directions = Math.max(0, directions - directionChanges(notes));
        const noteDurations = customBlocks ? customBlocks[index].filter(value => value > 0) : noteRhythm(shapeSeed, duration, notes.length, speed);
        return { notes, durations: noteDurations };
      });
      if (flexible && existing && globals.patternEffect > 0) blocks = [...existing.blocks.slice(0, -1), blocks[blocks.length - 1]];
    }
    const notes: Note[] = [];
    let adjustment = 0;
    blocks.forEach((block, index) => {
      if (index > 0) adjustment += changes[index - 1] * (originalOffset < 0 ? -1 : 1);
      const pitches = block.notes.map(note => degreePitch(first + note * (originalOffset < 0 ? -1 : 1) + adjustment));
      let blockDurations = [...block.durations];
      if (!customBlocks && !existing && globals.emphasizeKey) {
        // Production inserts the mode target at index one, including tonic for Ionian.
        const target = modeTargetPitch(scale);
        const order = [0, 7, 4, 5, 9, 2, 11].filter(pitch => pitch !== target);
        if (target >= 0) order.splice(1, 0, target);
        for (let left = 0; left < pitches.length; left++) for (let right = 0; right < pitches.length; right++) {
          if (pitches[left] === pitches[right]) continue;
          const swap = order.indexOf(pitches[left] % 12) < order.indexOf(pitches[right] % 12)
            ? blockDurations[left] + EPS < blockDurations[right] : blockDurations[left] - EPS > blockDurations[right];
          if (swap) [blockDurations[left], blockDurations[right]] = [blockDurations[right], blockDurations[left]];
        }
        // Inverted blocks are temporary Java copies; their duration assignment does not change the cache.
        if (globals.patternEffect !== 1 && originalOffset >= 0) blocks = blocks!.map((stored, blockIndex) => blockIndex === index
          ? { ...stored, durations: blockDurations } : stored);
      } else if (existing && globals.patternEffect === 0) blockDurations = [...existing.blocks[index].durations];
      const blockStart = notes.length;
      block.notes.forEach((note, noteIndex) => {
        const rhythm = blockDurations[noteIndex];
        const pitch = pitches[noteIndex];
        notes.push({ midi: pitch + (settings.transpose ?? 0),
          rhythm, duration: customBlocks ? rhythm : rhythm * (0.75 + durationRandom.nextDouble() / 4) * DURATION_MULTIPLIER,
          velocity: 0, offset: 0 });
      });
      if (customBlocks) {
        const sounded = notes.splice(blockStart);
        let sound = 0;
        for (const value of customBlocks[index]) {
          const duration = Math.abs(value) * (0.75 + durationRandom.nextDouble() / 4) * DURATION_MULTIPLIER;
          notes.push(value > 0 ? { ...sounded[sound++], duration }
            : { midi: -1, rhythm: -value, duration, velocity: 0, offset: 0 });
        }
      }
    });
    if (!existing) cache.set(offset, { changes, blocks });
    byChord.push(notes);
  }
  // Java skeleton pitches precede part transpose; the TS phrase path applies it during assembly.
  // Copy canonical values before expansion mutates objects, without consuming random values.
  publishSkeleton?.(byChord.map(notes => notes.map(note => ({ midi: note.midi < 0 ? -1 : note.midi - (settings.transpose ?? 0), rhythm: note.rhythm }))));
  const expanded = expandMelody(byChord, settings, baseSeed, notesSeedOffset, minimum, maximum);
  byChord.splice(0, byChord.length, ...expanded);
  const all = byChord.flat(), firstPitches = byChord.map((notes) => notes[0].midi);
  applyMelodyTonalTargets(byChord, scale, globals.tonicNoteTarget, globals.modeNoteTarget, notesSeedOffset);
  const repairChords = globals.chordNoteTarget > 0 || globals.replaceAvoidNotes > 0
    ? harmony?.voicings ?? melodyRepairChords(key, scale, progression, customChords) : [];
  applyMelodyChordTargets(byChord, repairChords, globals.chordNoteTarget);
  repairIntervals(all);
  replaceMelodyAvoidNotes(byChord, repairChords, baseSeed, globals.replaceAvoidNotes);
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
    if (!usesDurations && remaining.length >= 3 && first.midi >= 0 && first.duration < 1.1
      && remaining[1].midi !== first.midi && remaining[2].midi !== first.midi) first.duration *= 1 + accent / 200;
  });
  delayMelodyStarts(byChord.map(notes => notes.filter(note => !removed.has(note))), settings, baseSeed);
  adjustMelodyMinorChords(byChord, roots);
  const final = all.filter((note) => !removed.has(note));
  if (publishPattern) {
    const patternNotes: Array<Pick<PhraseNote, 'midi' | 'startBeat'>> = [];
    byChord.forEach((notes, chord) => {
      let time = chord * 4;
      for (const note of notes.filter(note => !removed.has(note))) {
        patternNotes.push({ startBeat: time + note.offset, midi: note.midi < 0 ? -1
          : convertScalePitch(note.midi, MAJOR, scaleNotes(scale)) + tonic });
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
    const midi = convertScalePitch(note.midi, MAJOR, scaleNotes(scale)) + tonic;
    return [{ id: `melody-${index}`, midi: Math.max(0, Math.min(127, midi)), startBeat: starts[index] + note.offset, durationBeats: duration, velocity: note.velocity }];
  });
}
