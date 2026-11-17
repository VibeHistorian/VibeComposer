import type { CompositionProject } from '../../core/project/project.model';
import type { PartSettingsScope } from '../../core/project/part-settings-scope';
import type { CompositionTrack } from '../../core/project/track.model';
import type { ArrangementSection } from '../../core/project/arrangement.model';
import type { ArrangedPart } from '../../core/music/part-role';
import { partValuesEqual, resolvePartTrack, settingsValues, type PartSettingValue } from '../../core/music/part-settings';

export interface PartEditorContext {
  readonly tracks: readonly CompositionTrack[];
  readonly section: ArrangementSection | undefined;
  readonly role: ArrangedPart;
  readonly values: Readonly<Record<string, PartSettingValue | null>>;
  readonly overriddenKeys: readonly string[];
}

function resolvePartEditorContext(project: CompositionProject, scope: PartSettingsScope): PartEditorContext {
  const tracks = project.tracks.filter(track => 'trackId' in scope ? track.id === scope.trackId : track.role === scope.role);
  const section = 'sectionId' in scope ? project.arrangement.find(section => section.id === scope.sectionId) : undefined;
  const role = 'role' in scope ? scope.role : tracks[0]?.role ?? 'bass';
  const rows = tracks.map(track => settingsValues(resolvePartTrack(track, section, project.melodyGlobal).generatorSettings, track.role));
  const values: Readonly<Record<string, PartSettingValue | null>> = Object.fromEntries(Object.keys(rows[0] ?? {}).map(key =>
    [key, rows.every(row => partValuesEqual(row[key], rows[0][key])) ? rows[0][key] : null]));
  const overriddenKeys = !section ? [] : 'role' in scope
    ? [...new Set([...Object.keys(section.rolePartOverrides?.[scope.role] ?? {}),
      ...tracks.flatMap(track => Object.keys(section.trackPartOverrides?.[track.id] ?? {}))])]
    : Object.keys(section.trackPartOverrides?.[scope.trackId] ?? {});
  return { tracks, section, role, values, overriddenKeys };
}

const contexts = new WeakMap<CompositionProject, Map<string, ReturnType<typeof resolvePartEditorContext>>>();

/** Immutable projects let quick settings and inspector share one explicit scope's derived state. */
export function partEditorContext(project: CompositionProject, scope: PartSettingsScope) {
  let scopes = contexts.get(project);
  if (!scopes) { scopes = new Map(); contexts.set(project, scopes); }
  const key = JSON.stringify([scope.kind, 'sectionId' in scope ? scope.sectionId : null, 'trackId' in scope ? scope.trackId : scope.role]);
  let context = scopes.get(key);
  if (!context) { context = resolvePartEditorContext(project, scope); scopes.set(key, context); }
  return context;
}
