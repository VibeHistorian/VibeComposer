const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { adjustColor, decodeAppearance, defaultAppearance, roleColorTokens } = loadMusic('../ui/appearance.ts');
const { randomAppearance } = loadMusic('../ui/random-appearance.ts');
const { themeColorTokens } = loadMusic('../ui/theme.ts');
const adjustments = (overrides = {}) => ({ brightness: 0, hueShift: 0, saturation: 0, ...overrides });

const luminance = color => {
  const rgb = [1, 3, 5].map(offset => parseInt(color.slice(offset, offset + 2), 16) / 255)
    .map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4);
  return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
};
const contrast = (a, b) => (Math.max(luminance(a), luminance(b)) + .05) / (Math.min(luminance(a), luminance(b)) + .05);
const hue = color => {
  const [r, g, b] = [1, 3, 5].map(offset => parseInt(color.slice(offset, offset + 2), 16) / 255);
  const max = Math.max(r, g, b), delta = max - Math.min(r, g, b);
  return delta === 0 ? 0 : (((max === r ? (g - b) / delta : max === g ? (b - r) / delta + 2 : (r - g) / delta + 4) * 60) + 360) % 360;
};
const hueDistance = (a, b) => Math.min(Math.abs(hue(a) - hue(b)), 360 - Math.abs(hue(a) - hue(b)));

test('random palettes vary modes, preserve fonts and remain readable with distinct track hues', () => {
  const original = { ...defaultAppearance(), colorAdjustments: adjustments({ brightness: -50, hueShift: 50 }),
    fonts: { ui: 'Verdana, sans-serif', heading: 'Georgia, serif', mono: 'monospace' } };
  const modes = new Set(), palettes = new Set();
  for (let index = 0; index < 1000; index++) {
    const seed = BigInt(index) * 0x9e3779b97f4a7c15n;
    const appearance = randomAppearance(original, seed);
    const theme = appearance.themeColors;
    modes.add(luminance(theme.background) > .5);
    palettes.add(JSON.stringify(theme));
    assert.deepEqual(decodeAppearance(appearance), appearance);
    assert.deepEqual(appearance.fonts, original.fonts);
    assert.deepEqual(appearance.colorAdjustments, adjustments());
    for (const color of Object.values(theme)) {
      const rgb = [1, 3, 5].map(offset => parseInt(color.slice(offset, offset + 2), 16));
      assert.ok(Math.max(...rgb) - Math.min(...rgb) <= 3 || hue(color) >= 140 && hue(color) <= 240, color);
    }
    for (const [key, surface] of Object.entries(themeColorTokens(theme))) {
      if (!key.startsWith('--surface-') || surface.length !== 7) continue;
      assert.ok(contrast(theme.text, surface) >= 4.5, `text ${index} ${key}`);
      assert.ok(contrast(theme.accent, surface) >= 3, `accent ${index} ${key}`);
    }
    const loop = themeColorTokens(theme)['--loop-accent'];
    assert.ok(contrast(loop, theme.background) >= 4.5, `loop background ${index}`);
    assert.ok(contrast(loop, theme.surface) >= 4.5, `loop surface ${index}`);
    const roles = Object.values(appearance.roleColors);
    for (const key of ['background', 'surface', 'inspector', 'roleGroups']) {
      assert.ok(contrast(theme.accent, theme[key]) >= 4.5, `accent ${index} ${key}`);
    }
    assert.equal(new Set(roles).size, 5);
    for (const color of roles) {
      assert.ok(contrast(color, theme.surface) >= 3, `track ${index}`);
      for (const other of roles) if (other !== color) assert.ok(hueDistance(color, other) > 40);
      assert.ok(hueDistance(color, theme.accent) > 35);
      for (const themeColor of Object.values(theme)) {
        const distance = Math.hypot(...[1, 3, 5].map(offset =>
          (parseInt(color.slice(offset, offset + 2), 16) - parseInt(themeColor.slice(offset, offset + 2), 16)) / 255));
        assert.ok(distance > .2, `track avoids theme ${index}`);
      }
    }
  }
  assert.equal(modes.size, 2);
  assert.equal(palettes.size, 1000);
  assert.deepEqual(randomAppearance(original, 123n), randomAppearance(original, 123n));
  assert.equal(original.colorAdjustments.hueShift, 50);
});

test('selection and score shading follow light and dark theme backgrounds', () => {
  const base = defaultAppearance().themeColors;
  const dark = themeColorTokens({ ...base, roleGroups: '#808080', background: '#101010', surface: '#202020' });
  const light = themeColorTokens({ ...base, roleGroups: '#808080', background: '#f0f0f0', surface: '#e0e0e0' });
  assert.equal(dark['--surface-role-group-selected'], '#5e5e5e');
  assert.equal(light['--surface-role-group-selected'], '#a2a2a2');
  assert.equal(dark['--score-out-of-scale'], '#1a1a1a');
  assert.equal(light['--score-out-of-scale'], '#e6e6e6');
});

