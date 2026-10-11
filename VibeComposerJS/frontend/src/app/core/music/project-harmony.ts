import type { ArrangedPart, ArrangementSection, CompositionProject, CompositionTrack } from '../project/project.model';
import { flattenProgression, soundingHarmony, voicedSoundingChords, type GenerationHarmony } from './harmony-voicing';
import { chordPitches, getPitchClass, progressionChords } from './harmony';
import { sectionChordDurations, sectionDegrees, sectionProject } from './section-context';
import { melodyRepairChords, melodyRootChords } from './melody-chord-targets';
import { generateMelody } from './melody-generator';
import { inferMelodyChordDegrees, type MelodySkeleton } from './melody-chord-inference';

export interface ResolvedHarmony extends GenerationHarmony {
  readonly durations: readonly number[];
  readonly degrees: readonly number[];
  readonly customChords: CompositionProject['progressionChords'];
  readonly inferred: boolean;
}
export interface MelodyHarmonyInference {
  readonly available: boolean;
  readonly sourceTrackId?: string;
  readonly manualSource: boolean;
  readonly harmony: ResolvedHarmony;
}
const bases = new WeakMap<CompositionProject, Map<ArrangementSection | undefined, ResolvedHarmony>>();
const alternates = new WeakMap<CompositionProject, MelodyHarmonyInference>();

/** Preserve the translation's track-ID stream, including exact Java long project seeds. */
export function withTrackSeed(project: CompositionProject, track: CompositionTrack): CompositionProject {
  if (track.id === `track-${track.role}-1`) return project;
  let hash = 0xcbf29ce484222325n;
  for (let index = 0; index < track.id.length; index++) {
    hash ^= BigInt(track.id.charCodeAt(index)); hash = BigInt.asUintN(64, hash * 0x100000001b3n);
  }
  return { ...project, seed: BigInt.asIntN(64, BigInt(project.seed) ^ hash).toString() };
}

export function baseGenerationHarmony(project: CompositionProject, section?: ArrangementSection): ResolvedHarmony {
  let cache = bases.get(project);
  if (!cache) { cache = new Map(); bases.set(project, cache); }
  const cached = cache.get(section);
  if (cached) return cached;
  const contextual = sectionProject(project, section);
  const baseDegrees = section ? sectionDegrees(project, section) : project.progression;
  const degrees = section?.customDurations ? section.customDurations.map((_, index) => baseDegrees[index % baseDegrees.length]) : baseDegrees;
  let customChords = section?.customChords ?? (section?.chordDegrees ? undefined : project.progressionChords);
  if (customChords && customChords.length !== degrees.length) customChords = degrees.map((_, index) => customChords![index % customChords!.length]);
  if (section?.context?.keyChangeType === 'two-five-one' && !section.chordDegrees && !section.customChords && degrees.length > 2) {
    const root = getPitchClass(contextual.key)!;
    customChords = degrees.map((_, index) => index === degrees.length - 2 ? { root: (root + 2) % 12, spice: 'm', inversion: 0 }
      : index === degrees.length - 1 ? { root: (root + 7) % 12, spice: '7', inversion: 0 } : customChords?.[index] ?? null);
  }
  project = contextual;
  const roots = melodyRootChords(project.key, project.scale, degrees, customChords);
  const raw = melodyRepairChords(project.key, project.scale, degrees, customChords);
  // The model stores inversion values rather than Java's explicit '.0' syntax.
  // Only nonzero inversions claim a protected custom voicing.
  const inversions = customChords?.flatMap((chord,index) => chord?.inversion ? [index] : []) ?? [];
  const definitions = progressionChords(project.key, project.scale, degrees, customChords);
  const soundingRoots = definitions.map(chord => chordPitches({ ...chord,inversion:0 },60));
  const originalSounding = definitions.map(chord => chordPitches(chord,60));
  const voicings = flattenProgression(raw, roots, BigInt(project.seed), project.harmonySettings, inversions);
  const durations = section ? sectionChordDurations(project, section) : degrees.map(() => 4);
  const result = { degrees, customChords, roots, durations, inferred: false,
    voicings, soundingRoots, soundingVoicings: voicedSoundingChords(raw,voicings,originalSounding) };
  cache.set(section, result);
  return result;
}

