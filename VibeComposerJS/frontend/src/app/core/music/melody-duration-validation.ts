import type { CompositionProject, CompositionTrack, MelodySettings, PartSettingsScope } from '../project/project.model';
import { DEFAULT_MELODY_DURATIONS, melodyDurationError } from './melody-durations';
import { resolvePartTrack } from './part-settings';
import { generateTrackPhrase } from './phrase';
import { baseGenerationHarmony, sectionGenerationHarmony } from './project-harmony';
import { sectionProject } from './section-context';

/** Dry-run every selectable lane against the destination tracks and harmonic contexts, without editing the project. */
export function validateMelodyDurationDraft(project: CompositionProject, scope: PartSettingsScope,
  patch: Partial<MelodySettings>): string | null {
  const tracks = project.tracks.filter(track => track.role === 'melody'
    && (!('trackId' in scope) || track.id === scope.trackId));
  if (!tracks.length) return 'The selected melody track no longer exists.';
  const sections = 'sectionId' in scope ? project.arrangement.filter(section => section.id === scope.sectionId)
    : [undefined, ...project.arrangement];
  if (!sections.length) return 'The selected section no longer exists.';
  for (const track of tracks) for (const section of sections) {
    const base = scope.kind.startsWith('global') ? { ...track, generatorSettings: { ...track.generatorSettings, ...patch } } as CompositionTrack : track;
    let effective = resolvePartTrack(base, section);
    if (scope.kind === 'section-track') effective = { ...effective, generatorSettings: { ...effective.generatorSettings, ...patch } } as CompositionTrack;
    if (scope.kind === 'section-role') effective = { ...effective, generatorSettings: {
      ...track.generatorSettings, ...section?.rolePartOverrides?.melody, ...patch, ...section?.trackPartOverrides?.[track.id],
    } } as CompositionTrack;
    if (effective.role !== 'melody' || effective.generatorSettings.rhythm !== 'custom') continue;
    const settings = effective.generatorSettings;
    const error = melodyDurationError(settings);
    if (error) return `${track.name}${section ? ' / ' + section.type : ''}: ${error}`;
    const rows = settings.customMelodyDurations ?? DEFAULT_MELODY_DURATIONS;
    const harmony = section ? sectionGenerationHarmony(project, section, 'melody') : baseGenerationHarmony(project);
    const contextual = { ...sectionProject(project, section), progression: harmony.degrees, progressionChords: harmony.customChords };
    try { generateTrackPhrase(contextual, effective, false, undefined, harmony); }
    catch (error) { return `${track.name}${section ? ' / ' + section.type : ''}: ${error instanceof Error ? error.message : 'Cannot generate this rhythm.'}`; }
    for (let lane = 0; lane < rows.length; lane++) {
      if (!rows[lane].length || settings.customDurationRandomWeighting && settings.customDurationChances?.[lane] === 0) continue;
      try {
        const candidate = { ...effective, generatorSettings: { ...settings, customMelodyDurations: rows.map((row, index) => index === lane ? row : []),
          customDurationRandomWeighting: false } } as CompositionTrack;
        generateTrackPhrase(contextual, candidate, false, undefined, harmony);
      } catch (error) {
        return `${track.name}${section ? ' / ' + section.type : ''}, lane ${lane + 1}: ${error instanceof Error ? error.message : 'Cannot generate this rhythm.'}`;
      }
    }
  }
  return null;
}
