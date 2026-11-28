import { BASE_KEY } from './harmony';
import { processManualPhrase } from './manual-phrase-processing';
import { convertManualPhrase } from './manual-phrase-mode';
import { resolveDrumEndFill } from './arrangement-drum-fill';
import { applyArpSubdivision, resolveArpSubdivision } from './arrangement-subdivision-ramp';
import { applyVelocityEnvelope, resolveVelocityEnvelope } from './arrangement-velocity-envelope';
import { applyArpEndFill, resolveArpEndFill } from './arrangement-end-fill';
import { applySectionCut, resolvePreDropCut } from './arrangement-boundary-events';
import { processOutputNotes } from './output-processing';
import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionProject } from '../project/project.model';
import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from './phrase-note';
import type { ArrangedPart } from './part-role';
import { shouldGenerateTrackInSection } from './arrangement-generator';
import { generateArpeggio } from './arpeggio-generator';
import { generateBassline } from './bass-generator';
import { generateChordPart } from './chord-generator';
import { generateDrumPart } from './drum-generator';
import { generateMelody } from './melody-generator';
import { resolvePartTrack } from './part-settings';
import { applyPartTiming } from './part-processing';
import { melodyContext, type MelodyContext } from './melody-context';
import { isMelodyGrid } from './melody-grid';
import { baseGenerationHarmony, sectionGenerationHarmony, withTrackSeed } from './project-harmony';
import type { GenerationHarmony } from './harmony-voicing';
import { sectionDurationBeats, sectionProject, sectionTranspose } from './section-context';
import { trackHasSectionHarmony, trackHasSectionRhythm } from './harmony-display';
import { applyPhraseRangeEdits, hasManualPhrase } from './phrase-range-edit';
import { sectionPhraseBounds } from './phrase-bounds';
import { clipNotesToFill, sectionHalfFill, type FillWindow } from './chord-span-fill';

/** Detached notes for the global preview, editor and validation callers. */
export function generateTrackPhrase(project: CompositionProject, track: CompositionTrack, includeTiming = true, context?: MelodyContext,
  harmony: GenerationHarmony = baseGenerationHarmony(project)): PhraseNote[] {
  const notes = generatedTrackPhrase(project, track, context, harmony).map(note => ({ ...note }));
  return includeTiming ? applyPartTiming(notes, track.generatorSettings) : notes;
}

const MAX_GENERATED_PHRASES = 256;
const MAX_GENERATED_NOTES = 65_536;
const generatedPhrases = new Map<string, readonly PhraseNote[]>();
let generatedNoteCount = 0;

function generationHarmonyInputs(role: ArrangedPart, harmony: GenerationHarmony) {
  switch (role) {
    case 'melody': return [harmony.roots, harmony.voicings, harmony.soundingVoicings, harmony.durations];
    case 'bass': return [harmony.roots, harmony.soundingRoots, harmony.durations];
    case 'chords':
    case 'arpeggio': return [harmony.voicings, harmony.soundingVoicings];
    case 'drums': return [];
  }
}

function generationKeyValue(this: object, key: string, value: unknown): unknown {
  // Preserve values that JSON would conflate, including explicit undefined fields rejected by the generators.
  if (value === undefined) return Object.hasOwn(this, key) ? { undefined: true } : { hole: true };
  if (typeof value === 'number') {
    if (Object.is(value, -0)) return { number: '-0' };
    if (!Number.isFinite(value)) return { number: String(value) };
  }
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const entries = Object.entries(value).sort(([a], [b]) => a < b ? -1 : 1);
    return Object.fromEntries(entries);
  }
  return value;
}

function usesMelodyRhythm(track: CompositionTrack): boolean {
  return (track.role === 'arpeggio' || track.role === 'drums') && track.generatorSettings.rhythm === 'melody';
}

