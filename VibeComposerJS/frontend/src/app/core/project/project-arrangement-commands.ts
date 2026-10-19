import type { ArrangedPart, ArrangementSection, CompositionProject, SectionType } from './project.model';
import { SECTION_TYPES } from './project.model';
import type { SectionTypeChanceSettings } from './section-type-chances';
import { MAX_ARRANGEMENT_SECTIONS } from './project-arrangement-decoder';
import { generateTrackPresence } from '../music/arrangement-generator';
import { copyPartSettings } from '../music/part-settings';
import { getDiatonicChords } from '../music/harmony';

// Build immutable candidates. ProjectService validates and commits each command once.
export function addSection(project: CompositionProject, type: SectionType, afterIndex: number, chances: SectionTypeChanceSettings, random: () => number): CompositionProject | undefined {
  const sections = project.arrangement;
  if (sections.length >= MAX_ARRANGEMENT_SECTIONS || !SECTION_TYPES.includes(type)) {
    return;
  }
  const insertAt = Math.max(0, Math.min(sections.length, afterIndex + 1));
  const nextId = sections.reduce((maximum, section) => {
    const match = /^section-(\d+)$/.exec(section.id);
    return match ? Math.max(maximum, Number(match[1])) : maximum;
  }, 0) + 1;
  const next = [...sections];
  next.splice(insertAt, 0, {
    id: `section-${nextId}`,
    type,
    measures: 4,
    trackPresence: generateTrackPresence(project.tracks, chances[type], random),
  });
  return { ...project, arrangement: next };
}

export function duplicateSection(project: CompositionProject, index: number): CompositionProject | undefined {
  const sections = project.arrangement;
  if (!Number.isInteger(index) || index < 0 || index >= sections.length
      || sections.length >= MAX_ARRANGEMENT_SECTIONS) {
    return;
  }
  const nextId = sections.reduce((maximum, section) => {
    const match = /^section-(\d+)$/.exec(section.id);
    return match ? Math.max(maximum, Number(match[1])) : maximum;
  }, 0) + 1;
  const next = [...sections];
  next.splice(index + 1, 0, {
    ...sections[index], id: `section-${nextId}`,
    context: sections[index].context ? { ...sections[index].context } : undefined,
    customChords: sections[index].customChords?.map(chord => chord ? { ...chord } : null),
    customDurations: sections[index].customDurations ? [...sections[index].customDurations] : undefined,
    chordDegrees: sections[index].chordDegrees ? [...sections[index].chordDegrees] : undefined,
    trackPresence: { ...sections[index].trackPresence },
    ...(sections[index].generatedTrackIds ? { generatedTrackIds: [...sections[index].generatedTrackIds] } : {}),
    ...(sections[index].trackPhrases !== undefined ? { trackPhrases: Object.fromEntries(Object.entries(sections[index].trackPhrases).map(([id, notes]) => [id, notes.map(note => ({ ...note }))])) } : {}),
    rolePartOverrides: Object.fromEntries(Object.entries(sections[index].rolePartOverrides ?? {}).map(([role, patch]) => [role, copyPartSettings(patch)])),
    trackPartOverrides: Object.fromEntries(Object.entries(sections[index].trackPartOverrides ?? {}).map(([id, patch]) => [id, copyPartSettings(patch)])),
  });
  return { ...project, arrangement: next };
}

export function removeSection(project: CompositionProject, index: number): CompositionProject | undefined {
  const sections = project.arrangement;
  if (!Number.isInteger(index) || index < 0 || index >= sections.length || sections.length <= 1) {
    return;
  }
  return { ...project, arrangement: sections.filter((_, sectionIndex) => sectionIndex !== index) };
}

export function moveSection(project: CompositionProject, index: number, offset: -1 | 1): CompositionProject | undefined {
  const sections = project.arrangement;
  const target = index + offset;
  if (!Number.isInteger(index) || index < 0 || target < 0 || target >= sections.length) {
    return;
  }
  const next = [...sections];
  [next[index], next[target]] = [next[target], next[index]];
  return { ...project, arrangement: next };
}

