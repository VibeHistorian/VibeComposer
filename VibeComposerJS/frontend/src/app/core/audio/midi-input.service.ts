import { computed, Injectable, signal } from '@angular/core';
export interface LiveMidiNote { id: string; midi: number; velocity: number; down: boolean; }
@Injectable({ providedIn: 'root' })
export class MidiInputService {
  private access?: MIDIAccess;
  readonly inputs = signal<readonly MIDIInput[]>([]);
  readonly selected = signal('');
  readonly error = signal('');
  readonly busy = signal(false);
  readonly options = computed(() => [{ value: '', label: 'No MIDI input' }, ...this.inputs().map(port => ({ value: port.id, label: port.name || 'MIDI input' }))]);
  listener?: (note: LiveMidiNote) => void;
  private readonly listeners: ((note: LiveMidiNote) => void)[] = [];
  /** The newest mounted/recording editor owns input; closing it restores the previous editor. */
  listen(listener: (note: LiveMidiNote) => void): () => void {
    this.dispatch({ id: '*', midi: 0, velocity: 0, down: false });
    this.listeners.push(listener);
    return () => {
      const index = this.listeners.indexOf(listener);
      if (index < 0) return;
      if (index === this.listeners.length - 1) listener({ id: '*', midi: 0, velocity: 0, down: false });
      this.listeners.splice(index, 1);
    };
  }
  ownsInput(listener: (note: LiveMidiNote) => void): boolean { return (this.listeners.at(-1) ?? this.listener) === listener; }
  private dispatch(note: LiveMidiNote): void { (this.listeners.at(-1) ?? this.listener)?.(note); }
  async refresh(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true); this.error.set('');
    try {
      if (typeof navigator.requestMIDIAccess !== 'function') throw new Error('Web MIDI is unavailable. Use keyboard recording or HTTPS / localhost in a supported browser.');
      if (!this.access) { this.access = await navigator.requestMIDIAccess({ sysex: false }); this.access.addEventListener('statechange', this.refreshPorts); }
      this.refreshPorts();
    } catch (error) { this.error.set(error instanceof Error ? error.message : 'Cannot access MIDI input.'); }
    finally { this.busy.set(false); }
  }
  private readonly refreshPorts = (): void => {
    this.inputs.set([...this.access!.inputs.values()].filter(port => port.state === 'connected'));
    if (!this.inputs().some(port => port.id === this.selected())) this.select('');
    else this.select(this.selected());
  };
  select(id: string): void {
    this.dispatch({ id: '*', midi: 0, velocity: 0, down: false });
    for (const port of this.access?.inputs.values() ?? []) port.onmidimessage = null;
    this.selected.set(id);
    const port = this.inputs().find(port => port.id === id);
    if (port) port.onmidimessage = event => {
      const [status, midi, velocity] = event.data ?? [];
      const command = status & 0xf0;
      if (command === 0x90 || command === 0x80) this.dispatch({ id: 'midi:' + (status & 15) + ':' + midi, midi,
        velocity, down: command === 0x90 && velocity > 0 });
    };
  }
}
