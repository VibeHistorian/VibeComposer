import type { ArrangementSection } from '../project/arrangement.model';
import type { CompositionProject } from '../project/project.model';
import type { CompositionTrack } from '../project/track.model';
import type { PhraseNote } from './phrase-note';
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
import { mapChordNotes, sectionProject } from './section-context';
import { trackHasSectionHarmony } from './harmony-display';

/** Generate a phrase using the independent track's settings. */
export function generateTrackPhrase(project: CompositionProject, track: CompositionTrack, includeTiming = true, context?: MelodyContext,
  harmony: GenerationHarmony = baseGenerationHarmony(project)): PhraseNote[] {
  const seed = BigInt(withTrackSeed(project, track).seed);
  let notes: PhraseNote[];
  switch (track.role) {
    case 'melody':
      notes = generateMelody(seed, project.key, project.scale, project.progression, track.generatorSettings,
        harmony.melodySeedOffset ?? 0, undefined, project.melodyGlobal, project.progressionChords, harmony);
      break;
    case 'bass':
      notes = generateBassline(seed, project.key, project.scale, project.progression,
        track.generatorSettings.rhythm, track.generatorSettings.noteVariation, track.generatorSettings, project.progressionChords, harmony)
        .map((note, index) => ({ id: `bass-${index}`, midi: note.midi, startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity }));
      break;
    case 'chords':
      notes = generateChordPart(seed, project.key, project.scale, project.progression, track.generatorSettings, project.progressionChords, harmony)
        .flatMap((hit, hitIndex) => hit.pitches.map((midi, pitchIndex) => ({
          id: `chords-${hitIndex}-${pitchIndex}`, midi, startBeat: hit.startBeat + (hit.pitchOffsets?.[pitchIndex] ?? 0),
          durationBeats: hit.pitchDurations?.[pitchIndex] ?? hit.durationBeats, velocity: hit.velocity,
        })));
      break;
    case 'arpeggio':
      notes = generateArpeggio(seed, project.key, project.scale, project.progression, track.generatorSettings, project.progressionChords,
        context ?? (track.generatorSettings.rhythm === 'melody' ? sectionMelodyContext(project) : undefined), harmony,
        track.orderOffset ?? Math.max(1, project.tracks.filter(candidate => candidate.role === track.role).findIndex(candidate => candidate.id === track.id) + 1))
        .map((note, index) => ({ id: `arpeggio-${index}`, midi: note.midi, startBeat: note.startBeat,
          durationBeats: note.durationBeats, velocity: note.velocity }));
      break;
    case 'drums':
      notes = generateDrumPart(seed, project.progression.length, track.generatorSettings,
        context ?? (track.generatorSettings.rhythm === 'melody' ? sectionMelodyContext(project) : undefined))
        .map((hit, index) => ({ id: `drums-${index}`, midi: hit.midi, startBeat: hit.startBeat,
          durationBeats: hit.durationBeats, velocity: hit.velocity }));
      break;
  }
  // Melody applies transpose before interval repair and length before mode conversion, as Java does.
  if (track.role === 'melody') return includeTiming ? applyPartTiming(notes, track.generatorSettings) : notes;
  const length = track.generatorSettings.noteLengthMultiplier ?? 100;
  const transpose = track.role === 'drums' ? 0 : track.generatorSettings.transpose ?? 0;
  const prepared = notes.map((note) => ({ ...note, durationBeats: note.durationBeats * length / 100,
    midi: Math.max(0, Math.min(127, note.midi + transpose)) }));
  return includeTiming ? applyPartTiming(prepared, track.generatorSettings) : prepared;
}

