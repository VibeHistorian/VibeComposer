import { ARRANGED_PARTS, type ArrangedPart } from '../project/project.model';

export const FONT_CHOICES = [
  { value: "'DM Sans', sans-serif", label: 'DM Sans' },
  { value: "'DM Mono', monospace", label: 'DM Mono' },
  { value: 'system-ui, sans-serif', label: 'System' },
  { value: 'Arial, sans-serif', label: 'Arial' },
  { value: 'Verdana, sans-serif', label: 'Verdana' },
  { value: 'Georgia, serif', label: 'Georgia' },
  { value: "'Courier New', monospace", label: 'Courier New' },
  { value: 'monospace', label: 'System monospace' },
] as const;

export const FONT_AREAS = [
  { key: 'ui', label: 'Interface text and controls' },
  { key: 'heading', label: 'Headings and track groups' },
  { key: 'mono', label: 'Numbers and music labels' },
] as const;
export type FontArea = typeof FONT_AREAS[number]['key'];
export interface AppearancePreferences {
  readonly fonts: Readonly<Record<FontArea, string>>;
  readonly roleColors: Readonly<Record<ArrangedPart, string>>;
}

export function defaultAppearance(): AppearancePreferences {
  return {
    fonts: { ui: FONT_CHOICES[0].value, heading: FONT_CHOICES[0].value, mono: FONT_CHOICES[1].value },
    roleColors: { melody: '#f26772', bass: '#22a7f2', chords: '#45df89', arpeggio: '#ed55bf', drums: '#f2bb38' },
  };
}

export function isRoleColor(value: unknown): value is string {
  return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value);
}

export function decodeAppearance(value: unknown): AppearancePreferences | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const data = value as AppearancePreferences;
  if (!data.fonts || !data.roleColors
      || !FONT_AREAS.every(area => FONT_CHOICES.some(font => font.value === data.fonts[area.key]))
      || !ARRANGED_PARTS.every(role => isRoleColor(data.roleColors[role]))) return null;
  return {
    fonts: Object.fromEntries(FONT_AREAS.map(area => [area.key, data.fonts[area.key]])) as AppearancePreferences['fonts'],
    roleColors: Object.fromEntries(ARRANGED_PARTS.map(role => [role, data.roleColors[role].toLowerCase()])) as AppearancePreferences['roleColors'],
  };
}

/** Concrete hex values also work in the canvas and Pixi color parsers. */
export function roleColorTokens(role: ArrangedPart, base: string): Readonly<Record<string, string>> {
  const mix = (target: number, amount: number): string => '#' + [1, 3, 5].map(offset =>
    Math.round(parseInt(base.slice(offset, offset + 2), 16) * (1 - amount) + target * amount)
      .toString(16).padStart(2, '0')).join('');
  const prefix = `--role-${role}`;
  const tokens: Record<string, string> = {
    [prefix]: base, [`${prefix}-glow`]: base + '50', [`${prefix}-strong`]: mix(255, .22),
    [`${prefix}-dark`]: mix(0, .4), [`${prefix}-surface`]: mix(0, .72),
    [`${prefix}-border`]: mix(0, .32), [`${prefix}-text`]: mix(255, .78),
    [`${prefix}-header`]: mix(0, .65), [`${prefix}-cell-surface`]: mix(0, .79),
  };
  if (role === 'drums') {
    for (const suffix of ['surface', 'border', 'cell-surface']) tokens[`--role-drum-${suffix}`] = tokens[`${prefix}-${suffix}`];
    tokens['--role-kick'] = base;
    tokens['--role-kick-glow'] = base + '50';
    tokens['--role-kick-dark'] = tokens[`${prefix}-dark`];
    tokens['--role-snare'] = tokens[`${prefix}-strong`];
    tokens['--role-snare-glow'] = base + '50';
    tokens['--role-hat'] = mix(255, .65);
  }
  return tokens;
}
