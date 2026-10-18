import type { CompositionProject } from './project.model';

export const EXPORT_NAME_FIELDS = [
  { id: 'date', label: 'Date' }, { id: 'time', label: 'Time' },
  { id: 'key', label: 'Key' }, { id: 'bpm', label: 'BPM' }, { id: 'seed', label: 'Seed' },
] as const;
export type ExportNameField = typeof EXPORT_NAME_FIELDS[number]['id'];
export const DEFAULT_EXPORT_NAME_FIELDS: readonly ExportNameField[] = EXPORT_NAME_FIELDS.map(field => field.id);

export function safeExportName(value: string): string {
  return value.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '-').trim().replace(/[. ]+$/, '');
}

export function exportFilename(project: Pick<CompositionProject, 'name' | 'key' | 'tempoBpm' | 'seed'>,
  fields: readonly ExportNameField[], now = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  const values: Record<ExportNameField, string> = {
    date: `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`,
    time: `${pad(now.getHours())}-${pad(now.getMinutes())}`,
    key: project.key.replace(/[#♯]/g, 's').replace(/♭/g, 'b'),
    bpm: String(project.tempoBpm), seed: project.seed,
  };
  return [...EXPORT_NAME_FIELDS.filter(field => fields.includes(field.id)).map(field => safeExportName(values[field.id])),
    safeExportName(project.name).slice(0, 80) || 'VibeComposer'].filter(Boolean).join('_');
}