export function updateSection(project: CompositionProject, index: number, patch: Partial<Pick<ArrangementSection, 'type' | 'measures'>>): CompositionProject | undefined {
  const sections = project.arrangement;
  if (!Number.isInteger(index) || index < 0 || index >= sections.length) {
    return;
  }
  const next = [...sections];
  const current = sections[index];
  const updated = { ...current, ...patch };
  if (patch.measures !== undefined && !current.customChords && !current.customDurations) {
    const progression = project.progression;
    const chordDegrees = Array.from({ length: patch.measures }, (_, measure) =>
      current.chordDegrees?.[measure] ?? progression[measure % progression.length]);
    updated.chordDegrees = matchesMainProgression(project, chordDegrees) ? undefined : chordDegrees;
  }
  next[index] = updated;
  return { ...project, arrangement: next };
}

export function setSectionChordDegree(project: CompositionProject, sectionIndex: number, measureIndex: number, degree: number): CompositionProject | undefined {
  const section = project.arrangement[sectionIndex];
  if (!section || !Number.isInteger(measureIndex) || measureIndex < 0 || measureIndex >= section.measures
      || !getDiatonicChords(project.key, project.scale).some((chord) => chord.degree === degree)) {
    return;
  }
  const chordDegrees = Array.from({ length: section.measures }, (_, measure) =>
    section.chordDegrees?.[measure] ?? project.progression[measure % project.progression.length]);
  chordDegrees[measureIndex] = degree;
  const next = [...project.arrangement];
  next[sectionIndex] = { ...section, chordDegrees: matchesMainProgression(project, chordDegrees) ? undefined : chordDegrees };
  return { ...project, arrangement: next };
}

export function clearSectionChordOverrides(project: CompositionProject, sectionIndex: number): CompositionProject | undefined {
  const section = project.arrangement[sectionIndex];
  if (!section || !section.chordDegrees && !section.customChords) return;
  const next = [...project.arrangement];
  next[sectionIndex] = { ...section, chordDegrees: undefined, customChords: undefined };
  return { ...project, arrangement: next };
}

export function toggleSectionRolePresence(project: CompositionProject, sectionId: string, role: ArrangedPart): CompositionProject | undefined {
  const section = project.arrangement.find(candidate => candidate.id === sectionId);
  const tracks = project.tracks.filter(track => track.role === role);
  if (!section || !tracks.length) return;
  const present = !tracks.some(track => section.trackPresence[track.id] ?? false);
  const trackPresence = { ...section.trackPresence,
    ...Object.fromEntries(tracks.map(track => [track.id, present])) };
  return { ...project, arrangement: project.arrangement.map(candidate => candidate.id === sectionId
    ? { ...section, trackPresence } : candidate) };
}

export function setSectionTrackPresence(project: CompositionProject, index: number, trackId: string, present: boolean): CompositionProject | undefined {
  const section = project.arrangement[index];
  if (!section || typeof present !== 'boolean' || !project.tracks.some((candidate) => candidate.id === trackId)) return;
  const trackPresence = { ...section.trackPresence, [trackId]: present };
  return { ...project, arrangement: project.arrangement.map((candidate, candidateIndex) => candidateIndex === index
    ? { ...section, trackPresence } : candidate) };
}

export function randomizeArrangementPresence(project: CompositionProject, sectionId: string | undefined, chances: SectionTypeChanceSettings, random: () => number): CompositionProject | undefined {
  const arrangement = project.arrangement.map((section) => sectionId === undefined || section.id === sectionId
    ? { ...section, trackPresence: generateTrackPresence(project.tracks, chances[section.type], random) }
    : section);
  if (sectionId !== undefined && !project.arrangement.some((section) => section.id === sectionId)) return;
  return { ...project, arrangement };
}

function matchesMainProgression(project: CompositionProject, chordDegrees: readonly number[]): boolean {
  if (project.progressionChords?.some(Boolean)) return false;
  const progression = project.progression;
  return chordDegrees.every((degree, index) => degree === progression[index % progression.length]);
}
