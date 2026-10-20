import type { ArrangedPart, PartSettingsPatch, SectionType } from './project.model';
import { ARRANGED_PARTS, SECTION_TYPES } from './project.model';
import { KEYS, validProgressionChords } from '../music/harmony';
import { isScaleMode } from '../music/scales';
import { validVolumePercent } from '../audio/mix-values';
import { PART_CONTROLS, decodePartPatch, validVelocityRange } from '../music/part-settings';
import { isDrumPitch } from '../music/drum-instruments';
import { decodeMelodyGlobal } from '../music/melody-global-settings';
import { decodeHarmonySettings } from '../music/harmony-voicing';
import { DEFAULT_TRACK_POLICIES, decodeTrackGenerationPolicy, validTrackGenerationMetadata } from '../music/track-generation';
import { DEFAULT_INSTRUMENT_POLICIES, decodeInstrumentPolicy, validInstrumentPolicies } from '../music/instrument-generation';
import { decodeTracks, isPhrase } from './project-track-decoder';
import { decodeArrangement, MAX_ARRANGEMENT_SECTIONS, MAX_ARRANGEMENT_MEASURES } from './project-arrangement-decoder';
import type { SectionTypeChanceSettings } from './section-type-chances';
import { decodeStoredArrangement, decodeStoredTrackPresence } from './project-storage';