/** Share a generated cycle across equivalent sections and immutable project snapshots. */
function generatedTrackPhrase(project: CompositionProject, track: CompositionTrack, context: MelodyContext | undefined,
  harmony: GenerationHarmony, actualBeats = false): readonly PhraseNote[] {
  const { role, generatorSettings } = track;
  const seed = BigInt(withTrackSeed(project, track).seed);
  const melody = usesMelodyRhythm(track) ? context ?? sectionMelodyContext(project) : undefined;
  const orderOffset = role === 'arpeggio' ? track.orderOffset
    ?? Math.max(1, project.tracks.filter(candidate => candidate.role === role).findIndex(candidate => candidate.id === track.id) + 1) : undefined;
  const harmonyInputs = role === 'drums' ? project.progression.length
    : [project.scale, project.progression, project.progressionChords, generationHarmonyInputs(role, harmony)];
  const key = JSON.stringify([role, seed.toString(), generatorSettings, harmonyInputs,
    role === 'melody' ? project.melodyGlobal : undefined, melody?.pitches, orderOffset, actualBeats], generationKeyValue);
  const cached = generatedPhrases.get(key);
  if (cached) {
    generatedPhrases.delete(key);
    generatedPhrases.set(key, cached);
    return cached;
  }

  const notes = generateUntimedTrackPhrase(project, track, seed, melody, harmony, orderOffset, actualBeats);
  if (notes.length > MAX_GENERATED_NOTES) return notes;
  generatedPhrases.set(key, notes);
  generatedNoteCount += notes.length;
  while (generatedPhrases.size > MAX_GENERATED_PHRASES || generatedNoteCount > MAX_GENERATED_NOTES) {
    const oldest = generatedPhrases.keys().next().value!;
    generatedNoteCount -= generatedPhrases.get(oldest)!.length;
    generatedPhrases.delete(oldest);
  }
  return notes;
}

function generateUntimedTrackPhrase(project: CompositionProject, track: CompositionTrack, seed: bigint, melody: MelodyContext | undefined,
  harmony: GenerationHarmony, orderOffset: number | undefined, actualBeats: boolean): PhraseNote[] {
  let notes: PhraseNote[];
  switch (track.role) {
    case 'melody':
      notes = generateMelody(seed, BASE_KEY, project.scale, project.progression, track.generatorSettings,
        0, undefined, project.melodyGlobal, project.progressionChords, harmony, undefined, actualBeats);
      break;
    case 'bass':
      notes = generateBassline(seed, BASE_KEY, project.scale, project.progression,
        track.generatorSettings.rhythm, track.generatorSettings.noteVariation, track.generatorSettings, project.progressionChords, harmony)
        .map((note, index) => ({ id: `bass-${index}`, midi: note.midi, startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity }));
      break;
    case 'chords':
      notes = generateChordPart(seed, BASE_KEY, project.scale, project.progression, track.generatorSettings, project.progressionChords, harmony)
        .flatMap((hit, hitIndex) => hit.pitches.map((midi, pitchIndex) => ({
          id: `chords-${hitIndex}-${pitchIndex}`, midi, startBeat: hit.startBeat + (hit.pitchOffsets?.[pitchIndex] ?? 0),
          durationBeats: hit.pitchDurations?.[pitchIndex] ?? hit.durationBeats, velocity: hit.velocity,
        })));
      break;
    case 'arpeggio':
      notes = generateArpeggio(seed, BASE_KEY, project.scale, project.progression, track.generatorSettings, project.progressionChords,
        melody, harmony, orderOffset)
        .map((note, index) => ({ id: `arpeggio-${index}`, midi: note.midi, startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity }));
      break;
    case 'drums':
      notes = generateDrumPart(seed, project.progression.length, track.generatorSettings, melody)
        .map((hit, index) => ({ id: `drums-${index}`, midi: hit.midi, startBeat: hit.startBeat,
          durationBeats: hit.durationBeats, velocity: hit.velocity }));
      break;
  }
  // Melody applies transpose before interval repair and length before mode conversion, as Java does.
  if (track.role === 'melody') return notes;
  const length = track.generatorSettings.noteLengthMultiplier ?? 100;
  const transpose = track.role === 'drums' ? 0 : track.generatorSettings.transpose ?? 0;
  // These role generators above already project into the five-field note contract.
  return notes.map((note) => ({ id: note.id, midi: Math.max(0, Math.min(127, note.midi + transpose)),
    startBeat: note.startBeat, durationBeats: note.durationBeats * length / 100, velocity: note.velocity }));
}

