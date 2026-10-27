import type { CompositionProject } from '../project/project.model';
import type { PartSettingsScope } from '../project/part-settings-scope';
import type { ArpeggioSettings } from './part-settings.model';
import type { ArpeggioPattern } from './arpeggio-pattern';
import { arpeggioDirection } from './arpeggio-pattern';
import { resolvePartTrack } from './part-settings';
import { baseGenerationHarmony, sectionGenerationHarmony, withTrackSeed } from './project-harmony';

/** One cycle for the first chord, before rhythm, contour, Span and Repeat. */
export function arpeggioPreviews(project: CompositionProject, scope: PartSettingsScope, pattern?: ArpeggioPattern) {
  const section = 'sectionId' in scope ? project.arrangement.find(section => section.id === scope.sectionId) : undefined;
  if ('sectionId' in scope && !section) return [];
  return project.tracks.filter(track => track.role === 'arpeggio' && (!('role' in scope) || scope.role === 'arpeggio')
    && (!('trackId' in scope) || scope.trackId === track.id)).map(track => {
    const effective = resolvePartTrack(track, section);
    const settings = { ...effective.generatorSettings, ...(pattern ? { pattern } : {}) } as ArpeggioSettings;
    const harmony = section ? sectionGenerationHarmony(project, section, 'arpeggio') : baseGenerationHarmony(project);
    const voices = settings.stretchEnabled ? settings.chordNotesStretch ?? 3 : harmony.voicings[0].length * settings.octaves;
    const hits = settings.rhythm === 'melody' ? harmony.degrees.length * 16 : settings.hitsPerPattern ?? (settings.rate === 'eighth' ? 8 : 16);
    const order = track.orderOffset ?? project.tracks.filter(candidate => candidate.role === 'arpeggio').findIndex(candidate => candidate.id === track.id) + 1;
    return { trackId: track.id, name: track.name, values: arpeggioDirection(BigInt(withTrackSeed(project, track).seed), settings, hits, voices, order) };
  });
}