/** Gather diagnostics only after rejection; these messages do not define acceptance rules. */
export function projectLoadIssues(value: unknown, chances: SectionTypeChanceSettings, random: () => number): readonly string[] {
  const issues: string[] = [];
  const object = (raw: unknown, path: string, required: readonly string[], allowed: readonly string[]): Record<string, unknown> | undefined => {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
      issues.push(`${path}: expected an object`);
      return undefined;
    }
    const fields = raw as Record<string, unknown>;
    for (const key of required) if (fields[key] === undefined) issues.push(`${path}.${key}: missing required property`);
    for (const key of Object.keys(fields)) if (!allowed.includes(key)) issues.push(`${path}.${key}: unknown property encountered`);
    return fields;
  };
  const check = (path: string, valid: boolean, expected: string) => {
    if (!valid) issues.push(`${path}: ${expected}`);
  };
  const settings = (role: ArrangedPart, raw: unknown, path: string, patch = false) => {
    const required = patch ? [] : role === 'melody' ? ['algorithm'] : role === 'bass' ? ['rhythm', 'noteVariation']
      : role === 'chords' ? ['rhythm', 'voicing', 'noteLengthPercent'] : role === 'arpeggio' ? ['pattern', 'rate', 'octaves']
      : ['pitch', 'rhythm', 'swingPercent'];
    const allowed = PART_CONTROLS[role].map(control => control.key as string);
    if (role === 'arpeggio') allowed.push('rate');
    if (!patch && role === 'melody') allowed.push('algorithm');
    if (!patch && role === 'drums') allowed.push('pitch');
    const fields = object(raw, path, required, allowed);
    if (!fields) return;
    for (const [key, field] of Object.entries(fields)) {
      if (!allowed.includes(key)) continue;
      if (key === 'algorithm') check(`${path}.${key}`, field === 'block', 'expected "block"');
      else if (key === 'pitch') check(`${path}.${key}`, isDrumPitch(field), 'expected a supported percussion MIDI pitch');
      else check(`${path}.${key}`, !!decodePartPatch(role, { [key]: field }), 'invalid value for this generator setting');
    }
    check(path, validVelocityRange(fields as PartSettingsPatch, role), 'velocityMin must not exceed velocityMax');
  };
  const mix = (raw: unknown, path: string) => {
    const keys = ['program', 'volumePercent', 'panPercent', 'muted', 'solo'];
    const fields = object(raw, path, keys, keys);
    if (!fields) return;
    check(`${path}.program`, Number.isInteger(fields['program']) && (fields['program'] as number) >= 0 && (fields['program'] as number) <= 127, 'expected an integer from 0 to 127');
    check(`${path}.volumePercent`, validVolumePercent(fields['volumePercent']), 'invalid volume percentage');
    check(`${path}.panPercent`, Number.isInteger(fields['panPercent']) && Math.abs(fields['panPercent'] as number) <= 100, 'expected an integer from -100 to 100');
    for (const key of ['muted', 'solo']) check(`${path}.${key}`, typeof fields[key] === 'boolean', 'expected a boolean');
  };
  const required = ['name', 'key', 'scale', 'tempoBpm', 'seed', 'progression', 'editedPhrases', 'tracks', 'arrangement'];
  const project = object(value, 'project', required, [...required, 'schemaVersion', 'transposeSemitones', 'progressionChords',
    'melodyGlobal', 'harmonySettings', 'trackGenerationPolicies', 'trackRerollCounts', 'instrumentGenerationPolicies']);
  if (project) {
    check('project.name', typeof project['name'] === 'string', 'expected a string');
    check('project.key', KEYS.includes(project['key'] as string), `expected one of ${KEYS.join(', ')}`);
    check('project.scale', isScaleMode(project['scale']), 'expected a supported scale/mode');
    check('project.tempoBpm', Number.isInteger(project['tempoBpm']) && (project['tempoBpm'] as number) >= 30 && (project['tempoBpm'] as number) <= 240, 'expected an integer from 30 to 240');
    const seed = project['seed'];
    check('project.seed', typeof seed === 'string' && /^-?\d+$/.test(seed) && BigInt(seed) >= -(1n << 63n) && BigInt(seed) <= (1n << 63n) - 1n, 'expected a decimal string within the signed 64-bit seed range');
    const progression = project['progression'];
    check('project.progression', Array.isArray(progression) && progression.length > 0 && progression.length <= 32 && progression.every(degree => Number.isInteger(degree) && degree >= 1 && degree <= 7), 'expected 1–32 chord degrees, each an integer from 1 to 7');
    if (project['transposeSemitones'] !== undefined) check('project.transposeSemitones', Number.isInteger(project['transposeSemitones']) && Math.abs(project['transposeSemitones'] as number) <= 24, 'expected an integer from -24 to 24');
    if (Array.isArray(progression)) check('project.progressionChords', !!validProgressionChords(project['progressionChords'], progression.length), 'invalid chords or length does not match progression');
    const tracks = project['tracks'];
    check('project.tracks', Array.isArray(tracks) && tracks.length <= 64, 'expected an array of at most 64 tracks');
    if (Array.isArray(tracks)) tracks.forEach((raw, index) => {
      const path = `project.tracks[${index}]`;
      const keys = ['id', 'role', 'name', 'midiChannel', 'mix', 'generatorSettings'];
      const track = object(raw, path, keys, [...keys, 'editedPhrase', 'rerollLocked', 'instrumentLocked', 'instrumentPool', 'orderOffset']);
      if (!track) return;
      mix(track['mix'], `${path}.mix`);
      const role = track['role'] as ArrangedPart;
      check(`${path}.role`, ARRANGED_PARTS.includes(role), `expected one of ${ARRANGED_PARTS.join(', ')}`);
      check(`${path}.id`, typeof track['id'] === 'string' && /^[a-zA-Z0-9_-]{1,64}$/.test(track['id']), 'expected 1–64 letters, digits, underscores, or hyphens');
      check(`${path}.name`, typeof track['name'] === 'string' && !!track['name'].trim() && track['name'].length <= 48, 'expected a nonempty name of at most 48 characters');
      check(`${path}.midiChannel`, Number.isInteger(track['midiChannel']) && (track['midiChannel'] as number) >= 1 && (track['midiChannel'] as number) <= 16 && (role !== 'drums' || track['midiChannel'] === 10), 'expected channel 1–16 (10 for drums)');
      if (ARRANGED_PARTS.includes(role)) settings(role, track['generatorSettings'], `${path}.generatorSettings`);
      check(path, !!decodeTracks([raw]), 'invalid track identity, name, channel, locks, instrument pool, settings, or phrase');
      check(`${path}.id`, !tracks.slice(0, index).some(previous => previous?.id === track['id']), 'duplicate track ID');
    });
    const phrases = object(project['editedPhrases'], 'project.editedPhrases', [], ARRANGED_PARTS);
    if (phrases) for (const [role, notes] of Object.entries(phrases)) check(`project.editedPhrases.${role}`, isPhrase(notes), 'invalid phrase: check note IDs, MIDI pitches, beat bounds, durations, and velocities');
    for (const [key, valid] of [
      ['trackGenerationPolicies', validTrackGenerationMetadata(project['trackGenerationPolicies'], undefined)],
      ['trackRerollCounts', validTrackGenerationMetadata(undefined, project['trackRerollCounts'])],
      ['instrumentGenerationPolicies', validInstrumentPolicies(project['instrumentGenerationPolicies'])],
      ['melodyGlobal', project['melodyGlobal'] === undefined || !!decodeMelodyGlobal(project['melodyGlobal'])],
      ['harmonySettings', project['harmonySettings'] === undefined || !!decodeHarmonySettings(project['harmonySettings'])],
    ] as const) check(`project.${key}`, valid, 'invalid settings, property names, or values');
    for (const key of ['trackGenerationPolicies', 'trackRerollCounts', 'instrumentGenerationPolicies']) {
      if (project[key] === undefined) continue;
      const policies = object(project[key], `project.${key}`, [], ARRANGED_PARTS);
      if (!policies || key === 'trackRerollCounts') continue;
      for (const [role, policy] of Object.entries(policies)) {
        if (!ARRANGED_PARTS.includes(role as ArrangedPart)) continue;
        const defaults = key === 'trackGenerationPolicies' ? DEFAULT_TRACK_POLICIES[role as ArrangedPart] : DEFAULT_INSTRUMENT_POLICIES[role as ArrangedPart];
        const keys = Object.keys(defaults);
        const validPolicy = (candidate: unknown) => key === 'trackGenerationPolicies'
          ? !!decodeTrackGenerationPolicy(role as ArrangedPart, candidate) : !!decodeInstrumentPolicy(role as ArrangedPart, candidate);
        const requiredKeys = keys.filter(field => !validPolicy({ ...defaults, [field]: undefined }));
        const fields = object(policy, `project.${key}.${role}`, requiredKeys, keys);
        if (!fields) continue;
        for (const field of keys) {
          const candidate = { ...defaults, [field]: fields[field] };
          check(`project.${key}.${role}.${field}`, validPolicy(candidate), 'invalid policy value');
        }
      }
    }
    const arrangement = project['arrangement'];
    check('project.arrangement', Array.isArray(arrangement) && arrangement.length >= 1 && arrangement.length <= MAX_ARRANGEMENT_SECTIONS, `expected 1–${MAX_ARRANGEMENT_SECTIONS} sections`);
    if (Array.isArray(arrangement)) arrangement.forEach((raw, index) => {
      const path = `project.arrangement[${index}]`;
      const section = object(raw, path, ['id', 'type', 'measures'], ['id', 'type', 'measures', 'context', 'customChords', 'customDurations', 'chordDegrees',
        'useMelodyHarmony', 'swapMelody', 'trackPresence', 'rolePartOverrides', 'trackPartOverrides', 'trackPhrases', 'generatedTrackIds', 'parts', 'partChances', 'trackParts', 'trackPartChances']);
      if (!section) return;
      check(`${path}.type`, SECTION_TYPES.includes(section['type'] as SectionType), `expected one of ${SECTION_TYPES.join(', ')}`);
      check(`${path}.measures`, Number.isInteger(section['measures']) && (section['measures'] as number) >= 1 && (section['measures'] as number) <= 32, 'expected an integer from 1 to 32');
      for (const [role, patch] of Object.entries(section['rolePartOverrides'] ?? {})) if (ARRANGED_PARTS.includes(role as ArrangedPart)) settings(role as ArrangedPart, patch, `${path}.rolePartOverrides.${role}`, true);
      const decodedTracks = decodeTracks(tracks);
      if (decodedTracks) {
        if (section['trackPresence'] !== undefined) check(`${path}.trackPresence`,
          decodeStoredTrackPresence(section['trackPresence'], decodedTracks) !== undefined,
          'expected an ID-to-boolean map or role arrays with one boolean per track, in track order');
        for (const [id, patch] of Object.entries(section['trackPartOverrides'] ?? {})) {
          const track = decodedTracks.find(track => track.id === id);
          if (track) settings(track.role, patch, `${path}.trackPartOverrides.${id}`, true);
        }
        check(path, !!decodeArrangement(decodeStoredArrangement([raw], decodedTracks), decodedTracks, 0n, chances, random), 'invalid section identity, type, measures, harmony, track references, overrides, or phrases');
      }
      check(`${path}.id`, !arrangement.slice(0, index).some(previous => previous?.id === section['id']), 'duplicate section ID');
    });
    if (Array.isArray(arrangement)) check('project.arrangement', arrangement.reduce((total, section) => total + (section?.measures ?? 0), 0) <= MAX_ARRANGEMENT_MEASURES, `total length must not exceed ${MAX_ARRANGEMENT_MEASURES} measures`);
  }
  return issues.length ? issues : ['Project structure or values failed validation.'];
}