export function phraseForTrack(project: CompositionProject, track: CompositionTrack): readonly PhraseNote[] {
  const transform = (note: PhraseNote) => processManualPhrase([note], track.generatorSettings, track.role);
  if (track.editedPhrase !== undefined) return processManualPhrase(applyPhraseRangeEdits(track.editedPhrase, track.phraseEdits,
    project.progression.length * 4), track.generatorSettings, track.role);
  return applyPhraseRangeEdits(generateTrackPhrase(project, track), track.phraseEdits, project.progression.length * 4, undefined, transform);
}

/** Add the legacy BassPart octave interval as a quieter upper octave. */
export function withBassOctaveInterval(notes: readonly PhraseNote[], enabled: boolean): PhraseNote[] {
  if (!enabled) return [...notes];
  return [
    ...notes,
    ...notes.filter((note) => note.midi <= 115).map((note) => ({
      ...note,
      id: `${note.id}-octave`,
      midi: note.midi + 12,
      velocity: Math.max(1, note.velocity - 15),
    })),
  ];
}

export type PhraseSource = 'global-generated' | 'global-edited' | 'section-generated' | 'section-edited'
  | 'global-section-harmony' | 'global-section-rhythm' | 'global-section-harmony-rhythm';

export function trackPhraseSource(track: CompositionTrack, section?: ArrangementSection, project?: CompositionProject): PhraseSource {
  if (section?.trackPhrases?.[track.id] !== undefined) return 'section-edited';
  if (hasManualPhrase(track) && !section?.generatedTrackIds?.includes(track.id)) return 'global-edited';
  // Phrase ownership follows the arrangement indicator and note-track selection.
  // Section harmony/context still uses global track settings unless part overrides exist.
  const local = section && (Object.keys(section.trackPartOverrides?.[track.id] ?? {}).length > 0
    || Object.keys(section.rolePartOverrides?.[track.role] ?? {}).length > 0);
  if (local) return 'section-generated';
  if (!section || !project) return 'global-generated';
  const harmony = trackHasSectionHarmony(project, track, section), rhythm = trackHasSectionRhythm(project, track, section);
  return harmony && rhythm ? 'global-section-harmony-rhythm' : harmony ? 'global-section-harmony'
    : rhythm ? 'global-section-rhythm' : 'global-generated';
}

type ArrangedNote = PhraseNote & { readonly phraseSource?: PhraseSource; readonly sectionId?: string };
const halfFillBounds = Symbol('half fill bounds');
type LayoutNote = ArrangedNote & { readonly [halfFillBounds]?: FillWindow };
const trackLayouts = new WeakMap<CompositionProject, WeakMap<CompositionTrack, readonly ArrangedNote[]>>();

/** Detached notes for editing and callers that need their own metadata projection. */
export function layOutTrackPhrase(project: CompositionProject, track: CompositionTrack, includeSource = false, includeOwnership = false): (PhraseNote & { readonly phraseSource?: PhraseSource; readonly sectionId?: string })[] {
  return resolvedTrackPhrase(project, track).map(note => {
    const { phraseSource, sectionId, ...plain } = note;
    return { ...plain, ...(includeSource ? { phraseSource } : {}), ...(includeOwnership ? { sectionId } : {}) };
  });
}

