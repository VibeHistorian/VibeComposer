import { ARRANGED_PARTS, type ArrangedPart } from '../music/part-role';
import { DEFAULT_THEME_COLORS, THEME_COLORS, type ThemeColors } from './theme';

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
export const COLOR_ADJUSTMENTS = [
  { key: 'brightness', label: 'Brightness', description: 'Multiply color brightness by 100% plus this amount.' },
  { key: 'hueShift', label: 'Hue shift', description: 'Rotate color hues; 50% is half a turn.' },
  { key: 'saturation', label: 'Saturation', description: 'Multiply color saturation by 100% plus this amount.' },
] as const;
export type ColorAdjustment = typeof COLOR_ADJUSTMENTS[number]['key'];
export type ColorAdjustments = Readonly<Record<ColorAdjustment, number>>;
const DEFAULT_COLOR_ADJUSTMENTS: ColorAdjustments = { brightness: 0, hueShift: 0, saturation: 0 };

export interface AppearancePreferences {
  readonly hueShiftRoles: boolean;
  readonly colorAdjustments: ColorAdjustments;
  readonly themeColors: ThemeColors;
  readonly fonts: Readonly<Record<FontArea, string>>;
  readonly roleColors: Readonly<Record<ArrangedPart, string>>;
}

export function defaultAppearance(): AppearancePreferences {
  return {
    hueShiftRoles: false,
    colorAdjustments: { ...DEFAULT_COLOR_ADJUSTMENTS },
    themeColors: { ...DEFAULT_THEME_COLORS },
    fonts: { ui: FONT_CHOICES[0].value, heading: FONT_CHOICES[0].value, mono: FONT_CHOICES[1].value },
    roleColors: { melody: '#d44040', bass: '#8ca9ba', chords: '#28b82b', arpeggio: '#ed55bf', drums: '#e4b74e' },
  };
}

export function isRoleColor(value: unknown): value is string {
  return typeof value === 'string' && /^#[0-9a-f]{6}$/i.test(value);
}

export function decodeAppearance(value: unknown): AppearancePreferences | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const data = value as AppearancePreferences;
  if (data.hueShiftRoles !== undefined && typeof data.hueShiftRoles !== 'boolean') return null;
  const colorAdjustments = data.colorAdjustments === undefined ? DEFAULT_COLOR_ADJUSTMENTS : data.colorAdjustments;
  if (!colorAdjustments || typeof colorAdjustments !== 'object' || Array.isArray(colorAdjustments)
      || !COLOR_ADJUSTMENTS.every(({ key }) => Number.isFinite(colorAdjustments[key])
        && colorAdjustments[key] >= -50 && colorAdjustments[key] <= 50)) return null;
  if (!data.fonts || !data.roleColors
      || !FONT_AREAS.every(area => FONT_CHOICES.some(font => font.value === data.fonts[area.key]))
      || !ARRANGED_PARTS.every(role => isRoleColor(data.roleColors[role]))) return null;
  const themeColors = data.themeColors === undefined ? DEFAULT_THEME_COLORS : data.themeColors;
  if (!themeColors || typeof themeColors !== 'object' || Array.isArray(themeColors)) return null;
  const resolvedColors = {
    ...themeColors,
    inspector: themeColors.inspector === undefined ? DEFAULT_THEME_COLORS.inspector : themeColors.inspector,
    roleGroups: themeColors.roleGroups === undefined ? DEFAULT_THEME_COLORS.roleGroups : themeColors.roleGroups,
  };
  if (!THEME_COLORS.every(color => isRoleColor(resolvedColors[color.key]))) return null;
  return {
    hueShiftRoles: data.hueShiftRoles === true,
    colorAdjustments: Object.fromEntries(COLOR_ADJUSTMENTS.map(({ key }) => [key, colorAdjustments[key]])) as ColorAdjustments,
    themeColors: Object.fromEntries(THEME_COLORS.map(color => [color.key, resolvedColors[color.key].toLowerCase()])) as ThemeColors,
    fonts: Object.fromEntries(FONT_AREAS.map(area => [area.key, data.fonts[area.key]])) as AppearancePreferences['fonts'],
    roleColors: Object.fromEntries(ARRANGED_PARTS.map(role => [role, data.roleColors[role].toLowerCase()])) as AppearancePreferences['roleColors'],
  };
}

/** Adjust in HSV, then clamp the final RGB channels. Always start from the chosen color. */
export function adjustColor(base: string, adjustments: ColorAdjustments): string {
  const { brightness, hueShift, saturation } = adjustments;
  if (brightness === 0 && hueShift === 0 && saturation === 0) return base;
  const [r, g, b] = [1, 3, 5].map(offset => parseInt(base.slice(offset, offset + 2), 16) / 255);
  const max = Math.max(r, g, b), min = Math.min(r, g, b), delta = max - min;
  let hue = delta === 0 ? 0 : max === r ? (g - b) / delta : max === g ? (b - r) / delta + 2 : (r - g) / delta + 4;
  hue = ((hue + hueShift * 6 / 100) % 6 + 6) % 6;
  const sat = Math.min(1, (max === 0 ? 0 : delta / max) * (1 + saturation / 100));
  const chroma = max * sat;
  const x = chroma * (1 - Math.abs(hue % 2 - 1));
  const channels = hue < 1 ? [chroma, x, 0] : hue < 2 ? [x, chroma, 0]
    : hue < 3 ? [0, chroma, x] : hue < 4 ? [0, x, chroma] : hue < 5 ? [x, 0, chroma] : [chroma, 0, x];
  return '#' + channels.map(channel => Math.max(0, Math.min(255,
    Math.round((channel + max - chroma) * (1 + brightness / 100) * 255))).toString(16).padStart(2, '0')).join('');
}

/** Concrete hex values also work in the canvas and Pixi color parsers. */
export function roleColorTokens(role: ArrangedPart, base: string, theme: ThemeColors = DEFAULT_THEME_COLORS): Readonly<Record<string, string>> {
  const mix = (target: string, amount: number): string => '#' + [1, 3, 5].map(offset =>
    Math.round(parseInt(base.slice(offset, offset + 2), 16) * (1 - amount)
      + parseInt(target.slice(offset, offset + 2), 16) * amount)
      .toString(16).padStart(2, '0')).join('');
  const prefix = `--role-${role}`;
  const tokens: Record<string, string> = {
    [prefix]: base, [`${prefix}-glow`]: base + '50', [`${prefix}-strong`]: mix(theme.text, .22),
    [`${prefix}-dark`]: mix(theme.surface, .4), [`${prefix}-surface`]: mix(theme.surface, .72),
    [`${prefix}-border`]: mix(theme.surface, .32), [`${prefix}-text`]: mix(theme.text, .78),
    [`${prefix}-header`]: mix(theme.surface, .55), [`${prefix}-cell-surface`]: mix(theme.background, .8),
    [`${prefix}-cell-empty`]: mix(theme.background, .88),
  };
  if (role === 'drums') {
    for (const suffix of ['surface', 'border', 'cell-surface', 'cell-empty']) tokens[`--role-drum-${suffix}`] = tokens[`${prefix}-${suffix}`];
    tokens['--role-kick'] = base;
    tokens['--role-kick-glow'] = base + '50';
    tokens['--role-kick-dark'] = tokens[`${prefix}-dark`];
    tokens['--role-snare'] = tokens[`${prefix}-strong`];
    tokens['--role-snare-glow'] = base + '50';
    tokens['--role-hat'] = mix(theme.text, .65);
  }
  return tokens;
}
