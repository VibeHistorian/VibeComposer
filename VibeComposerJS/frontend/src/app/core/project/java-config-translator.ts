import { ARRANGED_PARTS, PART_TYPES, type ArrangedPart } from '../music/part-role';
import { DEFAULT_PROJECT, DEFAULT_TRACKS } from './project-defaults';
import { SECTION_TYPES, type ArrangementSection, type SectionMusicalContext, type SectionType } from './arrangement.model';
import type { CompositionProject } from './project.model';
import type { CompositionTrack } from './track.model';
import type { MelodyGlobalSettings, PartSettingsPatch } from '../music/part-settings.model';
import type { PhraseNote } from '../music/phrase-note';
import { PART_CONTROLS, decodePartPatch, partValuesEqual, settingsValues, validVelocityRange } from '../music/part-settings';
import { CHORD_SPICES, chordPitches, getPitchClass, type ProgressionChord } from '../music/harmony';
import { convertScalePitch, scaleNotes, type ScaleMode } from '../music/scales';
import { STATIC_RHYTHM_DEFINITIONS } from '../music/rhythm-patterns';
import { decodeMelodyGlobal } from '../music/melody-global-settings';
import { decodeHarmonySettings } from '../music/harmony-voicing';
import { DEFAULT_TRACK_POLICIES } from '../music/track-generation';
import { DEFAULT_INSTRUMENT_POLICIES } from '../music/instrument-generation';
import { applyPartTiming } from '../music/part-processing';
import { swingNotes } from '../music/phrase-swing';

export interface JavaTranslationDiagnostic { readonly path: string; readonly code: string; readonly message: string }
export interface JavaTranslationResult {
  readonly project: CompositionProject;
  readonly diagnostics: readonly JavaTranslationDiagnostic[];
  readonly savedPhraseCount: number;
}
export interface JavaTranslationOptions {
  readonly name?: string;
  /** Browser DOMParser by default; inject an XML parser for Node/CLI use. */
  readonly parseXml?: (xml: string) => Document;
}

const PART_TAGS = { melody: 'melodyParts', bass: 'bassParts', chords: 'chordParts', arpeggio: 'arpParts', drums: 'drumParts' } as const;
const JAVA_SCALES: Readonly<Record<string, ScaleMode>> = {
  IONIAN: 'major', AEOLIAN: 'natural-minor', DORIAN: 'dorian', PHRYGIAN: 'phrygian', LYDIAN: 'lydian',
  MIXOLYDIAN: 'mixolydian', LOCRIAN: 'locrian', BLUES: 'blues', HARM_MINOR: 'harmonic-minor', TURKISH: 'turkish',
  INDIAN: 'indian', HARM_MAJOR: 'harmonic-major', WHISKEY: 'whiskey', DOUBLE_HARM: 'double-harmonic',
  PENTA_MAJOR: 'pentatonic-major', PENTA_MINOR: 'pentatonic-minor', HUNGARIAN_MINOR: 'hungarian-minor',
};
const JAVA_RHYTHMS: Readonly<Record<string, string>> = Object.fromEntries([
  ...Object.entries(STATIC_RHYTHM_DEFINITIONS).map(([key, value]) => [value.javaName, key]),
  ['EUCLID', 'euclid'], ['CUSTOM', 'custom'], ['MELODY1', 'melody'],
]);
const JAVA_ARPS: Readonly<Record<string, string>> = {
  UP: 'up', DOWN: 'down', UPDOWN: 'up-down', DOWNUP: 'down-up', FROG_U: 'frog-up', FROG_D: 'frog-down', RANDOM: 'random', CUSTOM: 'custom',
};
const MELODY_GLOBAL: Readonly<Record<string, keyof MelodyGlobalSettings>> = {
  contextualMelody: 'contextualMelody', melodyRangeMin: 'rangeMin', melodyRangeMax: 'rangeMax',
  melodyRangeRandomizeOnCompose: 'rangeRandomizeOnCompose', melodyTension: 'tension',
  melodyEnding: 'ending', melodyForm: 'form', melodyStyle: 'style',
  melodyChromaticApproaches: 'chromaticApproaches', melodyPhraseBars: 'phraseBars', melodyBeatsPerBar: 'beatsPerBar',
  melodyBlockTargetMode: 'blockTargetMode', melodyTonicNoteTarget: 'tonicNoteTarget', melodyModeNoteTarget: 'modeNoteTarget',
  melodyChordNoteTarget: 'chordNoteTarget', melodyReplaceAvoidNotes: 'replaceAvoidNotes', melodyEmphasizeKey: 'emphasizeKey',
  melodyPatternFlip: 'patternFlip', melodyMaxDirChanges: 'maxDirectionChanges',
  melodyFillPausesPerChord: 'fillPausesPerChord', melodyPatternEffect: 'patternEffect', melodyArpySurprises: 'arpySurprises',
  melodyTargetNoteVariation: 'targetNoteVariation', noteTargetDirectionChoice: 'targetDirection',
  melodyUseDirectionsFromProgression: 'useDirectionsFromProgression', melody1ForcePatterns: 'forceFirstOutline',
  melodyNewBlocksChance: 'newBlocksChance', melodyBlockTypePreference: 'blockTypeWeights', melodyBlockChoicePreference: 'blockChangePreference',
};
const elements = (node: Element): Element[] => Array.from(node.childNodes).filter(child => child.nodeType === 1) as Element[];
const tag = (node: Element): string => node.localName || node.nodeName;
const clampMidi = (value: number): number => Math.max(0, Math.min(127, value));