export function phraseForTrack(project: CompositionProject, track: CompositionTrack): readonly PhraseNote[] {
  return track.editedPhrase ?? generateTrackPhrase(project, track);
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

export type PhraseSource = 'global-generated' | 'global-edited' | 'section-generated' | 'section-edited' | 'global-section-harmony';

export function trackPhraseSource(track: CompositionTrack, section?: ArrangementSection, project?: CompositionProject): PhraseSource {
  if (section?.trackPhrases?.[track.id] !== undefined) return 'section-edited';
  if (track.editedPhrase !== undefined && !section?.generatedTrackIds?.includes(track.id)) return 'global-edited';
  // Phrase ownership follows the arrangement indicator and note-track selection.
  // Section harmony/context still uses global track settings unless part overrides exist.
  const local = section && (Object.keys(section.trackPartOverrides?.[track.id] ?? {}).length > 0
    || Object.keys(section.rolePartOverrides?.[track.role] ?? {}).length > 0);
  if (local) return 'section-generated';
  return section && project && trackHasSectionHarmony(project, track, section) ? 'global-section-harmony' : 'global-generated';
}

type ArrangedNote = PhraseNote & { readonly phraseSource?: PhraseSource };
const trackLayouts = new WeakMap<CompositionProject, WeakMap<CompositionTrack, readonly ArrangedNote[]>>();

/** Share generated layouts across score, overview and playback for an immutable project.
 * Return copies so callers cannot change another consumer's cached note data.
 */
export function layOutTrackPhrase(project: CompositionProject, track: CompositionTrack, includeSource = false): (PhraseNote & { readonly phraseSource?: PhraseSource })[] {
  let cache = trackLayouts.get(project);
  if (!cache) { cache = new WeakMap(); trackLayouts.set(project, cache); }
  let notes = cache.get(track);
  if (!notes) {
    notes = buildTrackLayout(project, track, true);
    cache.set(track, notes);
  }
  return notes.map(note => {
    if (includeSource) return { ...note };
    const { phraseSource, ...plain } = note;
    return plain;
  });
}

function buildTrackLayout(project: CompositionProject, track: CompositionTrack, includeSource: boolean): ArrangedNote[] {
  const result: (PhraseNote & { readonly phraseSource?: PhraseSource })[] = [];
  let arrangementBeat = 0;
  for (const section of project.arrangement) {
    const phraseSource = includeSource ? trackPhraseSource(track, section, project) : 'global-generated';
    const partEnters = shouldGenerateTrackInSection(section, track);
    const localPhrase = section.trackPhrases?.[track.id];
    if (localPhrase !== undefined) {
      const length = sectionGenerationHarmony(project, section, track.role).durations;
      const sectionLength = Array.from({ length: section.measures }, (_, index) => length[index % length.length]).reduce((sum, value) => sum + value, 0);
      if (partEnters) result.push(...localPhrase.filter(note => note.startBeat < sectionLength).map(note => ({ ...note,
        ...(includeSource ? { phraseSource } : {}), startBeat: arrangementBeat + note.startBeat })));
      arrangementBeat += sectionLength;
      continue;
    }
    const manual = track.editedPhrase !== undefined && !section.generatedTrackIds?.includes(track.id);
    const resolved = resolvePartTrack(manual ? track : { ...track, editedPhrase: undefined } as CompositionTrack, section);
    const effectiveTrack = section.context?.swingPercent === undefined || resolved.role === 'bass' ? resolved : {
      ...resolved, generatorSettings: { ...resolved.generatorSettings, swingPercent: section.context.swingPercent },
    } as CompositionTrack;
    const harmony = sectionGenerationHarmony(project, section, track.role);
    const progression = !manual ? harmony.degrees : project.progression;
    const generated = !manual
      ? generateTrackPhrase({ ...sectionProject(project, section), progression, progressionChords: harmony.customChords }, effectiveTrack, false,
        (effectiveTrack.role === 'arpeggio' && effectiveTrack.generatorSettings.rhythm === 'melody')
          || (effectiveTrack.role === 'drums' && effectiveTrack.generatorSettings.rhythm === 'melody') ? sectionMelodyContext(project, section) : undefined, harmony) : track.editedPhrase!;
    const expandedPhrase = effectiveTrack.role === 'bass' && !manual
      ? withBassOctaveInterval(generated, effectiveTrack.generatorSettings.octaveInterval) : generated;
    const keyChange = !manual && track.role !== 'drums' ? section.context?.keyChange ?? 0 : 0;
    const normalizedPhrase = keyChange === 0 ? expandedPhrase : expandedPhrase.map(note => ({ ...note,
      midi: Math.max(0, Math.min(127, note.midi + keyChange)) }));
    const durations = harmony.durations;
    // Manual replacements keep their track-owned pitches and rhythms. Only arrangement slot
    // boundaries relocate their repeated bars; section settings never rewrite their notes.
    const sectionPhrase = !manual ? mapChordNotes(normalizedPhrase, durations) : normalizedPhrase;
    const sectionNotes: PhraseNote[] = [];
    for (let measure = 0; measure < section.measures; measure++) {
      if (partEnters) {
        const sourceMeasure = measure % progression.length;
        const sourceStartBeat = !manual ? durations.slice(0, sourceMeasure).reduce((sum, value) => sum + value, 0) : sourceMeasure * 4;
        const sourceEndBeat = sourceStartBeat + (!manual ? durations[sourceMeasure] : 4);
        for (const note of sectionPhrase) {
          if (note.startBeat >= sourceStartBeat && note.startBeat < sourceEndBeat) {
            sectionNotes.push({ ...note, startBeat: arrangementBeat + note.startBeat - sourceStartBeat });
          }
        }
      }
      arrangementBeat += durations[measure % durations.length];
    }
    const timedNotes = !manual ? applyPartTiming(sectionNotes, effectiveTrack.generatorSettings) : sectionNotes;
    result.push(...(includeSource ? timedNotes.map(note => ({ ...note, phraseSource })) : timedNotes));
  }
  const notes = result;
  if (track.role === 'drums') return notes;
  const transpose = project.transposeSemitones ?? 0;
  return transpose === 0 ? notes : notes.map((note) => ({
    ...note,
    midi: Math.max(0, Math.min(127, note.midi + transpose)),
  }));
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
  if (source.editedPhrase !== undefined && !section?.generatedTrackIds?.includes(source.id)) return melodyContext(source.editedPhrase, progression.length, project.melodyGlobal?.patternFlip);
  const effective = resolvePartTrack(source, section);
  if (effective.role !== 'melody') {
    return { ...melodyContext([], progression.length), available: false };
  }
  let result = melodyContext([], progression.length);
  const contextual = sectionProject(project, section);
  const seeded = withTrackSeed(contextual, source);
  generateMelody(BigInt(seeded.seed), contextual.key, contextual.scale, progression, effective.generatorSettings, harmony.melodySeedOffset ?? 0,
    notes => { result = melodyContext(notes.map(note => ({ ...note,
      midi: note.midi < 0 ? note.midi : Math.max(0, Math.min(127, note.midi + (section?.context?.keyChange ?? 0))) })),
      progression.length, project.melodyGlobal?.patternFlip); }, project.melodyGlobal,
    harmony.customChords, harmony);
  return result;
}