/** Canonical frozen layout shared by resolved timelines without copying every note. */
export function resolvedTrackPhrase(project: CompositionProject, track: CompositionTrack): readonly ArrangedNote[] {
  let cache = trackLayouts.get(project);
  if (!cache) { cache = new WeakMap(); trackLayouts.set(project, cache); }
  let notes = cache.get(track);
  if (!notes) {
    const processed = processOutputNotes(project, track.id, track.role, buildTrackLayout(project, track, true));
    notes = project.humanization?.timingBeats || project.humanization?.durationBeats
      ? processed.flatMap(note => {
        const window = note[halfFillBounds];
        if (!window) return [note];
        const { [halfFillBounds]: _, ...plain } = note;
        return clipNotesToFill([plain], window);
      }) : processed;
    // Preserve baseline output draws; apply events only to the owning section's notes.
    let sectionStart = 0;
    for (const section of project.arrangement) {
      const window = resolvePreDropCut(track, section);
      if (window) {
        const absolute = { startBeat: sectionStart + window.startBeat, endBeat: sectionStart + window.endBeat };
        notes = notes.flatMap(note => note.sectionId === section.id ? applySectionCut([note], absolute) : [note]);
      }
      const drumFill = resolveDrumEndFill(track, section);
      const fill = drumFill ?? resolveArpEndFill(track, section);
      if (fill) {
        const owned: readonly ArrangedNote[] = notes.filter(note => note.sectionId === section.id);
        const filled: ArrangedNote[] = applyArpEndFill(owned, { startBeat: sectionStart + fill.startBeat, endBeat: sectionStart + fill.endBeat },
          BigInt(section.variations!.choices.find(choice => choice.trackId === track.id)?.seed ?? section.variations!.seed), drumFill ? track.id + '/drum' : track.id, fill.limit,
          drumFill ? notes.filter(note => note.sectionId !== section.id) : []);
        const first = notes.findIndex(note => note.sectionId === section.id);
        notes = notes.flatMap((note, index): ArrangedNote[] => note.sectionId !== section.id ? [note] : index === first ? filled : []);
      }
      const subdivision = resolveArpSubdivision(track, section);
      if (subdivision) {
        let slotStart = sectionStart;
        const slots = Array.from({ length: section.measures }, (_, i) => {
          const startBeat = slotStart;
          const durations = section.customDurations ?? [4];
          slotStart += durations[i % durations.length] * (section.context?.beatDurationMultiplier ?? 1);
          return { startBeat, endBeat: slotStart };
        });
        const owned = notes.filter(note => note.sectionId === section.id);
        const staged: ArrangedNote[] = applyArpSubdivision(owned,
          { startBeat: sectionStart + subdivision.startBeat, endBeat: sectionStart + subdivision.endBeat },
          slots, track.generatorSettings.hitsPerPattern ?? 8, track.id, notes.filter(note => note.sectionId !== section.id), subdivision.limit);
        const first = notes.findIndex(note => note.sectionId === section.id);
        notes = notes.flatMap((note, index): ArrangedNote[] => note.sectionId !== section.id ? [note] : index === first ? staged : []);
      }
      const ramp = resolveVelocityEnvelope(track, section);
      if (ramp) {
        const absolute = { startBeat: sectionStart + ramp.startBeat, endBeat: sectionStart + ramp.endBeat };
        notes = notes.map(note => note.sectionId === section.id ? applyVelocityEnvelope([note], absolute, ramp.amount, ramp.delta)[0] : note);
      }
      sectionStart += sectionDurationBeats(project, section);
    }
    for (const note of notes) Object.freeze(note);
    notes = Object.freeze(notes);
    cache.set(track, notes);
  }
  return notes;
}

function patternHarmony(harmony: GenerationHarmony): GenerationHarmony {
  // Fixed grids and duration lanes keep their four-beat source pattern.
  return harmony.durations?.some(duration => duration !== 4)
    ? { ...harmony, durations: harmony.durations.map(() => 4) } : harmony;
}

function usesActualChordDurations(track: CompositionTrack, durations: readonly number[]): boolean {
  const rhythm = track.generatorSettings.rhythm;
  return (track.role === 'bass' && rhythm === 'bass1'
    || track.role === 'melody' && rhythm !== 'durations' && !isMelodyGrid(rhythm))
    && durations.some(duration => duration !== 4);
}

