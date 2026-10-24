import type { ArrangedPart } from '../music/part-role';
import type { CompositionProject } from './project.model';
import type { TrackGenerationPolicies } from '../music/generation-policy.model';
import { KEYS, validProgressionChords } from '../music/harmony';
import { isScaleMode } from '../music/scales';
import { decodeMelodyGlobal } from '../music/melody-global-settings';
import { decodeHarmonySettings } from '../music/harmony-voicing';
import { decodeTrackGenerationPolicy, validTrackGenerationMetadata } from '../music/track-generation';
import { decodeInstrumentPolicy, validInstrumentPolicies } from '../music/instrument-generation';
import { decodeTracks, decodeEditedPhrases } from './project-track-decoder';
import { decodeArrangement } from './project-arrangement-decoder';
import { decodeStoredArrangement } from './project-storage';
import type { SectionTypeChanceSettings } from './section-type-chances';

/** Decode imported/session data, retaining the existing optional-field defaults and recovery rules. */
export function decodeProject(value: unknown, chances: SectionTypeChanceSettings, random: () => number): CompositionProject | undefined {
  if (!value || typeof value !== 'object') {
    return undefined;
  }

  const project = value as Partial<CompositionProject>;
  const validBase = typeof project.name === 'string'
    && typeof project.key === 'string' && KEYS.includes(project.key)
    && (isScaleMode(project.scale))
    && (project.transposeSemitones === undefined || (Number.isInteger(project.transposeSemitones) && project.transposeSemitones >= -24 && project.transposeSemitones <= 24))
    && (project.randomizeTempoOnCompose === undefined || typeof project.randomizeTempoOnCompose === 'boolean')
    && (project.randomizeHarmonyOnCompose === undefined || typeof project.randomizeHarmonyOnCompose === 'boolean')
    && (project.randomizeArrangementOnCompose === undefined || typeof project.randomizeArrangementOnCompose === 'boolean')
    && (project.arrangementPreview === undefined || typeof project.arrangementPreview === 'boolean')
    && (project.arrangementManual === undefined || typeof project.arrangementManual === 'boolean')
    && (project.randomizeTransposeOnCompose === undefined || typeof project.randomizeTransposeOnCompose === 'boolean')
    && Number.isInteger(project.tempoBpm) && (project.tempoBpm ?? 0) >= 30 && (project.tempoBpm ?? 0) <= 240
    && typeof project.seed === 'string' && /^-?\d+$/.test(project.seed)
    && BigInt(project.seed) >= -(1n << 63n) && BigInt(project.seed) <= (1n << 63n) - 1n
    && Array.isArray(project.progression)
    && project.progression.length > 0 && project.progression.length <= 32
    && project.progression.every((degree) => Number.isInteger(degree) && degree >= 1 && degree <= 7)
    && validProgressionChords(project.progressionChords, project.progression.length);
  if (!validBase || !validTrackGenerationMetadata(project.trackGenerationPolicies, project.trackRerollCounts)
    || !validInstrumentPolicies(project.instrumentGenerationPolicies)
    || (project.melodyGlobal !== undefined && !decodeMelodyGlobal(project.melodyGlobal))
    || (project.harmonySettings !== undefined && !decodeHarmonySettings(project.harmonySettings))) {
    return undefined;
  }

  const editedPhrases = decodeEditedPhrases(project.editedPhrases);
  const tracks = decodeTracks(project.tracks);
  const arrangement = tracks ? decodeArrangement(decodeStoredArrangement(project.arrangement, tracks), tracks, BigInt(project.seed!), chances, random) : undefined;
  if (!arrangement || !editedPhrases || !tracks) {
    return undefined;
  }
  const trackIds = new Set(tracks.map((track) => track.id));
  if (arrangement.some((section) => Object.keys(section.trackPresence).some((trackId) => !trackIds.has(trackId)))) return undefined;
  return {
    schemaVersion: 12,
    name: project.name!,
    key: project.key!,
    scale: project.scale!,
    transposeSemitones: project.transposeSemitones ?? 0,
    tempoBpm: project.tempoBpm!,
    ...(project.randomizeTempoOnCompose !== undefined ? { randomizeTempoOnCompose: project.randomizeTempoOnCompose } : {}),
    ...(project.randomizeHarmonyOnCompose !== undefined ? { randomizeHarmonyOnCompose: project.randomizeHarmonyOnCompose } : {}),
    ...(project.randomizeTransposeOnCompose !== undefined ? { randomizeTransposeOnCompose: project.randomizeTransposeOnCompose } : {}),
    seed: project.seed!,
    ...(project.randomizeArrangementOnCompose !== undefined ? { randomizeArrangementOnCompose: project.randomizeArrangementOnCompose } : {}),
    ...(project.arrangementPreview !== undefined ? { arrangementPreview: project.arrangementPreview } : {}),
    ...(project.arrangementManual !== undefined ? { arrangementManual: project.arrangementManual } : {}),
    progression: [...project.progression!],
    ...(project.progressionChords ? { progressionChords: project.progressionChords.map(chord => chord ? { ...chord } : null) } : {}),
    ...(project.harmonySettings !== undefined ? { harmonySettings: decodeHarmonySettings(project.harmonySettings)! } : {}),
    ...(project.melodyGlobal !== undefined ? { melodyGlobal: decodeMelodyGlobal(project.melodyGlobal)! } : {}),
    editedPhrases,
    tracks,
    arrangement,
    ...(project.trackGenerationPolicies !== undefined ? { trackGenerationPolicies: Object.fromEntries(
      Object.entries(project.trackGenerationPolicies).map(([role, policy]) => [role, decodeTrackGenerationPolicy(role as ArrangedPart, policy)!])
    ) as Partial<TrackGenerationPolicies> } : {}),
    ...(project.trackRerollCounts !== undefined ? { trackRerollCounts: { ...project.trackRerollCounts } } : {}),
    ...(project.instrumentGenerationPolicies !== undefined ? { instrumentGenerationPolicies: Object.fromEntries(
      Object.entries(project.instrumentGenerationPolicies).map(([role, policy]) => [role, decodeInstrumentPolicy(role as ArrangedPart, policy)!])
    ) } : {}),
  };
}

