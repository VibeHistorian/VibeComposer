export const THEME_COLORS = [
  { key: 'background', label: 'Background' },
  { key: 'surface', label: 'Surface' },
  { key: 'inspector', label: 'Inspector background' },
  { key: 'roleGroups', label: 'Role groups background' },
  { key: 'text', label: 'Text' },
  { key: 'border', label: 'Border' },
  { key: 'accent', label: 'Accent' },
] as const;
export type ThemeColor = typeof THEME_COLORS[number]['key'];
export type ThemeColors = Readonly<Record<ThemeColor, string>>;

export const DEFAULT_THEME_COLORS: ThemeColors = {
  background: '#080e16', surface: '#0b1e30',
  inspector: '#0b1e30', roleGroups: '#112d45',
  text: '#f2f7ff', border: '#3b607d', accent: '#32d7f4',
};

/** Derive concrete colors so CSS, Canvas and Pixi share the same palette. */
export function themeColorTokens(colors: ThemeColors): Readonly<Record<string, string>> {
  const mix = (from: string, to: string, amount: number): string => '#' + [1, 3, 5].map(offset =>
    Math.round(parseInt(from.slice(offset, offset + 2), 16) * (1 - amount)
      + parseInt(to.slice(offset, offset + 2), 16) * amount).toString(16).padStart(2, '0')).join('');
  const { background, surface, inspector, roleGroups, text, border, accent } = colors;
  const raised = (amount: number) => mix(surface, border, amount);
  const tinted = (amount: number) => mix(surface, accent, amount);
  const muted = (amount: number) => mix(text, surface, amount);
  const luminance = (color: string): number => {
    const channels = [1, 3, 5].map(offset => parseInt(color.slice(offset, offset + 2), 16) / 255)
      .map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4);
    return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
  };
  const loopBackgrounds = [background, surface, raised(.18)].map(luminance);
  const loopContrast = (color: string): number => {
    const light = luminance(color);
    return Math.min(...loopBackgrounds.map(other => (Math.max(light, other) + .05) / (Math.min(light, other) + .05)));
  };
  const inverseBackground = '#' + [1, 3, 5].map(offset =>
    (255 - parseInt(background.slice(offset, offset + 2), 16)).toString(16).padStart(2, '0')).join('');
  // Retain yellow wherever readable; move toward the inverse background only as far as needed.
  let loopAccent = '#ffe16a';
  for (const target of [inverseBackground, text]) {
    for (let step = 1; step <= 100 && loopContrast(loopAccent) < 4.5; step++) {
      const candidate = mix('#ffe16a', target, step / 100);
      if (loopContrast(candidate) > loopContrast(loopAccent)) loopAccent = candidate;
    }
  }
  return {
    '--surface-canvas': background,
    '--surface-inspector': inspector, '--surface-role-group': roleGroups,
    '--surface-role-group-selected': mix(roleGroups, background, .3),
    '--surface-input': mix(background, surface, .65), '--surface-base': surface,
    '--surface-low': raised(.18), '--surface-card': raised(.35),
    '--surface-button': tinted(.23), '--surface-button-hover': tinted(.35),
    '--surface-hover': tinted(.2), '--surface-accent': tinted(.15), '--surface-accent-hover': tinted(.25),
    '--surface-score': mix(background, surface, .6), '--surface-glass': surface + 'f5',
    '--surface-blue': tinted(.12), '--surface-violet': mix(surface, '#a49ac9', .18),
    '--surface-warm': mix(surface, '#ffc66b', .16), '--surface-danger': mix(surface, '#ff9fb5', .16),
    '--text-primary': text, '--text-strong': muted(.1), '--text-secondary': muted(.28),
    '--text-muted': muted(.44), '--text-subtle': muted(.52), '--text-faint': muted(.6), '--text-disabled': muted(.7),
    '--border-subtle': mix(surface, border, .3), '--border-default': mix(surface, border, .65),
    '--border-strong': border, '--border-contrast': mix(border, text, .18),
    '--border-focus': accent, '--border-accent': mix(surface, accent, .5),
    '--border-danger': mix(border, '#ff9fb5', .4), '--border-blue': mix(border, accent, .2),
    '--border-violet': mix(border, '#a49ac9', .4), '--border-warm': mix(border, '#ffc66b', .4),
    '--border-grid': mix(surface, border, .6),
    '--grid-subdivision': raised(.15), '--grid-step': raised(.08), '--grid-eighth': raised(.3),
    '--grid-beat': raised(.8), '--grid-measure': mix(border, text, .2),
    '--score-row-odd': mix(background, surface, .8), '--score-row-even': surface,
    '--score-pitch-a': mix(background, surface, .75), '--score-pitch-b': mix(background, surface, .9),
    '--score-pitch-band': border + '0d', '--score-out-of-scale': mix(background, surface, .6),
    '--accent-primary': accent, '--accent-hover': mix(accent, text, .4),
    '--accent-shadow': accent + '26', '--accent-glow': accent + '80', '--accent-outline': accent + '2e',
    '--ambient-glow': accent + '0a',
    '--loop-accent': loopAccent, '--loop-line': mix(loopAccent, surface, .2),
    '--loop-surface': loopAccent + '1f', '--loop-outline': loopAccent + '33',
  };
}
