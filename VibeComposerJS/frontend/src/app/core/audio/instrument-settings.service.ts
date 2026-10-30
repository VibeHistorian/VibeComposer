import { computed, Injectable, signal } from '@angular/core';
import type { InstrumentEngineKind } from './instrument-engine';
import type { Sf2Bank } from './sf2-resolver';

export const MAX_SOUNDFONT_BYTES = 250 * 1024 * 1024;
export const MAX_LOADED_SOUNDFONTS = 3;
export interface LoadedSoundfont {
  readonly id: InstrumentEngineKind;
  readonly name: string;
  readonly size: number;
  readonly bank: Sf2Bank;
}
/** Session audio resources, independent of project history and MIDI program identity. */
@Injectable({ providedIn: 'root' })
export class InstrumentSettingsService {
  readonly kind = signal<InstrumentEngineKind>('synth');
  private readonly banks = signal<readonly LoadedSoundfont[]>([]);
  readonly loaded = this.banks.asReadonly();
  readonly busy = signal(false);
  readonly message = signal('');
  readonly options = computed(() => [{ value: 'synth', label: 'TinySynth' },
    ...this.loaded().map(bank => ({ value: bank.id, label: bank.name }))]);
  private nextId = 0;

  selectedBank(): Sf2Bank | undefined { return this.loaded().find(bank => bank.id === this.kind())?.bank; }
  select(id: string): void {
    if (id === 'synth' || this.loaded().some(bank => bank.id === id)) this.kind.set(id as InstrumentEngineKind);
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
      this.message.set('Loaded ' + file.name + '.' + (evicted ? ' Unloaded ' + evicted.name + ' (oldest).' : ''));
    } catch (error) {
      this.message.set('Could not load ' + file.name + ': ' + (error instanceof Error ? error.message : 'Invalid soundfont.'));
    } finally { this.busy.set(false); }
  }
  unload(id: InstrumentEngineKind): void {
    if (this.kind() === id) this.kind.set('synth');
    this.banks.update(banks => banks.filter(bank => bank.id !== id));
  }
}