function chordBoundaries(durations: readonly number[]): number[] {
  const beats = [0];
  for (const duration of durations) beats.push(beats.at(-1)! + duration);
  return beats;
}

function chordPatterns(notes: readonly PhraseNote[], chordCount: number, boundaries?: readonly number[]): PhraseNote[][] {
  const patterns = Array.from({ length: chordCount }, (): PhraseNote[] => []);
  for (const note of notes) {
    const chord = boundaries
      ? boundaries.findIndex(end => end > note.startBeat + 1e-9) - 1 : Math.floor(note.startBeat / 4);
    patterns[chord]?.push(note);
  }
  return patterns;
}

/** Repeat a generated chord's four-beat pattern at its original rate. */
function appendChordPattern(target: PhraseNote[], pattern: readonly PhraseNote[],
  sourceStart: number, targetStart: number, duration: number, repeats?: readonly (readonly PhraseNote[])[]): void {
  for (let offset = 0; offset < duration; offset += 4) {
    const pass = offset / 4;
    for (const note of repeats?.[pass - 1] ?? pattern) {
      const localBeat = offset + (note.startBeat - sourceStart);
      const remaining = duration - localBeat;
      if (duration !== 4 && remaining <= 1e-9) continue;
      target.push({ ...note, id: pass === 0 ? note.id : `${note.id}-repeat-${pass}`,
        startBeat: targetStart + offset + note.startBeat - sourceStart,
        // Keep ordinary four-beat articulations, including intentional overlapping sustains.
        durationBeats: duration === 4 ? note.durationBeats : Math.min(note.durationBeats, remaining) });
    }
  }
}

