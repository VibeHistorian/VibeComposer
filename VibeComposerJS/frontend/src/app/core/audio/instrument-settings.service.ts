import { computed, Injectable, signal } from '@angular/core';
import type { InstrumentEngineKind } from './instrument-engine';
import type { Sf2Bank } from './sf2-resolver';

export const MAX_SOUNDFONT_BYTES = 250 * 1024 * 1024;
export const MAX_LOADED_SOUNDFONTS = 3;
const NORMALIZATION_KEY = 'vibecomposer.instrument-normalization.v1';
function loadNormalization(): boolean {
  try { return localStorage.getItem(NORMALIZATION_KEY) === 'true'; } catch { return false; }
}
export interface LoadedSoundfont {
  readonly id: InstrumentEngineKind;
  readonly name: string;
  readonly size: number;
  readonly bank: Sf2Bank;
}
/** Session audio resources, independent of project history and MIDI program identity. */
@Injectable({ providedIn: 'root' })
export class InstrumentSettingsService {
  readonly normalization = signal(loadNormalization());
  readonly calibrationStatus = signal('');
  private calibrationRequest = 0;
  readonly kind = signal<InstrumentEngineKind>('synth');
  private readonly banks = signal<readonly LoadedSoundfont[]>([]);
  readonly loaded = this.banks.asReadonly();
  readonly busy = signal(false);
  readonly message = signal('');
  readonly options = computed(() => [{ value: 'synth', label: 'TinySynth' },
    { value: 'chiptune', label: 'Chiptune (TinySynth)' },
    ...this.loaded().map(bank => ({ value: bank.id, label: bank.name }))]);
  private nextId = 0;

  constructor() { void this.prepareNormalization(); }
  async setNormalization(enabled: boolean): Promise<void> {
    this.normalization.set(enabled);
    try { localStorage.setItem(NORMALIZATION_KEY, String(enabled)); } catch { /* Session preference remains usable. */ }
    await this.prepareNormalization();
  }
  /** Prepare while Settings is still open; selection changes invalidate old status updates. */
  private async prepareNormalization(): Promise<void> {
    const request = ++this.calibrationRequest;
    if (!this.normalization()) { this.calibrationStatus.set(''); return; }
    const kind = this.kind(), bank = this.selectedBank();
    const name = this.options().find(entry => entry.value === kind)?.label ?? 'Soundfont';
    this.calibrationStatus.set(`Preparing loudness normalization for ${name}…`);
    try {
      const { calibrateInstruments } = await import('./instrument-loudness');
      // An obsolete request must not start more work after a settings change.
      if (request !== this.calibrationRequest) return;
      if (kind.startsWith('sf2:') && !bank) throw new Error('The selected soundfont is no longer loaded.');
      await calibrateInstruments(bank, message => {
        if (request === this.calibrationRequest) this.calibrationStatus.set(`${name}: ${message}`);
      }, kind);
      if (request === this.calibrationRequest) this.calibrationStatus.set(`Loudness normalization ready for ${name}.`);
    } catch (error) {
      if (request === this.calibrationRequest) this.calibrationStatus.set(`Could not prepare loudness normalization for ${name}: `
        + (error instanceof Error ? error.message : 'Calibration failed.') + ' Turn normalization off and on to retry.');
    }
  }
  selectedBank(): Sf2Bank | undefined { return this.loaded().find(bank => bank.id === this.kind())?.bank; }
  select(id: string): void {
    if (id === 'synth' || id === 'chiptune' || this.loaded().some(bank => bank.id === id)) {
      this.kind.set(id as InstrumentEngineKind);
      void this.prepareNormalization();
    }
  }
  async load(file: File): Promise<void> {
    if (this.busy()) return;
    this.message.set('');
    if (file.size > MAX_SOUNDFONT_BYTES) { this.message.set('Soundfonts must be 250 MiB or smaller.'); return; }
    if (!file.name.toLowerCase().endsWith('.sf2')) { this.message.set('Choose an SF2 soundfont file.'); return; }
    this.busy.set(true);
    this.message.set('Loading ' + file.name + '…');
    try {
      await new Promise<void>(resolve => setTimeout(resolve, 0));
      const data = await file.arrayBuffer();
      if (data.byteLength > MAX_SOUNDFONT_BYTES) throw new Error('Soundfonts must be 250 MiB or smaller.');
      const { Sf2Bank } = await import('./sf2-resolver');
      const bank = new Sf2Bank(data);
      if (!bank.presets.some(preset => preset.regions.length)) throw new Error('The soundfont contains no playable presets.');
      const entry: LoadedSoundfont = { id: `sf2:${++this.nextId}`, name: file.name, size: file.size, bank };
      const previous = this.loaded();
      const evicted = previous.length === MAX_LOADED_SOUNDFONTS ? previous[0] : undefined;
      this.banks.set([...previous, entry].slice(-MAX_LOADED_SOUNDFONTS));
      this.kind.set(entry.id);
      void this.prepareNormalization();
      this.message.set('Loaded ' + file.name + '.' + (evicted ? ' Unloaded ' + evicted.name + ' (oldest).' : ''));
    } catch (error) {
      this.message.set('Could not load ' + file.name + ': ' + (error instanceof Error ? error.message : 'Invalid soundfont.'));
    } finally { this.busy.set(false); }
  }
  unload(id: InstrumentEngineKind): void {
    const selected = this.kind() === id;
    if (selected) this.kind.set('synth');
    this.banks.update(banks => banks.filter(bank => bank.id !== id));
    if (selected) void this.prepareNormalization();
  }
}