/** Validate a complete command candidate without normalizing it or adding import defaults. */
export function isProject(value: unknown, chances: SectionTypeChanceSettings, random: () => number): value is CompositionProject {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const project = value as Partial<CompositionProject>;
  const tracks = decodeTracks(project.tracks);
  return typeof project.name === 'string'
    && typeof project.key === 'string' && KEYS.includes(project.key)
    && (isScaleMode(project.scale))
    && (project.transposeSemitones === undefined || (Number.isInteger(project.transposeSemitones) && project.transposeSemitones >= -24 && project.transposeSemitones <= 24))
    && (project.randomizeTempoOnCompose === undefined || typeof project.randomizeTempoOnCompose === 'boolean')
    && (project.randomizeHarmonyOnCompose === undefined || typeof project.randomizeHarmonyOnCompose === 'boolean')
    && (project.randomizeArrangementOnCompose === undefined || typeof project.randomizeArrangementOnCompose === 'boolean')
    && (project.arrangementPreview === undefined || typeof project.arrangementPreview === 'boolean')
    && (project.arrangementManual === undefined || typeof project.arrangementManual === 'boolean')
    && (project.randomizeTransposeOnCompose === undefined || typeof project.randomizeTransposeOnCompose === 'boolean')
    && Number.isInteger(project.tempoBpm) && (project.tempoBpm ?? 0) >= 30 && (project.tempoBpm ?? 0) <= 240
    && typeof project.seed === 'string' && /^-?\d+$/.test(project.seed)
    && BigInt(project.seed) >= -(1n << 63n) && BigInt(project.seed) <= (1n << 63n) - 1n
    && Array.isArray(project.progression)
    && project.progression.length > 0
    && project.progression.length <= 32
    && project.progression.every((degree) => Number.isInteger(degree) && degree >= 1 && degree <= 7)
    && validProgressionChords(project.progressionChords, project.progression.length)
    && tracks !== undefined
    && decodeArrangement(project.arrangement, tracks, BigInt(project.seed!), chances, random) !== undefined
    && decodeEditedPhrases(project.editedPhrases) !== undefined
    && validTrackGenerationMetadata(project.trackGenerationPolicies, project.trackRerollCounts)
    && validInstrumentPolicies(project.instrumentGenerationPolicies)
    && (project.harmonySettings === undefined || decodeHarmonySettings(project.harmonySettings) !== undefined)
    && (project.melodyGlobal === undefined || decodeMelodyGlobal(project.melodyGlobal) !== undefined);
}
