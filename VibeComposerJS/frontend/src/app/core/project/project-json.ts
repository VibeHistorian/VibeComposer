import type { CompositionProject } from './project.model';

type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };

/** Keep settings structured, numeric arrays inline, and manual phrases one note per line. */
export function formatProjectJson(project: CompositionProject): string {
  // Preserve JSON.stringify's handling of optional undefined fields and string escaping.
  const value: JsonValue = JSON.parse(JSON.stringify(project));
  return formatValue(value, 0);
}

function formatValue(value: JsonValue, depth: number): string {
  if (value === null || typeof value !== 'object') return JSON.stringify(value);

  const indent = '  '.repeat(depth);
  const childIndent = indent + '  ';
  if (Array.isArray(value)) {
    if (value.every(item => typeof item === 'number')) return `[${value.join(', ')}]`;
    return `[\n${value.map(item => childIndent + formatValue(item, depth + 1)).join(',\n')}\n${indent}]`;
  }

  const entries = Object.entries(value);
  if (entries.length === 0) return '{}';
  const isNote = typeof value['id'] === 'string'
    && ['midi', 'startBeat', 'durationBeats', 'velocity'].every(key => typeof value[key] === 'number')
    && entries.every(([, item]) => item === null || typeof item !== 'object');
  if (isNote) {
    return `{${entries.map(([key, item]) => `${JSON.stringify(key)}: ${JSON.stringify(item)}`).join(', ')}}`;
  }
  return `{\n${entries.map(([key, item]) => `${childIndent}${JSON.stringify(key)}: ${formatValue(item, depth + 1)}`).join(',\n')}\n${indent}}`;
}
