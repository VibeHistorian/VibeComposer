import { type ArrangedPart, ARRANGED_PARTS } from '../music/part-role';
import { type ArrangementSection, type SectionType, SECTION_TYPES } from './arrangement.model';
import type { CompositionTrack } from './track.model';
import type { PartSettingsPatch } from '../music/part-settings.model';
import type { PhraseNote } from '../music/phrase-note';
import { DEFAULT_SECTION_TYPE_CHANCES } from './section-type-chances';
import type { SectionTypeChanceSettings } from './section-type-chances';
import { generateTrackPresence, legacyTrackPresence } from '../music/arrangement-generator';
import { decodePartPatch, resolvePartTrack, validVelocityRange } from '../music/part-settings';
import { validSectionHarmony } from '../music/section-context';
import { isPhrase } from './project-track-decoder';

export const MAX_ARRANGEMENT_SECTIONS = 32;
export const MAX_ARRANGEMENT_MEASURES = 128;

export function decodeArrangement(value: unknown, tracks: readonly CompositionTrack[], projectSeed: bigint, sectionTypeChances: SectionTypeChanceSettings, random: () => number): ArrangementSection[] | undefined {
  if (!Array.isArray(value) || value.length < 1 || value.length > MAX_ARRANGEMENT_SECTIONS) {
    return undefined;
  }
  const sections: ArrangementSection[] = [];
  const ids = new Set<string>();
  const trackIds = new Set(tracks.map((track) => track.id));
  let measureCount = 0;
  for (const candidate of value) {
    if (!candidate || typeof candidate !== 'object') {
      return undefined;
    }
    const section = candidate as Partial<ArrangementSection> & {
      parts?: Partial<Record<ArrangedPart, unknown>>;
      partChances?: Partial<Record<ArrangedPart, unknown>>;
      trackParts?: Record<string, unknown>;
      trackPartChances?: Record<string, unknown>;
    };
    const sectionType = section.type as SectionType;
    const parts = section.parts;
    const chances = section.partChances;
    const trackParts = section.trackParts;
    const trackPartChances = section.trackPartChances;
    const trackPresence = section.trackPresence as Record<string, unknown> | undefined;
    const chordDegrees = section.chordDegrees;
    if ((section.useMelodyHarmony !== undefined && typeof section.useMelodyHarmony !== 'boolean')
      || (section.swapMelody !== undefined && typeof section.swapMelody !== 'boolean')) return undefined;
    if (typeof section.id !== 'string' || section.id.length === 0 || section.id.length > 80 || ids.has(section.id)
        || !SECTION_TYPES.includes(sectionType)
        || !Number.isInteger(section.measures) || (section.measures ?? 0) < 1 || (section.measures ?? 33) > 32
        || !validSectionHarmony(section as ArrangementSection)
        || (chordDegrees !== undefined && (!Array.isArray(chordDegrees) || chordDegrees.length < 1 || chordDegrees.length > 32
          || chordDegrees.some((degree) => !Number.isInteger(degree) || degree < 1 || degree > 7)))
        || (parts !== undefined && (!parts || typeof parts !== 'object' || Array.isArray(parts)
          || ARRANGED_PARTS.some((part) => part === 'melody' && parts[part] === undefined ? false : typeof parts[part] !== 'boolean')))
        || (chances !== undefined && (!chances || typeof chances !== 'object' || Array.isArray(chances)
          || ARRANGED_PARTS.some((part) => part === 'melody' && chances[part] === undefined ? false : !Number.isInteger(chances[part])
            || (chances[part] as number) < 0 || (chances[part] as number) > 100)))
        || (trackParts !== undefined && (!trackParts || typeof trackParts !== 'object' || Array.isArray(trackParts)
          || Object.entries(trackParts).some(([id, included]) => !/^[a-zA-Z0-9_-]{1,64}$/.test(id) || typeof included !== 'boolean')))
        || (trackPartChances !== undefined && (!trackPartChances || typeof trackPartChances !== 'object' || Array.isArray(trackPartChances)
          || Object.entries(trackPartChances).some(([id, chance]) => !/^[a-zA-Z0-9_-]{1,64}$/.test(id)
            || !Number.isInteger(chance) || (chance as number) < 0 || (chance as number) > 100)))
        || (trackPresence !== undefined && (!trackPresence || typeof trackPresence !== 'object' || Array.isArray(trackPresence)
          || Object.entries(trackPresence).some(([id, present]) => !/^[a-zA-Z0-9_-]{1,64}$/.test(id) || typeof present !== 'boolean')))) {
      return undefined;
    }
    if ([...Object.keys(trackParts ?? {}), ...Object.keys(trackPartChances ?? {}), ...Object.keys(trackPresence ?? {})]
      .some((trackId) => !trackIds.has(trackId))) return undefined;
    const rolePartOverrides: Partial<Record<ArrangedPart, PartSettingsPatch>> = {};
    const trackPartOverrides: Record<string, PartSettingsPatch> = {};
    for (const [raw, decoded, byTrack] of [[section.rolePartOverrides, rolePartOverrides, false],
      [section.trackPartOverrides, trackPartOverrides, true]] as const) {
      if (raw === undefined) continue;
      if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return undefined;
      for (const [key, patch] of Object.entries(raw)) {
        const role = byTrack ? tracks.find((track) => track.id === key)?.role : key as ArrangedPart;
        if (!role || !ARRANGED_PARTS.includes(role)) return undefined;
        const result = decodePartPatch(role, patch);
        if (!result) return undefined;
        (decoded as Record<string, PartSettingsPatch>)[key] = result;
      }
    }
    const trackPhrases: Record<string, readonly PhraseNote[]> = {};
    if (section.generatedTrackIds !== undefined && (!Array.isArray(section.generatedTrackIds)
      || section.generatedTrackIds.some(id => typeof id !== 'string' || !trackIds.has(id))
      || new Set(section.generatedTrackIds).size !== section.generatedTrackIds.length)) return undefined;
    if (section.trackPhrases !== undefined) {
      if (!section.trackPhrases || typeof section.trackPhrases !== 'object' || Array.isArray(section.trackPhrases)) return undefined;
      for (const [trackId, notes] of Object.entries(section.trackPhrases)) {
        if (!trackIds.has(trackId) || !isPhrase(notes, 2112, -64)) return undefined;
        trackPhrases[trackId] = notes.map(note => ({ ...note }));
      }
    }
    const effectiveSection = { rolePartOverrides, trackPartOverrides } as ArrangementSection;
    if (tracks.some((track) => !validVelocityRange(resolvePartTrack(track, effectiveSection).generatorSettings, track.role))) return undefined;
    ids.add(section.id);
    measureCount += section.measures!;
    if (measureCount > MAX_ARRANGEMENT_MEASURES) {
      return undefined;
    }
    const hasLegacyChanceData = parts !== undefined || chances !== undefined
      || trackParts !== undefined || trackPartChances !== undefined;
    const generatedPresence = hasLegacyChanceData ? {} : generateTrackPresence(tracks, sectionTypeChances[sectionType], random);
    const presence: Record<string, boolean> = {};
    for (const track of tracks) {
      const explicit = trackPresence?.[track.id];
      if (typeof explicit === 'boolean') {
        presence[track.id] = explicit;
      } else if (hasLegacyChanceData) {
        const included = trackParts?.[track.id] ?? parts?.[track.role] ?? true;
        const chance = trackPartChances?.[track.id] ?? chances?.[track.role]
          ?? DEFAULT_SECTION_TYPE_CHANCES[sectionType][track.role];
        presence[track.id] = legacyTrackPresence(projectSeed, track, included as boolean, chance as number);
      } else {
        presence[track.id] = generatedPresence[track.id];
      }
    }
    sections.push({
      id: section.id,
      type: sectionType,
      measures: section.measures!,
      ...(section.context ? { context: { ...section.context } } : {}),
      ...(section.customChords ? { customChords: section.customChords.map(chord => chord ? { ...chord } : null) } : {}),
      ...(section.customDurations ? { customDurations: [...section.customDurations] } : {}),
      chordDegrees: chordDegrees === undefined ? undefined : [...chordDegrees],
      ...(section.useMelodyHarmony !== undefined ? { useMelodyHarmony: section.useMelodyHarmony } : {}),
      ...(section.swapMelody !== undefined ? { swapMelody: section.swapMelody } : {}),
      trackPresence: presence,
      rolePartOverrides: rolePartOverrides as NonNullable<ArrangementSection['rolePartOverrides']>,
      trackPartOverrides,
      ...(section.trackPhrases !== undefined ? { trackPhrases } : {}),
      ...(section.generatedTrackIds !== undefined ? { generatedTrackIds: [...section.generatedTrackIds] } : {}),
    });
  }
  return sections;
}
