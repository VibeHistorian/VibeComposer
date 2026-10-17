const assert = require('node:assert/strict');
const test = require('node:test');
const { loadMusic } = require('./music-loader.cjs');
const { adjustColor, decodeAppearance, defaultAppearance } = loadMusic('../ui/appearance.ts');
const adjustments = (overrides = {}) => ({ brightness: 0, hueShift: 0, saturation: 0, ...overrides });

test('neutral adjustments preserve every chosen color exactly', () => {
  const appearance = defaultAppearance();
  for (const color of [...Object.values(appearance.themeColors), ...Object.values(appearance.roleColors)]) {
    assert.equal(adjustColor(color, adjustments()), color);
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
  const appearance = { ...defaultAppearance(), colorAdjustments: adjustments({ brightness: 20, hueShift: -50, saturation: 50 }) };
  assert.deepEqual(decodeAppearance(JSON.parse(JSON.stringify(appearance))), appearance);
  const { colorAdjustments, ...original } = appearance;
  assert.deepEqual(decodeAppearance(original).colorAdjustments, adjustments());
  for (const value of [null, [], {}, { ...colorAdjustments, brightness: 51 },
    { ...colorAdjustments, saturation: -51 }, { ...colorAdjustments, hueShift: '10' }]) {
    assert.equal(decodeAppearance({ ...appearance, colorAdjustments: value }), null);
  }
});