function buildTrackLayout(project: CompositionProject, track: CompositionTrack, includeSource: boolean): LayoutNote[] {
  const result: LayoutNote[] = [];
  const humanized = project.humanization?.timingBeats || project.humanization?.durationBeats;
  const transpose = track.role === 'drums' ? 0 : project.transposeSemitones ?? 0;
  // Finalize each detached section note once instead of separate ownership and transpose copies.
  const append = (notes: readonly LayoutNote[], phraseSource: PhraseSource, section: ArrangementSection, offset?: number): void => {
    const shift = sectionTranspose(section, track.role);
    for (const note of notes) {
      const placed: { -readonly [K in keyof LayoutNote]: LayoutNote[K] } = { ...note };
      if (includeSource) { placed.phraseSource = phraseSource; placed.sectionId = section.id; }
      if (offset !== undefined) placed.startBeat = offset + note.startBeat;
      if (shift !== 0) placed.midi = Math.max(0, Math.min(127, note.midi + shift));
      if (transpose !== 0) placed.midi = Math.max(0, Math.min(127, placed.midi + transpose));
      result.push(placed);
    }
  };
  let arrangementBeat = 0;
  for (const section of project.arrangement) {
    const sectionStart = arrangementBeat;
    const phraseSource = includeSource ? trackPhraseSource(track, section, project) : 'global-generated';
    const partEnters = shouldGenerateTrackInSection(section, track);
    const localPhrase = section.trackPhrases?.[track.id];
    if (localPhrase !== undefined) {
      const length = sectionGenerationHarmony(project, section, track.role).durations;
      const sectionLength = Array.from({ length: section.measures }, (_, index) => length[index % length.length]).reduce((sum, value) => sum + value, 0);
      if (partEnters) append(processManualPhrase(localPhrase,
        resolvePartTrack(track, section, project.melodyGlobal).generatorSettings, track.role), phraseSource, section, arrangementBeat);
      arrangementBeat += sectionLength;
      continue;
    }
    const manual = track.editedPhrase !== undefined && !section.generatedTrackIds?.includes(track.id);
    const resolved = resolvePartTrack(manual ? track : { ...track, editedPhrase: undefined } as CompositionTrack, section, project.melodyGlobal);
    // An explicit section-track swing takes precedence over the section-wide fallback.
    const effectiveTrack = section.context?.swingPercent === undefined || resolved.role === 'bass'
      || section.trackPartOverrides?.[track.id]?.swingPercent !== undefined ? resolved : {
      ...resolved, generatorSettings: { ...resolved.generatorSettings, swingPercent: section.context.swingPercent },
    } as CompositionTrack;
    const harmony = sectionGenerationHarmony(project, section, track.role);
    const progression = !manual ? harmony.degrees : project.progression;
    const patternProject = { ...sectionProject(project, section), progression, progressionChords: harmony.customChords };
    const durations = harmony.durations;
    const actualBeats = !manual && usesActualChordDurations(effectiveTrack, durations);
    const boundaries = actualBeats ? chordBoundaries(durations) : undefined;
    const generationHarmony = actualBeats ? harmony : patternHarmony(harmony);
    const melody = !manual && usesMelodyRhythm(effectiveTrack) ? sectionMelodyContext(project, section) : undefined;
    const fill = !manual && sectionHalfFill(effectiveTrack.generatorSettings, sectionStart, sectionStart + sectionDurationBeats(project, section));
    // Generate a complete harmony cycle, then apply the section's half once in song time.
    const patternTrack = fill ? { ...effectiveTrack, generatorSettings: { ...effectiveTrack.generatorSettings,
      chordSpanFill: 'ALL', fillFlip: false } } as CompositionTrack : effectiveTrack;
    const generated = !manual
      ? generatedTrackPhrase(patternProject, patternTrack, melody, generationHarmony, actualBeats)
      : manualNotesInSection(track.editedPhrase!, section, track.role);
    const expandedPhrase = effectiveTrack.role === 'bass' && !manual
      ? withBassOctaveInterval(generated, effectiveTrack.generatorSettings.octaveInterval) : generated;
    const patterns = manual ? undefined : chordPatterns(expandedPhrase, progression.length, boundaries);
    const melodyPatterns = (context: MelodyContext) => chordPatterns(
      generatedTrackPhrase(patternProject, patternTrack, context, generationHarmony), progression.length);
    const repeatPatterns = melody?.repeats?.map(melodyPatterns);
    const outsideFill = melody?.outsideFill;
    const outsidePatterns = outsideFill && melodyPatterns(outsideFill.context);
    const outsideRepeats = outsideFill?.context.repeats?.map(melodyPatterns);
    const outsideNotes: PhraseNote[] = [];
    const sectionNotes: PhraseNote[] = [];
    for (let measure = 0; measure < section.measures; measure++) {
      const duration = durations[measure % durations.length];
      if (partEnters) {
        const sourceMeasure = measure % progression.length;
        const sourceStartBeat = boundaries?.[sourceMeasure] ?? sourceMeasure * 4;
        if (patterns && boundaries) {
          for (const note of patterns[sourceMeasure]) {
            sectionNotes.push({ ...note, startBeat: arrangementBeat + (note.startBeat - sourceStartBeat) });
          }
        } else if (patterns) {
          appendChordPattern(sectionNotes, patterns[sourceMeasure], sourceStartBeat, arrangementBeat, duration,
            repeatPatterns?.map(pass => pass[sourceMeasure]));
          if (outsidePatterns) appendChordPattern(outsideNotes, outsidePatterns[sourceMeasure], sourceStartBeat, arrangementBeat, duration,
            outsideRepeats?.map(pass => pass[sourceMeasure]));
        } else {
          const sourceEndBeat = sourceStartBeat + 4;
          for (const note of expandedPhrase) {
            if (note.startBeat >= sourceStartBeat && note.startBeat < sourceEndBeat
              || sourceMeasure === 0 && note.startBeat < 0
              || measure === section.measures - 1 && note.startBeat >= sourceEndBeat && note.startBeat < sourceEndBeat + 4
              || sourceMeasure === progression.length - 1 && note.startBeat >= progression.length * 4) {
              sectionNotes.push({ ...note, startBeat: arrangementBeat + note.startBeat - sourceStartBeat });
            }
          }
        }
      }
      arrangementBeat += duration;
    }
    let placed = sectionNotes;
    if (outsideFill) {
      // Followers can still play over a silent melody grid when their pattern is inverted or uses ghosts.
      const clip = (notes: readonly PhraseNote[], window: FillWindow) => clipNotesToFill(notes,
        { startBeat: sectionStart + window.startBeat, endBeat: sectionStart + window.endBeat });
      placed = [...clip(sectionNotes, outsideFill.window.active), ...clip(outsideNotes, outsideFill.window.inactive)]
        .sort((left, right) => left.startBeat - right.startBeat);
    }
    const timed = !manual ? applyPartTiming(placed, effectiveTrack.generatorSettings) : placed;
    let filled: LayoutNote[] = fill ? clipNotesToFill(timed, fill.active) : timed;
    // Recheck generated notes after output humanization. Manual replacements carry no fill bounds.
    if (fill && humanized) filled = filled.map(note => ({ ...note, [halfFillBounds]: fill.active }));
    const edits = partEnters ? manualEditsInSection(track, section) : undefined;
    const timedNotes = edits?.length
      ? applyPhraseRangeEdits(filled.map(note => ({ ...note, startBeat: note.startBeat - sectionStart })),
        edits, arrangementBeat - sectionStart, sectionPhraseBounds(project, section),
          note => manual ? [note] : processManualPhrase([note], effectiveTrack.generatorSettings, track.role))
        .map(note => ({ ...note, startBeat: note.startBeat + sectionStart })) : filled;
    const adjusted = manual ? processManualPhrase(timedNotes, effectiveTrack.generatorSettings, track.role) : timedNotes;
    append(adjusted, phraseSource, section);
  }
  return result;
}

