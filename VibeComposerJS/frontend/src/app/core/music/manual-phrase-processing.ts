import type { CommonPartSettings, PartSettingsPatch } from './part-settings.model';
import type { PhraseNote } from './phrase-note';
import type { CompositionProject } from '../project/project.model';
import type { ArrangedPart } from './part-role';
import { MANUAL_PARAMETER_TARGETS, PART_CONTROLS } from './part-settings';

/** Apply after ownership/repetition, before project output processing. Preserve pickups. */
export function processManualPhrase(notes: readonly PhraseNote[], settings: CommonPartSettings, role: ArrangedPart): PhraseNote[] {
  if (!(settings.manualTranspose && role !== 'drums') && (settings.manualLength ?? 100) === 100
    && !settings.manualOffset && !settings.manualDelayCount) return notes.map(note => ({ ...note }));
  return notes.flatMap(note => Array.from({ length: (settings.manualDelayCount ?? 0) + 1 }, (_, index) => ({
    ...note, id: index === 0 ? note.id : `${note.id}-manual-delay-${index}`,
    midi: Math.max(0, Math.min(127, note.midi + (role === 'drums' ? 0 : settings.manualTranspose ?? 0))),
    durationBeats: Math.max(1 / 960, note.durationBeats * (settings.manualLength ?? 100) / 100),
    startBeat: note.startBeat + (settings.manualOffset ?? 0) / 1000 + index * (settings.manualDelayDuration ?? 750) / 1000,
    velocity: index === 0 ? note.velocity : Math.max(0, Math.min(127, Math.trunc(note.velocity * ((settings.manualDelayVelocity ?? 65) / 100) ** index))),
  }))).filter(note => note.velocity > 0).sort((a, b) => a.startBeat - b.startBeat || a.midi - b.midi);
}

/** Add each manual adjustment relative to its neutral default to retained generator settings. */
export function transferManualParameters<T extends PartSettingsPatch>(settings: T, role: ArrangedPart, baseline: PartSettingsPatch = settings): T {
  const result = { ...settings } as Record<string, unknown>;
  for (const [key, target] of Object.entries(MANUAL_PARAMETER_TARGETS)) {
    if (result[key] === undefined) continue;
    const destination = key === 'manualLength' && role === 'chords' ? 'noteLengthPercent' : target;
    const control = PART_CONTROLS[role].find(control => control.key === destination);
    const manual = PART_CONTROLS[role].find(control => control.key === key)!;
    const original = (baseline as Record<string, unknown>)[destination] ?? control?.defaultValue ?? (destination === 'noteLengthPercent' ? 100 : 0);
    const delta = (result[key] as number) - (manual.defaultValue as number);
    const value = control ? Math.max(control.minimum!, Math.min(control.maximum!, (original as number) + delta)) : 0;
    if (control && (!control.allowedValues || control.allowedValues.includes(value))
      && (!control.step || (value - control.minimum!) % control.step === 0)) result[destination] = value;
    delete result[key];
  }
  return result as T;
}

/** Piano-roll drafts edit source notes, so applying them never bakes these controls twice. */
export function withoutManualAdjustments(project: CompositionProject): CompositionProject {
  const clear = <T extends PartSettingsPatch>(settings: T): T => Object.fromEntries(
    Object.entries(settings).filter(([key]) => !(key in MANUAL_PARAMETER_TARGETS))) as T;
  return { ...project, tracks: project.tracks.map(track => ({ ...track, generatorSettings: clear(track.generatorSettings) })) as CompositionProject['tracks'],
    arrangement: project.arrangement.map(section => ({ ...section,
      rolePartOverrides: Object.fromEntries(Object.entries(section.rolePartOverrides ?? {}).map(([id, patch]) => [id, clear(patch)])),
      trackPartOverrides: Object.fromEntries(Object.entries(section.trackPartOverrides ?? {}).map(([id, patch]) => [id, clear(patch)])),
    })) };
}