/** Infer from base skeleton only: never recurse through selected alternate section harmony. */
export function projectMelodyHarmony(project: CompositionProject): MelodyHarmonyInference {
  const cached = alternates.get(project);
  if (cached) return cached;
  const base = baseGenerationHarmony(project), source = project.tracks.find(track => track.role === 'melody');
  let result: MelodyHarmonyInference = { available: false, sourceTrackId: source?.id,
    manualSource: source?.editedPhrase !== undefined, harmony: base };
  if (source?.role === 'melody') {
    let skeleton: MelodySkeleton | undefined;
    generateMelody(BigInt(withTrackSeed(project, source).seed), project.key, project.scale, base.degrees,
      source.generatorSettings, 0, undefined, project.melodyGlobal, base.customChords, base,
      notes => { skeleton = notes; });
    if (skeleton) {
      const inferred = inferMelodyChordDegrees(skeleton);
      const degrees = base.degrees.map((degree,index) => index > 0 && index < inferred.length - 1 ? inferred[index] : degree);
      const interiorRoots = melodyRootChords('C', 'major', degrees);
      const roots = base.roots.map((chord,index) => [...(index > 0 && index < degrees.length - 1 ? interiorRoots[index] : chord)]);
      const raw = base.voicings.map((chord,index) => [...(index > 0 && index < degrees.length - 1 ? interiorRoots[index] : chord)]);
      // Java inference always uses ordinary squish and protects no inversion slots.
      // One/two-chord recovery retains the base progression without Java's duplicate endpoint.
      const voicings = degrees.length <= 2 ? base.voicings.map(chord => [...chord])
        : flattenProgression(raw, roots, BigInt(project.seed), { ...project.harmonySettings, progressiveVoicing: false });
      const inferredSounding = soundingHarmony(interiorRoots,project.key,project.scale);
      const soundingRoots = roots.map((_,index) => [...(index > 0 && index < degrees.length - 1 ? inferredSounding[index] : base.soundingRoots![index])]);
      const rawSounding = raw.map((_,index) => [...(index > 0 && index < degrees.length - 1 ? inferredSounding[index] : base.soundingVoicings![index])]);
      const soundingVoicings = voicedSoundingChords(raw,voicings,rawSounding);
      const customChords = base.customChords?.map((chord,index) => index > 0 && index < degrees.length - 1 ? null : chord);
      result = { ...result, available: true, harmony: { degrees, customChords, roots, durations: base.durations, voicings, soundingRoots, soundingVoicings, inferred: true } };
    }
  }
  alternates.set(project, result);
  return result;
}

export function sectionGenerationHarmony(project: CompositionProject, section: ArrangementSection, role: ArrangedPart): ResolvedHarmony {
  const base = baseGenerationHarmony(project, section);
  if (section.chordDegrees || section.customChords || section.context?.keyChangeType === 'two-five-one'
    || !(section.useMelodyHarmony || role === 'melody' && section.swapMelody)) return base;
  const inference = projectMelodyHarmony(project);
  if (!inference.available) return base;
  if (section.context?.key !== undefined || section.context?.scale !== undefined || section.context?.keyChange !== undefined
    || base.degrees.length !== inference.harmony.degrees.length) {
    const contextual = sectionProject(project, section);
    const degrees = base.degrees.map((_, index) => inference.harmony.degrees[index % inference.harmony.degrees.length]);
    const roots = melodyRootChords(contextual.key, contextual.scale, degrees);
    const raw = melodyRepairChords(contextual.key, contextual.scale, degrees);
    const voicings = flattenProgression(raw, roots, BigInt(project.seed), { ...project.harmonySettings, progressiveVoicing: false });
    const soundingRoots = soundingHarmony(roots, contextual.key, contextual.scale);
    return { degrees, customChords: undefined, roots, durations: base.durations, voicings, soundingRoots,
      soundingVoicings: soundingHarmony(voicings, contextual.key, contextual.scale), inferred: true,
      melodySeedOffset: role === 'melody' && section.swapMelody ? 100 : 0 };
  }
  return { ...inference.harmony, durations: base.durations, melodySeedOffset: role === 'melody' && section.swapMelody ? 100 : 0 };
}