function manualNotesInSection(notes: readonly PhraseNote[], section: ArrangementSection | undefined, role: ArrangedPart): readonly PhraseNote[] {
  const mode = section?.manualPhraseMode;
  return mode && role !== 'drums' ? convertManualPhrase(notes, mode.from, mode.to) : notes;
}

function manualEditsInSection(track: CompositionTrack, section: ArrangementSection | undefined) {
  if (section?.generatedTrackIds?.includes(track.id)) return undefined;
  return section?.manualPhraseMode && track.role !== 'drums' ? track.phraseEdits?.map(edit => ({
    ...edit, notes: manualNotesInSection(edit.notes, section, track.role),
  })) : track.phraseEdits;
}

interface SectionMelodyContext extends MelodyContext {
  readonly repeats?: readonly MelodyContext[];
  readonly outsideFill?: {
    readonly window: NonNullable<ReturnType<typeof sectionHalfFill>>;
    readonly context: SectionMelodyContext;
  };
}

/** Retain the onsets in each four-beat window at the original sixteenth-note rate. */
function placedMelodyContext(notes: readonly PhraseNote[], durations: readonly number[],
  shift: (midi: number) => number): SectionMelodyContext {
  const starts = chordBoundaries(durations);
  const grid = (offset: number) => melodyContext(durations.flatMap((duration, index) => {
    // Keep the first pass for chords that never use this repeat.
    const pass = offset < duration ? offset : 0;
    const from = starts[index] + pass;
    const until = starts[index] + Math.min(pass + 4, duration);
    return notes.filter(note => note.startBeat >= from - 1e-9 && note.startBeat < until - 1e-9)
      .map(note => ({ midi: shift(note.midi), startBeat: index * 4 + Math.max(0, note.startBeat - from) }));
  }), durations.length);
  const first = grid(0);
  const repeatCount = Math.ceil(Math.max(...durations) / 4) - 1;
  return repeatCount === 0 ? first : {
    ...first, repeats: Array.from({ length: repeatCount }, (_, index) => grid((index + 1) * 4)),
  };
}

const melodyContexts = new WeakMap<CompositionProject, Map<ArrangementSection | undefined, SectionMelodyContext>>();

