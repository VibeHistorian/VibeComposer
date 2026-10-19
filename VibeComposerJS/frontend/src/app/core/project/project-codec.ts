import type { ArrangedPart, CompositionProject, TrackGenerationPolicies } from './project.model';
import { DEFAULT_MELODY_SETTINGS } from './project.model';
import { KEYS, validProgressionChords } from '../music/harmony';
import { isScaleMode } from '../music/scales';
import { decodeMelodyGlobal } from '../music/melody-global-settings';
import { decodeHarmonySettings } from '../music/harmony-voicing';
import { decodeTrackGenerationPolicy, validTrackGenerationMetadata } from '../music/track-generation';
import { decodeInstrumentPolicy, validInstrumentPolicies } from '../music/instrument-generation';
import { decodeMelody, decodeBass, decodeChords, decodeArpeggio, decodeDrums } from './project-part-decoder';
import { decodeTracks, decodeEditedPhrases, decodeMix } from './project-track-decoder';
import { decodeArrangement } from './project-arrangement-decoder';
import type { SectionTypeChanceSettings } from './section-type-chances';

/** Decode imported/session data, retaining the existing optional-field defaults and recovery rules. */
export function decodeProject(value: unknown, chances: SectionTypeChanceSettings, random: () => number): CompositionProject | undefined {
  if (!value || typeof value !== 'object') {
    return undefined;
  }

  const project = value as Partial<CompositionProject> & { bass?: unknown; chords?: unknown; arpeggio?: unknown; drums?: unknown; tracks?: unknown };
  const validBase = typeof project.name === 'string'
    && typeof project.key === 'string' && KEYS.includes(project.key)
    && (isScaleMode(project.scale))
    && (project.transposeSemitones === undefined || (Number.isInteger(project.transposeSemitones) && project.transposeSemitones >= -24 && project.transposeSemitones <= 24))
    && (project.randomizeTempoOnCompose === undefined || typeof project.randomizeTempoOnCompose === 'boolean')
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

  const melody = decodeMelody(project.melody === undefined ? DEFAULT_MELODY_SETTINGS : project.melody);
  const bass = decodeBass(project.bass);
  const chords = decodeChords(project.chords);
  const arpeggio = decodeArpeggio(project.arpeggio);
  const drums = decodeDrums(project.drums);
  const editedPhrases = decodeEditedPhrases(project.editedPhrases);
  const mix = decodeMix(project.mix);
  const tracks = decodeTracks(project.tracks);
  const arrangement = tracks ? decodeArrangement(project.arrangement, tracks, BigInt(project.seed!), chances, random) : undefined;
  if (!melody || !bass || !chords || !arpeggio || !drums || !arrangement || !editedPhrases || !mix || !tracks) {
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
    ...(project.randomizeTransposeOnCompose !== undefined ? { randomizeTransposeOnCompose: project.randomizeTransposeOnCompose } : {}),
    seed: project.seed!,
    progression: [...project.progression!],
    ...(project.progressionChords ? { progressionChords: project.progressionChords.map(chord => chord ? { ...chord } : null) } : {}),
    melody,
    ...(project.harmonySettings !== undefined ? { harmonySettings: decodeHarmonySettings(project.harmonySettings)! } : {}),
    ...(project.melodyGlobal !== undefined ? { melodyGlobal: decodeMelodyGlobal(project.melodyGlobal)! } : {}),
    bass,
    chords,
    arpeggio,
    drums,
    editedPhrases,
    mix,
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
    && (project.randomizeTransposeOnCompose === undefined || typeof project.randomizeTransposeOnCompose === 'boolean')
    && Number.isInteger(project.tempoBpm) && (project.tempoBpm ?? 0) >= 30 && (project.tempoBpm ?? 0) <= 240
    && typeof project.seed === 'string' && /^-?\d+$/.test(project.seed)
    && BigInt(project.seed) >= -(1n << 63n) && BigInt(project.seed) <= (1n << 63n) - 1n
    && Array.isArray(project.progression)
    && project.progression.length > 0
    && project.progression.length <= 32
    && project.progression.every((degree) => Number.isInteger(degree) && degree >= 1 && degree <= 7)
    && validProgressionChords(project.progressionChords, project.progression.length)
    && decodeMelody(project.melody) !== undefined
    && decodeBass(project.bass) !== undefined
    && decodeChords(project.chords) !== undefined
    && decodeArpeggio(project.arpeggio) !== undefined
    && decodeDrums(project.drums) !== undefined
    && tracks !== undefined
    && decodeArrangement(project.arrangement, tracks, BigInt(project.seed!), chances, random) !== undefined
    && decodeEditedPhrases(project.editedPhrases) !== undefined
    && decodeMix(project.mix) !== undefined
    && validTrackGenerationMetadata(project.trackGenerationPolicies, project.trackRerollCounts)
    && validInstrumentPolicies(project.instrumentGenerationPolicies)
    && (project.harmonySettings === undefined || decodeHarmonySettings(project.harmonySettings) !== undefined)
    && (project.melodyGlobal === undefined || decodeMelodyGlobal(project.melodyGlobal) !== undefined);
}