test('loop yellow adapts to light surfaces and stays bright on dark surfaces', () => {
  const base = defaultAppearance().themeColors;
  assert.equal(themeColorTokens(base)['--loop-accent'], '#ffe16a');
  for (const background of ['#ffffff', '#e6e8eb', '#080e16', '#173344']) {
    const colors = { ...base, background, surface: background, border: background };
    const tokens = themeColorTokens(colors);
    assert.ok(contrast(tokens['--loop-accent'], background) >= 4.5, background);
    assert.ok(contrast(tokens['--loop-line'], background) < contrast(tokens['--loop-accent'], background), 'markers are softer than the toggle');
    assert.ok(contrast(tokens['--loop-line'], background) >= 3, 'markers remain visible');
    assert.equal(tokens['--loop-surface'], tokens['--loop-accent'] + '1f');
    assert.equal(tokens['--loop-outline'], tokens['--loop-accent'] + '33');
  }
});

test('neutral adjustments preserve every chosen color exactly', () => {
  const appearance = defaultAppearance();
  for (const color of [...Object.values(appearance.themeColors), ...Object.values(appearance.roleColors)]) {
    assert.equal(adjustColor(color, adjustments()), color);
  }
});

test('role backgrounds follow the theme surface and role foregrounds follow its text', () => {
  const theme = { ...defaultAppearance().themeColors, surface: '#ffffff', text: '#000000' };
  for (const role of ['melody', 'bass', 'chords', 'arpeggio', 'drums']) {
    const tokens = roleColorTokens(role, '#804020', theme);
    assert.equal(tokens[`--role-${role}-dark`], '#b38c79');
    assert.equal(tokens[`--role-${role}-surface`], '#dbcac1');
    assert.equal(tokens[`--role-${role}-header`], '#c6a99b');
    assert.equal(tokens[`--role-${role}-cell-surface`], '#e4d7d0');
    assert.equal(tokens[`--role-${role}-text`], '#1c0e07');
    assert.ok(contrast(theme.text, tokens[`--role-${role}-header`]) > 4.5);
    assert.ok(contrast(tokens[`--role-${role}-text`], tokens[`--role-${role}-surface`]) > 4.5);
    if (role === 'drums') assert.equal(tokens['--role-drum-cell-surface'], tokens['--role-drums-cell-surface']);
    const dark = roleColorTokens(role, '#804020', defaultAppearance().themeColors);
    assert.ok(luminance(dark[`--role-${role}-cell-surface`]) < .1);
  }
});

test('brightness scales RGB channels and clips at the channel limits', () => {
  assert.equal(adjustColor('#804020', adjustments({ brightness: 50 })), '#c06030');
  assert.equal(adjustColor('#804020', adjustments({ brightness: -50 })), '#402010');
  assert.equal(adjustColor('#ffcc00', adjustments({ brightness: 50 })), '#ffff00');
  assert.equal(adjustColor('#000000', adjustments({ brightness: 50 })), '#000000');
});

test('hue wraps in either direction while saturation scales independently', () => {
  assert.equal(adjustColor('#ff0000', adjustments({ hueShift: 50 })), '#00ffff');
  assert.equal(adjustColor('#ff0000', adjustments({ hueShift: -50 })), '#00ffff');
  assert.equal(adjustColor('#00ff00', adjustments({ hueShift: -25 })), '#ff8000');
  assert.equal(adjustColor('#ff0000', adjustments({ saturation: -50 })), '#ff8080');
  assert.equal(adjustColor('#804040', adjustments({ saturation: 50 })), '#802020');
  assert.equal(adjustColor('#808080', adjustments({ hueShift: 50, saturation: 50 })), '#808080');
});

test('adjustments survive serialization and reject invalid imported values', () => {
  const appearance = { ...defaultAppearance(), hueShiftRoles: true, colorAdjustments: adjustments({ brightness: 20, hueShift: -50, saturation: 50 }) };
  assert.deepEqual(decodeAppearance(JSON.parse(JSON.stringify(appearance))), appearance);
  const { colorAdjustments, ...original } = appearance;
  assert.deepEqual(decodeAppearance(original).colorAdjustments, adjustments());
  for (const value of [null, [], {}, { ...colorAdjustments, brightness: 51 },
    { ...colorAdjustments, saturation: -51 }, { ...colorAdjustments, hueShift: '10' }]) {
    assert.equal(decodeAppearance({ ...appearance, colorAdjustments: value }), null);
  }
  const { hueShiftRoles, ...withoutRoleToggle } = appearance;
  assert.equal(decodeAppearance(withoutRoleToggle).hueShiftRoles, false);
  assert.equal(defaultAppearance().hueShiftRoles, false);
  assert.equal(decodeAppearance({ ...appearance, hueShiftRoles: 'true' }), null);
});
