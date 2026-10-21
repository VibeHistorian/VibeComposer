import { JavaRandom } from '../music/java-random';
import { ARRANGED_PARTS } from '../music/part-role';
import { type AppearancePreferences } from './appearance';
import { themeColorTokens, type ThemeColors } from './theme';

function hsl(hue: number, saturation: number, lightness: number): string {
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
  const sector = ((hue % 360) + 360) % 360 / 60;
  const x = chroma * (1 - Math.abs(sector % 2 - 1));
  const channels = sector < 1 ? [chroma, x, 0] : sector < 2 ? [x, chroma, 0]
    : sector < 3 ? [0, chroma, x] : sector < 4 ? [0, x, chroma]
      : sector < 5 ? [x, 0, chroma] : [chroma, 0, x];
  return '#' + channels.map(channel => Math.round((channel + lightness - chroma / 2) * 255)
    .toString(16).padStart(2, '0')).join('');
}

function luminance(color: string): number {
  const channels = [1, 3, 5].map(offset => {
    const channel = parseInt(color.slice(offset, offset + 2), 16) / 255;
    return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4;
  });
  return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
}

function contrast(first: string, second: string): number {
  const a = luminance(first), b = luminance(second);
  return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
}

/** A separate seeded stream: appearance never consumes musical generation randomness. */
export function randomAppearance(current: AppearancePreferences, seed: bigint): AppearancePreferences {
  const random = new JavaRandom(seed);
  const between = (min: number, max: number) => min + random.nextDouble() * (max - min);
  const light = random.nextDouble() < .3;
  const hue = between(155, 225);
  const saturation = random.nextDouble() < .25 ? between(.01, .08) : between(.15, .48);
  const panel = () => hsl(hue + between(-8, 8), saturation, light ? between(.87, .95) : between(.07, .16));
  let themeColors: ThemeColors = {
    background: hsl(hue, saturation * .6, light ? between(.95, .99) : between(.025, .065)),
    surface: panel(), inspector: panel(), roleGroups: panel(),
    text: hsl(hue, between(.05, .2), light ? .045 : .975),
    border: hsl(hue, saturation, light ? between(.56, .66) : between(.24, .34)),
    accent: hsl(hue + between(-8, 8), between(.5, .85), light ? .2 : .72),
  };
  // Check actual derived button, selected-group and panel surfaces as well as the base colors.
  const surfaces = (colors: ThemeColors) => Object.entries(themeColorTokens(colors))
    .filter(([key, value]) => key.startsWith('--surface-') && value.length === 7).map(([, value]) => value);
  // Move the accent toward the text until it is legible even on tinted buttons.
  for (let step = 0; step < 20; step++) {
    if (surfaces(themeColors).every(surface => contrast(themeColors.accent, surface) >= 3)
        && [themeColors.background, themeColors.surface, themeColors.inspector, themeColors.roleGroups]
          .every(surface => contrast(themeColors.accent, surface) >= 4.5)) break;
    themeColors = { ...themeColors,
      accent: hsl(hue, .65, light ? Math.max(.06, .2 - (step + 1) * .007) : Math.min(.96, .72 + (step + 1) * .012)) };
  }

  // Distribute five hues over the arc outside the theme family, then shuffle role assignments.
  // The 48° exclusion also covers the small hue variation between theme surfaces and accent.
  const rolePalette = Array.from({ length: 5 }, (_, index) => {
    const roleHue = hue + 48 + (index + .5) * 264 / 5 + between(-5, 5);
    const roleSaturation = between(.55, .9);
    let lightness = light ? between(.32, .46) : between(.55, .7);
    let color = hsl(roleHue, roleSaturation, lightness);
    for (let step = 0; step < 30 && contrast(color, themeColors.surface) < 3; step++) {
      lightness += light ? -.008 : .008;
      color = hsl(roleHue, roleSaturation, lightness);
    }
    return color;
  });
  for (let index = rolePalette.length - 1; index > 0; index--) {
    const other = random.nextInt(index + 1);
    [rolePalette[index], rolePalette[other]] = [rolePalette[other], rolePalette[index]];
  }
  return {
    ...current, themeColors,
    colorAdjustments: { brightness: 0, hueShift: 0, saturation: 0 },
    roleColors: Object.fromEntries(ARRANGED_PARTS.map((role, index) => [role, rolePalette[index]])) as AppearancePreferences['roleColors'],
  };
}
