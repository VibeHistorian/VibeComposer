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
import { baseGenerationHarmony, sectionGenerationHarmony, withTrackSeed } from './project-harmony';
import type { GenerationHarmony } from './harmony-voicing';
import { chordGridBeat, mapChordBeat, mapChordNotes, sectionDurationBeats, sectionProject, sectionTranspose } from './section-context';
import { trackHasSectionHarmony, trackHasSectionRhythm } from './harmony-display';
import { applyPhraseRangeEdits, hasManualPhrase } from './phrase-range-edit';
import { sectionPhraseBounds } from './phrase-bounds';

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
    case 'bass': return [harmony.roots, harmony.soundingRoots];
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
  harmony: GenerationHarmony): readonly PhraseNote[] {
  const { role, generatorSettings } = track;
  const seed = BigInt(withTrackSeed(project, track).seed);
  const melody = usesMelodyRhythm(track) ? context ?? sectionMelodyContext(project) : undefined;
  const orderOffset = role === 'arpeggio' ? track.orderOffset
    ?? Math.max(1, project.tracks.filter(candidate => candidate.role === role).findIndex(candidate => candidate.id === track.id) + 1) : undefined;
  const harmonyInputs = role === 'drums' ? project.progression.length
    : [project.scale, project.progression, project.progressionChords, generationHarmonyInputs(role, harmony)];
  const key = JSON.stringify([role, seed.toString(), generatorSettings, harmonyInputs,
    role === 'melody' ? project.melodyGlobal : undefined, melody?.pitches, orderOffset], generationKeyValue);
  const cached = generatedPhrases.get(key);
  if (cached) {
    generatedPhrases.delete(key);
    generatedPhrases.set(key, cached);
    return cached;
  }

  const notes = generateUntimedTrackPhrase(project, track, seed, melody, harmony, orderOffset);
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
  harmony: GenerationHarmony, orderOffset: number | undefined): PhraseNote[] {
  let notes: PhraseNote[];
  switch (track.role) {
    case 'melody':
      notes = generateMelody(seed, BASE_KEY, project.scale, project.progression, track.generatorSettings,
        0, undefined, project.melodyGlobal, project.progressionChords, harmony);
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
    notes = processOutputNotes(project, track.id, track.role, buildTrackLayout(project, track, true));
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

function buildTrackLayout(project: CompositionProject, track: CompositionTrack, includeSource: boolean): ArrangedNote[] {
  const result: ArrangedNote[] = [];
  const transpose = track.role === 'drums' ? 0 : project.transposeSemitones ?? 0;
  // Finalize each detached section note once instead of separate ownership and transpose copies.
  const append = (notes: readonly PhraseNote[], phraseSource: PhraseSource, section: ArrangementSection, offset?: number): void => {
    const shift = sectionTranspose(section, track.role);
    for (const note of notes) {
      const placed: { -readonly [K in keyof ArrangedNote]: ArrangedNote[K] } = { ...note };
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
    const generated = !manual
      ? generatedTrackPhrase({ ...sectionProject(project, section), progression, progressionChords: harmony.customChords }, effectiveTrack,
        usesMelodyRhythm(effectiveTrack) ? sectionMelodyContext(project, section) : undefined, harmony)
      : manualNotesInSection(track.editedPhrase!, section, track.role);
    const expandedPhrase = effectiveTrack.role === 'bass' && !manual
      ? withBassOctaveInterval(generated, effectiveTrack.generatorSettings.octaveInterval) : generated;
    const durations = harmony.durations;
    const sectionPhrase = !manual ? mapChordNotes(expandedPhrase, durations) : expandedPhrase;
    const sectionNotes: PhraseNote[] = [];
    for (let measure = 0; measure < section.measures; measure++) {
      if (partEnters) {
        const sourceMeasure = measure % progression.length;
        const sourceStartBeat = !manual ? durations.slice(0, sourceMeasure).reduce((sum, value) => sum + value, 0) : sourceMeasure * 4;
        const sourceEndBeat = sourceStartBeat + (!manual ? durations[sourceMeasure] : 4);
        for (const note of sectionPhrase) {
          if (note.startBeat >= sourceStartBeat && note.startBeat < sourceEndBeat
            || manual && sourceMeasure === 0 && note.startBeat < 0
            || manual && measure === section.measures - 1 && note.startBeat >= sourceEndBeat && note.startBeat < sourceEndBeat + 4
            || manual && sourceMeasure === progression.length - 1 && note.startBeat >= progression.length * 4) {
            const placed = { ...note };
            placed.startBeat = arrangementBeat + note.startBeat - sourceStartBeat;
            sectionNotes.push(placed);
          }
        }
      }
      arrangementBeat += durations[measure % durations.length];
    }
    const timed = !manual ? applyPartTiming(sectionNotes, effectiveTrack.generatorSettings) : sectionNotes;
    const edits = partEnters ? manualEditsInSection(track, section) : undefined;
    const timedNotes = edits?.length
      ? applyPhraseRangeEdits(timed.map(note => ({ ...note, startBeat: note.startBeat - sectionStart })),
        edits, arrangementBeat - sectionStart, sectionPhraseBounds(project, section),
          note => manual ? [note] : processManualPhrase([note], effectiveTrack.generatorSettings, track.role))
        .map(note => ({ ...note, startBeat: note.startBeat + sectionStart })) : timed;
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

const melodyContexts = new WeakMap<CompositionProject, Map<ArrangementSection | undefined, MelodyContext>>();

/** First melody by track order owns dependencies, including an absent arrangement cell.
 * No melody or generation disabled yields an empty grid. Manual melody owns its onset grid.
 * Generated melody publishes its pre-swing/pre-feedback onset map, as Java does.
 */
export function sectionMelodyContext(project: CompositionProject, section?: ArrangementSection): MelodyContext {
  let cache = melodyContexts.get(project);
  if (!cache) { cache = new Map(); melodyContexts.set(project, cache); }
  const cached = cache.get(section);
  if (cached) return cached;
  const result = buildSectionMelodyContext(project, section);
  cache.set(section, result);
  return result;
}

function buildSectionMelodyContext(project: CompositionProject, section?: ArrangementSection): MelodyContext {
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
  if (local !== undefined) return melodyContext(processManualPhrase(local, effective.generatorSettings, 'melody').map(note => ({ ...note, midi: shifted(note.midi),
    startBeat: chordGridBeat(note.startBeat, harmony.durations) })), progression.length);
  if (source.editedPhrase !== undefined && !section?.generatedTrackIds?.includes(source.id)) {
    return melodyContext(processManualPhrase(applyPhraseRangeEdits(manualNotesInSection(source.editedPhrase, section, 'melody'), edits, sectionEnd, bounds),
      effective.generatorSettings, 'melody').map(note => ({ ...note, midi: shifted(note.midi) })), progression.length);
  }
  if (effective.role !== 'melody') {
    return { ...melodyContext([], progression.length), available: false };
  }
  let result = melodyContext([], progression.length);
  const contextual = sectionProject(project, section);
  const seeded = withTrackSeed(contextual, source);
  generateMelody(BigInt(seeded.seed), BASE_KEY, contextual.scale, progression, effective.generatorSettings, 0,
    notes => {
      const generated = notes.map((note, index) => ({ ...note, id: `context-${index}`, durationBeats: .25, velocity: 90 }));
      // Keep the generated pre-swing/pre-feedback grid outside edited windows.
      const resolved = edits?.length ? applyPhraseRangeEdits(generated.map(note => ({ ...note,
        startBeat: mapChordBeat(note.startBeat, harmony.durations) })), edits, sectionEnd, bounds)
        .map(note => ({ ...note, startBeat: chordGridBeat(note.startBeat, harmony.durations) })) : generated;
      result = melodyContext(resolved.map(note => ({ ...note, midi: shifted(note.midi) })), progression.length);
    }, project.melodyGlobal,
    harmony.customChords, harmony);
  return result;
}