/** First melody by track order owns dependencies, including an absent arrangement cell.
 * No melody or generation disabled yields an empty grid. Manual melody owns its onset grid.
 * Generated melody publishes its pre-swing/pre-feedback onset map, as Java does.
 */
export function sectionMelodyContext(project: CompositionProject, section?: ArrangementSection): SectionMelodyContext {
  let cache = melodyContexts.get(project);
  if (!cache) { cache = new Map(); melodyContexts.set(project, cache); }
  const cached = cache.get(section);
  if (cached) return cached;
  const result = buildSectionMelodyContext(project, section);
  cache.set(section, result);
  return result;
}

function buildSectionMelodyContext(project: CompositionProject, section?: ArrangementSection): SectionMelodyContext {
  const harmony = section ? sectionGenerationHarmony(project, section, 'melody') : baseGenerationHarmony(project);
  const progression = harmony.degrees;
  const source = project.tracks.find(track => track.role === 'melody');
  if (!source) return { ...melodyContext([], progression.length), available: false };
  const edits = manualEditsInSection(source, section);
  const sectionEnd = section ? sectionDurationBeats(project, section) : progression.length * 4;
  const bounds = section ? sectionPhraseBounds(project, section) : undefined;
  const local = section?.trackPhrases?.[source.id];
  const effective = resolvePartTrack(source, section, project.melodyGlobal);
  const shift = sectionTranspose(section, 'melody');
  const shifted = (midi: number) => midi < 0 || !shift ? midi : Math.max(0, Math.min(127, midi + shift));
  if (local !== undefined) return placedMelodyContext(processManualPhrase(local, effective.generatorSettings, 'melody'),
    harmony.durations, shifted);
  if (source.editedPhrase !== undefined && !section?.generatedTrackIds?.includes(source.id)) {
    return melodyContext(processManualPhrase(applyPhraseRangeEdits(manualNotesInSection(source.editedPhrase, section, 'melody'), edits, sectionEnd, bounds),
      effective.generatorSettings, 'melody').map(note => ({ ...note, midi: shifted(note.midi) })), progression.length);
  }
  if (effective.role !== 'melody') {
    return { ...melodyContext([], progression.length), available: false };
  }
  const contextual = sectionProject(project, section);
  const seeded = withTrackSeed(contextual, source);
  const actualBeats = usesActualChordDurations(effective, harmony.durations);
  const fill = section && sectionHalfFill(effective.generatorSettings, 0, sectionEnd);
  const settings = fill ? { ...effective.generatorSettings, chordSpanFill: 'ALL' as const, fillFlip: false } : effective.generatorSettings;
  const fromOnsets = (notes: readonly Pick<PhraseNote, 'midi' | 'startBeat'>[]): SectionMelodyContext => {
    if (!actualBeats && !edits?.length) return melodyContext(notes.map(note => ({ ...note, midi: shifted(note.midi) })), progression.length);
    // Keep the generated pre-swing/pre-feedback grid outside edited windows.
    const generated = notes.map((note, index) => ({ ...note, id: `context-${index}`, durationBeats: .25, velocity: 90 }));
    const placed: PhraseNote[] = actualBeats ? generated : [];
    if (!actualBeats) {
      const patterns = chordPatterns(generated, progression.length);
      let start = 0;
      harmony.durations.forEach((duration, index) => {
        appendChordPattern(placed, patterns[index], index * 4, start, duration);
        start += duration;
      });
    }
    return placedMelodyContext(applyPhraseRangeEdits(placed, edits, sectionEnd, bounds), harmony.durations, shifted);
  };
  let result: SectionMelodyContext = melodyContext([], progression.length);
  generateMelody(BigInt(seeded.seed), BASE_KEY, contextual.scale, progression, settings, 0,
    notes => { result = fromOnsets(notes); }, project.melodyGlobal,
    harmony.customChords, actualBeats ? harmony : patternHarmony(harmony), undefined, actualBeats);
  return fill ? { ...result, outsideFill: { window: fill, context: fromOnsets([]) } } : result;
}