/** Import musical state, not the Java UI or its arrangement-generation outline.
 * No Angular, storage or file writes: callers can inspect diagnostics before loading.
 */
export function translateJavaConfig(xml: string, options: JavaTranslationOptions = {}): JavaTranslationResult {
  if (/<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error('DTD/entity declarations are not supported in project XML.');
  const document = options.parseXml ? options.parseXml(xml) : new DOMParser().parseFromString(xml, 'application/xml');
  const root = document.documentElement;
  if (!root || tag(root) !== 'GUIConfig' || document.getElementsByTagName('parsererror').length) throw new Error('Expected a well-formed GUIConfig XML document.');
  const diagnostics: JavaTranslationDiagnostic[] = [], used = new Set<Element>();
  const warn = (path: string, code: string, message: string) => diagnostics.push({ path, code, message });
  const all = (node: Element, name: string) => elements(node).filter(child => {
    if (tag(child) !== name) return false;
    if (child.getAttribute('xsi:nil') === 'true') { ignore(child); return false; }
    return true;
  });
  const one = (node: Element, name: string) => all(node, name)[0];
  const ignore = (node: Element | undefined): void => { if (node) { used.add(node); elements(node).forEach(ignore); } };
  const text = (node: Element, name: string): string | undefined => {
    const child = one(node, name); if (!child || child.getAttribute('xsi:nil') === 'true') { ignore(child); return undefined; }
    ignore(child); return child.textContent?.trim() ?? '';
  };
  const numeric = (node: Element, name: string, fallback?: number): number | undefined => {
    const value = text(node, name); if (value === undefined) return fallback;
    if (value === '' || !Number.isFinite(Number(value))) throw new Error(`${tag(node)}.${name}: expected a finite number.`);
    return Number(value);
  };
  const bool = (node: Element, name: string, fallback = false): boolean => {
    const value = text(node, name); if (value === undefined) return fallback;
    if (value !== 'true' && value !== 'false') throw new Error(`${tag(node)}.${name}: expected true or false.`);
    return value === 'true';
  };
  const numbers = (node: Element, name: string): number[] => all(node, name).flatMap(child => {
    ignore(child); const value = child.textContent?.trim();
    return value ? value.split(/[\s,]+/).map(item => {
      if (!Number.isFinite(Number(item))) throw new Error(`${name}: invalid numeric list.`);
      return Number(item);
    }) : [];
  });
  const scaleFrom = (value: string | undefined): ScaleMode => {
    if (value === undefined) return 'major';
    const result = JAVA_SCALES[value]; if (!result) throw new Error(`Unsupported Java scale ${value}.`); return result;
  };
  const scale = scaleFrom(text(root, 'scaleMode'));
  const snap = bool(root, 'transposedNotesForceScale');
  const customSnap = bool(root, 'customMidiForceScale');
  const seedText = root.getAttribute('randomSeed') || '0';
  if (!/^-?\d+$/.test(seedText) || BigInt(seedText) < -(1n << 63n) || BigInt(seedText) >= 1n << 63n) throw new Error('randomSeed must be a Java long.');
  const bpm = numeric(root, 'bpm', 120)!;
  if (bpm < 30 || bpm > 240 || !Number.isInteger(bpm)) warn('GUIConfig.bpm', 'adjusted', `BPM ${bpm} becomes ${Math.max(30, Math.min(240, Math.round(bpm)))}.`);
  const chordGen = one(root, 'chordGenSettings');
  const chordTranspose = chordGen ? bool(chordGen, 'useTranspose', true) : true;
  const octaveAdjustments = bool(root, 'useOctaveAdjustments');
  const correctMelodyNotes = bool(root, 'randomArpCorrectMelodyNotes');
  const globalSwing = numeric(root, 'globalSwingOverride');
  const volumeScaling = bool(root, 'scaleMidiVelocityInArrangement');
  const globalMultiplierIndex = numeric(root, 'beatDurationMultiplierIndex', 1)!;
  const multiplier = (index: number): 0.5 | 1 | 2 => {
    if (![0,1,2].includes(index)) throw new Error('Unsupported beat duration multiplier index.');
    return ([0.5,1,2] as const)[index];
  };

  // Java XML chord symbols describe canonical C/Ionian pitches. JSON Chordlets
  // describe sounding pitches; preserve inversions and non-triad mode voicings.
  const chord = (symbol: string, mode: ScaleMode): ProgressionChord => {
    const match = /^([A-G])([#b♯♭]*)([^.]*)(?:\.(-?\d+))?$/.exec(symbol.trim());
    if (!match || !CHORD_SPICES.includes(match[3] as typeof CHORD_SPICES[number])) throw new Error(`Unsupported chord symbol ${symbol}.`);
    const rootPitch = getPitchClass(match[1] + match[2].replace(/#/g, '♯').replace(/b/g, '♭'));
    if (rootPitch === undefined) throw new Error(`Invalid chord root ${symbol}.`);
    const source: ProgressionChord = { root: rootPitch, spice: match[3] as ProgressionChord['spice'], inversion: 0 };
    const pitches = chordPitches(source, 60).map(pitch => convertScalePitch(pitch, scaleNotes('major'), scaleNotes(mode), snap));
    const mappedRoot = convertScalePitch(60 + rootPitch, scaleNotes('major'), scaleNotes(mode), snap);
    const intervals = pitches.map(pitch => pitch - mappedRoot);
    const spice = CHORD_SPICES.find(spice => partValuesEqual(chordPitches({ root: 0, spice, inversion: 0 }, 0), intervals));
    const inversion = Number(match[4] ?? 0);
    if (Math.abs(inversion) > 5) throw new Error(`Chord inversion outside the supported range: ${symbol}.`);
    return { root: mappedRoot % 12, spice: spice ?? '', inversion, ...(spice === undefined ? { intervals } : {}) };
  };
  const chordList = (value: string, mode: ScaleMode) => value.split(',').map(symbol => chord(symbol, mode));
  const globalChordText = text(root, 'customChords');
  const globalChordsEnabled = bool(root, 'customChordsEnabled');
  const globalDurationText = numbers(root, 'customChordDurations');
  const globalDurationsEnabled = bool(root, 'customDurationsEnabled');
  const globalChords = globalChordsEnabled && globalChordText && globalChordText !== '?' ? chordList(globalChordText, scale) : undefined;
  if (!globalChords) warn('GUIConfig.customChords', 'missing-harmony', 'No explicit global chord progression is saved; using the workspace default progression.');
  const progression = globalChords ? globalChords.map(() => 1) : [...DEFAULT_PROJECT.progression];
  const globalDurations = progression.map((_, index) => globalDurationsEnabled && globalDurationText.length
    ? globalDurationText[index % globalDurationText.length] : numeric(root, 'fixedDuration', 4)!);
  const globals: Record<string, unknown> = {};
  for (const [source, target] of Object.entries(MELODY_GLOBAL)) {
    if (!one(root, source)) continue;
    const array = target === 'blockTypeWeights' || target === 'blockChangePreference';
    const value = array ? numbers(root, source) : text(root, source)!;
    globals[target] = array ? value : value === 'true' || value === 'false' ? value === 'true'
      : /^-?\d+$/.test(value as string) ? Number(value) : value;
  }
  const melodyGlobal = decodeMelodyGlobal(globals);
  if (!melodyGlobal) throw new Error('Java melody globals are outside the current supported ranges.');

  const readPart = (node: Element, role: ArrangedPart, path: string): PartSettingsPatch => {
    const result: Record<string, unknown> = {};
    for (const control of PART_CONTROLS[role]) {
      const name = control.key === 'rhythm' ? 'pattern' : role === 'chords' && control.key === 'noteLengthPercent' ? 'noteLengthMultiplier' : control.key;
      if (!one(node, name)) continue;
      if (['pattern', 'useCustomVelocities', 'humanize'].includes(control.key)) continue;
      const value = ['integers','pattern','velocities'].includes(control.kind) ? numbers(node, name)
        : control.kind === 'boolean' ? bool(node, name) : control.kind === 'number' || control.kind === 'integer' ? numeric(node, name) : text(node, name);
      result[control.key] = control.key === 'rhythm' ? JAVA_RHYTHMS[value as string] : value;
      if (result[control.key] === undefined) { delete result[control.key]; warn(`${path}.${name}`, 'unsupported', `Unsupported ${role} rhythm ${value}; using its default.`); }
    }
    if (role === 'bass') {
      // Java's alternating branch wins over the stored rhythm grid.
      if (bool(node, 'alternatingRhythm')) result['rhythm'] = 'bass1';
      if (result['rhythm'] === 'melody') { delete result['rhythm']; warn(`${path}.pattern`, 'unsupported', 'Bass following Melody has no current TypeScript consumer.'); }
      if (one(node, 'doubleOct')) result['octaveInterval'] = bool(node, 'doubleOct');
    }
    if (role === 'arpeggio') {
      if (one(node, 'arpPattern')) {
        const name = text(node, 'arpPattern')!; if (!JAVA_ARPS[name]) throw new Error(`Unsupported arpeggio pattern ${name}.`);
        result['pattern'] = JAVA_ARPS[name];
      }
      result['useOctaveAdjustments'] = octaveAdjustments;
      result['correctMelodyNotes'] = correctMelodyNotes;
    }
    if (role === 'chords' && !chordTranspose) result['transpose'] = 0;
    if (globalSwing !== undefined && role !== 'bass') result['swingPercent'] = globalSwing;
    if (one(node, 'customVelocities') && role !== 'melody') result['useCustomVelocities'] = numbers(node, 'customVelocities').length > 0;
    if (role === 'drums' && one(node, 'velocityPattern')) result['ghosts'] = bool(node, 'velocityPattern');
    for (const [field, value] of Object.entries(result)) if (!decodePartPatch(role, { [field]: value })) {
      delete result[field];
      warn(`${path}.${field}`, 'unsupported-value', 'This saved value exceeds the current setting range or list capacity; omitted rather than truncated.');
    }
    const decoded = decodePartPatch(role, result as PartSettingsPatch);
    if (!decoded || !validVelocityRange(decoded, role)) throw new Error(`${path}: part settings cannot be represented by the current project model.`);
    return decoded;
  };

  const arrangementXml = one(root, 'actualArrangement');
  if (!arrangementXml) throw new Error('GUIConfig.actualArrangement is required; arrangement is only a generation outline.');
  const sectionsXml = all(arrangementXml, 'sections');
  if (!sectionsXml.length || sectionsXml.length > 32) throw new Error('Expected 1–32 actual arrangement sections.');
  const tracks: CompositionTrack[] = [];
  const sources = new Map<string, Element>();
  const orderOf = (node: Element) => {
    const order = Number(node.getAttribute('order') || '1');
    if (!Number.isInteger(order) || order < 1) throw new Error('Part order must be a positive integer.'); return order;
  };
  for (const role of ARRANGED_PARTS) {
    const sourceNodes = all(root, PART_TAGS[role]);
    const orders = new Map<number, Element>();
    sourceNodes.forEach(node => { const order = orderOf(node); if (orders.has(order)) throw new Error(`Duplicate global ${role} order ${order}.`); orders.set(order, node); });
    sectionsXml.forEach(section => all(section, PART_TAGS[role]).forEach(node => { if (!orders.has(orderOf(node))) orders.set(orderOf(node), node); }));
    for (const [order, node] of [...orders].sort(([left], [right]) => left - right)) {
      const id = `track-${role}-${order}`, path = `GUIConfig.${PART_TAGS[role]}[${order}]`;
      const defaults = DEFAULT_TRACKS.find(track => track.role === role)!;
      const settings = { ...defaults.generatorSettings, ...readPart(node, role, path) };
      const orderOffset = numeric(node, 'orderOffset', order)!;
      if (!Number.isInteger(orderOffset) || orderOffset < -2147483648 || orderOffset > 2147483647) throw new Error(`${path}.orderOffset: expected a Java int.`);
      const instrument = numeric(node, 'instrument', role === 'drums' ? 36 : defaults.mix.program)!;
      if (role === 'drums') (settings as { pitch: number }).pitch = instrument;
      const track = { ...defaults, id, orderOffset, name: `${({ melody:'M', bass:'B', chords:'C', arpeggio:'A', drums:'D' })[role]}${order}`,
        midiChannel: role === 'drums' ? 10 : numeric(node, 'midiChannel', defaults.midiChannel)!, generatorSettings: settings,
        mix: { program: role === 'drums' ? 0 : instrument, volumePercent: numeric(node, 'sliderVolume', 100)!,
          panPercent: (numeric(node, 'sliderPan', 50)! - 50) * 2, muted: bool(node, 'muted'), solo: false },
      } as CompositionTrack;
      const pool = text(node, 'instPool');
      if (pool && ['ALL','MELODY','BASS','CHORD','PLUCK','LONG_PAD','DRUM'].includes(pool)) Object.assign(track, { instrumentPool: pool });
      tracks.push(track); sources.set(id, node);
    }
  }
  if (tracks.length > 64) throw new Error('The Java configuration exceeds the workspace capacity of 64 tracks.');

  const library = new Map<string, Element>();
  for (const map of all(root, 'patternMap')) {
    const part = map.getAttribute('part') || '0';
    for (const entry of all(one(map, 'partOrderPatternMap') ?? map, 'entry')) {
      const order = text(entry, 'key'); const value = one(entry, 'value');
      if (!value) continue;
      for (const pattern of all(one(value, 'namedNotesMap') ?? value, 'entry')) {
        const name = text(pattern, 'key'), notes = one(pattern, 'value');
        if (notes && name) { library.set(`${part}/${order}/${name}`, notes); ignore(pattern); }
      }
    }
  }
  const selectedPatterns = new Set<Element>();
  let savedPhraseCount = 0, pendingKeyChange = 0, pendingScale = scale;
  const arrangement: ArrangementSection[] = sectionsXml.map((section, index) => {
    const path = `GUIConfig.actualArrangement.sections[${index}]`, id = `section-${index + 1}`;
    const javaType = section.getAttribute('type') || 'CLIMAX';
    const type = javaType === 'PREVIEW' ? 'CLIMAX' : javaType;
    if (!SECTION_TYPES.includes(type as SectionType)) throw new Error(`${path}: unsupported section type ${javaType}.`);
    const config = one(section, 'secConfig');
    const context: Record<string, unknown> = {};
    const mode = pendingScale;
    if (mode !== scale) context['scale'] = mode;
    if (pendingKeyChange !== 0) context['keyChange'] = pendingKeyChange;
    if (config) {
      for (const [source, target] of [['sectionBpm','tempoBpm'], ['sectionSwingOverride','swingPercent']] as const) {
        const value = numeric(config, source); if (value !== undefined) context[target] = value;
      }
    }
    const mult = multiplier(config ? numeric(config, 'beatDurationMultiplierIndex', globalMultiplierIndex)! : globalMultiplierIndex);
    const variations = numbers(section, 'sectionVariations');
    const customChords = bool(section, 'customChordsEnabled');
    const customDurations = bool(section, 'customDurationsEnabled');
    const alternateChords = bool(section, 'displayAlternateChords');
    const storedChordText = text(section, 'customChords');
    const sectionChordText = (customChords || alternateChords) && storedChordText && storedChordText !== '?' ? storedChordText : undefined;
    let chords = sectionChordText ? chordList(sectionChordText, mode)
      : globalChordsEnabled && globalChordText && globalChordText !== '?' ? chordList(globalChordText, mode) : undefined;
    const customDurationValues = numbers(section, 'customDurations');
    const generatedDurations = numbers(section, 'generatedSectionBeatDurations');
    const knownGlobalTiming = !!globalChords || globalDurationsEnabled && globalDurationText.length > 0;
    const effectiveGlobalDurations = globalDurations.map(duration => duration * multiplier(globalMultiplierIndex));
    // SectionGenerationPlanner.prepareCustomProgression uses literal custom
    // section durations, otherwise the CURRENT global progression. The saved
    // generatedSectionBeatDurations cache can describe an older generation.
    // Both generated caches and explicit section durations are already beats:
    // applying the beat-duration multiplier to either would scale them twice.
    let durations: number[];
    if (customDurations) {
      if (!customDurationValues.length) throw new Error(`${path}: enabled custom durations are empty.`);
      durations = customDurationValues;
    } else if (knownGlobalTiming) {
      durations = sectionChordText ? Array.from({ length: chords!.length }, (_, slot) =>
        effectiveGlobalDurations[slot] ?? 4 * mult) : [...effectiveGlobalDurations];
    } else {
      durations = generatedDurations.length ? [...generatedDurations] : [...effectiveGlobalDurations];
    }
    if (generatedDurations.length && (customDurations || knownGlobalTiming) && !partValuesEqual(durations, generatedDurations)) {
      warn(path, 'stale-duration-cache', 'Active custom/global durations take precedence over the saved generated-duration cache.');
    }
    const chordCount = chords?.length ?? progression.length;
    if (durations.length !== chordCount) {
      warn(path, 'harmony-length', `Saved durations have ${durations.length} slots, while chord names have ${chordCount}; chord names repeat/trim to the saved duration slots.`);
      if (chords) chords = durations.map((_, slot) => chords![slot % chords!.length]);
    }
    // Java removes the penultimate chord and sustains the final chord through both durations.
    const keyChangeType = config ? numeric(config, 'customKeyChangeType', 0)! : 0;
    const twoFiveOne = variations[4] > 0 && keyChangeType !== 2 && (keyChangeType === 1 || text(root, 'keyChangeType') === 'TWOFIVEONE');
    if (variations[0] > 0 && !twoFiveOne && durations.length >= 3) {
      const n = durations.length; durations.splice(n - 2, 2, durations[n - 2] + durations[n - 1]);
      if (chords) chords.splice(n - 2, 1);
      else warn(path, 'missing-harmony', 'Skip N−1 was realized in durations, but the source has no explicit chords.');
    }
    if (durations.some(value => !Number.isFinite(value) || value < 0.25 || value > 32) || durations.length > 32) throw new Error(`${path}: chord durations exceed the current supported range.`);
    const repeats = numeric(section, 'measures', 1)!;
    const measures = repeats * durations.length;
    if (!Number.isInteger(repeats) || repeats < 1 || measures > 32) throw new Error(`${path}: section exceeds 32 chord slots.`);
    const length = durations.reduce((sum, value) => sum + value, 0) * repeats;
    const presence: Record<string, boolean> = {}, overrides: Record<string, PartSettingsPatch> = {}, phrases: Record<string, PhraseNote[]> = {};
    const flags = new Map<string, boolean[]>();
    const partVariations = one(one(section, 'partVariations') ?? section, 'map');
    for (const entry of partVariations ? all(partVariations, 'entry') : []) {
      const part = text(entry, 'key'), value = one(entry, 'value'); if (!value) continue;
      for (const row of all(value, 'inclElems')) {
        const order = text(row, 'part'), raw = text(row, 'options') ?? '';
        if (raw.split(/\s+/).some(value => value !== 'true' && value !== 'false')) throw new Error(`${path}: invalid presence flags.`);
        flags.set(`${part}/${order}`, raw.split(/\s+/).map(value => value === 'true'));
      }
    }
    const patternRefs = new Map<string, { name: string; sourceKey: string }>();
    const patternMaps = all(section, 'pattern');
    for (const [mapIndex, map] of patternMaps.entries()) {
      for (const entry of all(one(map, 'usedPatternMap') ?? map, 'entry')) {
        const value = one(entry, 'value'); if (!value) continue;
        const part = text(value, 'part'), targetOrder = text(entry, 'key'), order = text(value, 'partOrder') ?? targetOrder, name = text(value, 'name');
        // Section.patterns is a five-element role-ordered list. Older JAXB files
        // serialize part="0" on every wrapper. The value points to the SOURCE
        // role/order, which may differ when a pattern was copied between parts.
        const targetPart = patternMaps.length === 5 ? mapIndex : map.getAttribute('part') || part;
        if (name !== undefined) patternRefs.set(`${targetPart}/${targetOrder}`, { name, sourceKey: `${part}/${order}/${name}` });
        ignore(entry);
      }
    }
    const volumes = numbers(section, 'instVelocityMultiplier');
    for (const track of tracks) {
      const part = PART_TYPES[track.role], order = track.id.substring(track.id.lastIndexOf('-') + 1), key = `${part}/${order}`;
      const localNodes = all(section, PART_TAGS[track.role]);
      const local = localNodes.find(node => String(orderOf(node)) === order);
      const node = local ?? sources.get(track.id)!;
      const localSettings = local ? readPart(local, track.role, `${path}.${PART_TAGS[track.role]}[${order}]`) : {};
      // A Java section part list is an independent snapshot, including values
      // currently equal to the globals. Preserve that boundary on later edits.
      const defaults = DEFAULT_TRACKS.find(source => source.role === track.role)!;
      const full = { ...track.generatorSettings,
        ...(local ? settingsValues(defaults.generatorSettings, track.role) : {}), ...localSettings } as Record<string, unknown>;
      const row = flags.get(key);
      // Presence is realized state. Missing rows in an existing presence map mean absent;
      // without a saved presence map we use membership, never generation chances.
      presence[track.id] = flags.size ? row?.[0] ?? false : !localNodes.length || !!local;
      if (local) {
        for (const name of ['instrument','midiChannel','sliderVolume','sliderPan','instPool','muted']) ignore(one(local, name));
      }
      if (row?.[1] && ['melody','chords','arpeggio'].includes(track.role)) full['transpose'] = Number(full['transpose'] ?? 0) + 12;
      if (track.role === 'melody' && variations[3] > 0) full['speed'] = 100;
      if (context['swingPercent'] !== undefined && track.role !== 'bass') full['swingPercent'] = context['swingPercent'];
      if ((track.role === 'drums' ? row?.[1] : row?.[2]) && ['chords','arpeggio','drums'].includes(track.role)) { full['chordSpanFill'] = 'ALL'; full['fillFlip'] = false; }
      if (row?.slice(2).some(Boolean)) warn(`${path}.${track.id}`, 'part-variation', 'Some Java part variation flags have no exact settings equivalent. Supported transpose/fill flags were materialized.');
      if (volumeScaling && volumes[part] !== undefined && volumes[part] !== 100) {
        for (const field of ['velocityMin','velocityMax']) full[field] = Math.max(1, Math.min(127, Math.trunc(Number(full[field] ?? (field === 'velocityMin' ? 69 : 90)) * volumes[part] / 100)));
        if (Array.isArray(full['customVelocities'])) full['customVelocities'] = (full['customVelocities'] as number[]).map(value => clampMidi(Math.trunc(value * (volumes[3] ?? 100) / 100)));
      }
      const patch = Object.fromEntries(Object.entries(full).filter(([field, value]) => value !== undefined && field !== 'algorithm' && field !== 'pitch'
        && (local || !partValuesEqual(value, (track.generatorSettings as unknown as Record<string, unknown>)[field])))) as PartSettingsPatch;
      if (Object.keys(patch).length) overrides[track.id] = patch;
      const reference = patternRefs.get(key);
      const baseName = /^(CHORUS|CLIMAX|PREVIEW)/.test(javaType) ? 'MAIN' : javaType.startsWith('VERSE') ? 'VERSE' : 'INST';
      const direct = reference === undefined ? undefined : library.get(reference.sourceKey);
      const fallback = library.get(`${key}/${baseName}`);
      const stored = reference?.name.toUpperCase() === 'NONE' ? undefined
        : direct && bool(direct, 'applied') ? direct : fallback && bool(fallback, 'applied') ? fallback : undefined;
      if (stored) {
        selectedPatterns.add(stored);
        phrases[track.id] = translatePhrase(stored, track.role, full, mode, Number(context['keyChange'] ?? 0), length, `${id}-${track.id}`);
        savedPhraseCount++;
      } else if (reference && reference.name.toUpperCase() !== 'NONE') warn(`${path}.${track.id}`, 'missing-pattern', `No applied note payload for ${reference.name}; this section/track remains generated.`);
    }
    if (variations[4] > 0) {
      const next = config ? numeric(config, 'customKeyChange') : undefined;
      if (next !== undefined) pendingKeyChange = next;
      else warn(path, 'key-change', 'A generated key change has no stored transpose; keeping the preceding key.');
      const nextScale = config ? text(config, 'customScale') : undefined;
      if (nextScale) pendingScale = scaleFrom(nextScale);
      if (twoFiveOne) {
        context['keyChangeType'] = 'two-five-one';
        if (chords) warn(path, 'cadence', 'The key-change type is retained, but the workspace gives explicit section chords precedence over automatic ii–V preparation.');
      }
    }
    if (numeric(section, 'transitionType', 0) !== 0) warn(path, 'transition', 'Java transition processing is not represented in project settings.');
    ignore(one(section, 'secConfig'));
    return { id, type: type as SectionType, measures, trackPresence: presence,
      chordDegrees: durations.map((_, slot) => progression[slot % progression.length]),
      ...(chords ? { customChords: chords } : {}), customDurations: durations,
      ...(Object.keys(context).length ? { context: context as SectionMusicalContext } : {}),
      useMelodyHarmony: variations[1] > 0, swapMelody: variations[2] > 0 && variations[0] === 0,
      trackPartOverrides: overrides, ...(Object.keys(phrases).length ? { trackPhrases: phrases } : {}),
    };
  });

  function translatePhrase(stored: Element, role: ArrangedPart, settings: Record<string, unknown>, mode: ScaleMode,
    keyChange: number, length: number, prefix: string): PhraseNote[] {
    const notes = all(stored, 'phraseNote').map(note => ({ midi: numeric(note, 'pitch', -1)!, rhythm: numeric(note, 'rv', 0)!,
      duration: numeric(note, 'duration', 0)!, offset: numeric(note, 'offset', 0)!, velocity: numeric(note, 'dynamic', 80)! }));
    const originalLength = notes.reduce((sum, note) => sum + note.rhythm, 0);
    if (originalLength <= 0) throw new Error(`${prefix}: saved phrase has no positive rhythmic length.`);
    const factor = length / originalLength;
    for (const note of notes) { note.rhythm *= factor; note.duration *= factor; note.offset *= factor; }
    const swing = role === 'bass' || role !== 'melody' && Number(settings['hitsPerPattern'] ?? 8) % 2 ? 50 : Number(settings['swingPercent'] ?? 50);
    swingNotes(notes, swing);
    const articulation = role === 'melody' || role === 'arpeggio' ? Number(settings['noteLengthMultiplier'] ?? 100) / 100 : 1;
    let cursor = 0;
    const starts = notes.map(note => { const start = cursor + note.offset; cursor += note.rhythm; return start; });
    cursor = 0;
    const result: PhraseNote[] = [];
    notes.forEach((note, index) => {
      const startBeat = cursor + note.offset; cursor += note.rhythm;
      if (note.midi < 0 || note.velocity <= 0 || note.duration <= 0) return;
      let midi = note.midi;
      if (role !== 'drums') {
        if (role === 'melody') {
          midi += Number(settings['transpose'] ?? 0);
          if (customSnap) midi = convertScalePitch(midi, scaleNotes('major'), scaleNotes('major'), true);
          midi = convertScalePitch(midi, scaleNotes('major'), scaleNotes(mode), snap);
        } else midi = convertScalePitch(midi, scaleNotes('major'), scaleNotes(mode), snap) + Number(settings['transpose'] ?? 0);
        midi += keyChange;
      }
      let duration = note.duration * articulation;
      if (articulation > 1) {
        const collision = [index + 1, index + 2].find(next => next < notes.length && note.midi === notes[next].midi);
        if (collision !== undefined) duration = Math.min(duration, starts[collision] - starts[index]);
      }
      if (duration <= 0) return;
      result.push({ id: `${prefix}-${index}`, midi: clampMidi(midi), startBeat: Math.max(0, startBeat),
        durationBeats: duration, velocity: Math.max(1, clampMidi(Math.trunc(note.velocity))) });
    });
    // Local phrases bypass generator processing. Bake offsets/feedback once, while
    // the global project transpose stays live in layOutTrackPhrase.
    return applyPartTiming(result, settings as PartSettingsPatch).filter(note => note.startBeat < length);
  }

  const harmonySettings = {
    generation: (text(root, 'progressionStrategy') ?? 'CURRENT') as import('../music/harmony-voicing').HarmonySettings['generation'],
    flattenVoicingChance: chordGen ? numeric(chordGen, 'flattenVoicingChance', 100)! : 100,
    flattenLargeChords: bool(root, 'spiceFlattenBigChords'), progressiveVoicing: bool(root, 'squishProgressively'),
  };
  if (!decodeHarmonySettings(harmonySettings)) throw new Error('Java harmony settings are outside the supported ranges.');
  const trackGenerationPolicies = structuredClone(DEFAULT_TRACK_POLICIES);
  for (const [sourceTag, role, mapping] of [
    ['arpGenSettings','arpeggio',{ includePresets:'patterns', shiftChance:'shiftChance', useTranspose:'transpose' }],
    ['chordGenSettings','chords',{ includePresets:'patterns', shiftChance:'shiftChance', useTranspose:'transpose', useDelay:'delay', useShortening:'varyLength', useSplit:'split', useStrum:'strum' }],
    ['drumGenSettings','drums',{ includePresets:'patterns', patternShiftChance:'shiftChance', useSlide:'randomOffset' }],
  ] as const) {
    const source = one(root, sourceTag); if (!source) continue;
    for (const [from, to] of Object.entries(mapping)) if (one(source, from)) {
      const raw = text(source, from)!;
      Object.assign(trackGenerationPolicies[role], { [to]: raw === 'true' || raw === 'false' ? raw === 'true' : Number(raw) });
    }
  }
  const instrumentGenerationPolicies = structuredClone(DEFAULT_INSTRUMENT_POLICIES);
  if (chordGen && one(chordGen, 'sustainChance')) Object.assign(instrumentGenerationPolicies.chords, { sustainChance: numeric(chordGen, 'sustainChance') });
  const project = { ...DEFAULT_PROJECT, name: options.name?.trim() || 'Imported Java composition', key: 'C', scale,
    tempoBpm: Math.max(30, Math.min(240, Math.round(bpm))), transposeSemitones: numeric(root, 'transpose', 0)!, seed: BigInt(seedText).toString(),
    progression, ...(globalChords ? { progressionChords: globalChords } : {}), melodyGlobal, harmonySettings,
    tracks, arrangement, editedPhrases: {}, trackGenerationPolicies, instrumentGenerationPolicies,
  } as CompositionProject;
  if (arrangement.reduce((sum, section) => sum + section.measures, 0) > 128) throw new Error('Actual arrangement exceeds 128 chord slots.');
  // Deliberately omitted by the translator's contract, rather than lost options.
  for (const child of elements(root)) if (/^(arrangement|soundbankName|humanizeNotes|humanizeDrums|\w+Enable|\w+Enabled|version)$/.test(tag(child))) ignore(child);
  for (const section of sectionsXml) for (const child of elements(section)) if (/Chance$|displayAlternateChords/.test(tag(child))) ignore(child);
  for (const node of sources.values()) for (const name of ['orderOffset','humanize']) ignore(one(node, name));
  const reportUnused = (node: Element, path: string): void => {
    const children = elements(node);
    if (!children.length && !used.has(node) && node.textContent?.trim()) warn(path, 'unsupported', 'No current project setting for this Java option.');
    children.forEach((child, index) => reportUnused(child, `${path}.${tag(child)}[${index}]`));
  };
  reportUnused(root, 'GUIConfig');
  for (const [key, notes] of library) if (!selectedPatterns.has(notes) && bool(notes, 'applied')) warn(`GUIConfig.patternMap.${key}`, 'unused-pattern', 'This applied library pattern is not selected by any actual section; the workspace has no unattached pattern library.');
  const grouped = new Map<string, { diagnostic: JavaTranslationDiagnostic; count: number }>();
  for (const diagnostic of diagnostics) {
    const key = diagnostic.code === 'unsupported' ? diagnostic.path.replace(/\[\d+\]/g, '') : `${diagnostic.code}:${diagnostic.path}`;
    const previous = grouped.get(key);
    if (previous) previous.count++;
    else grouped.set(key, { diagnostic, count: 1 });
  }
  return { project, diagnostics: [...grouped.values()].map(({ diagnostic, count }) => count === 1 ? diagnostic
    : { ...diagnostic, message: `${diagnostic.message} (${count} occurrences; first location shown.)` }), savedPhraseCount };
}
