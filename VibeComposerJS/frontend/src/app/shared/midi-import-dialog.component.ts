import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { fitImportedMidi, midiDurationError, type ImportedMidiTrack, type MidiScaleChoice } from '../core/music/midi-import-options';

export interface MidiImportDialogData {
  readonly track: ImportedMidiTrack;
  readonly phraseBeats: number;
  readonly choices: readonly MidiScaleChoice[];
}

@Component({
  selector: 'vc-midi-import-dialog',
  templateUrl: './midi-import-dialog.component.html',
  styleUrl: './midi-import-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MidiImportDialogComponent {
  readonly data = inject<MidiImportDialogData>(DIALOG_DATA);
  readonly dialog = inject(DialogRef);
  readonly duration = signal('');
  readonly choice = signal('');
  readonly tooLong = this.data.track.durationBeats > this.data.phraseBeats;
  readonly error = computed(() => midiDurationError(this.duration(), this.data.phraseBeats));

  apply(): void {
    if (this.error()) return;
    const pitches = this.data.choices.find(choice => choice.id === this.choice())?.pitches;
    this.dialog.close(fitImportedMidi(this.data.track, this.data.phraseBeats, this.duration(), pitches));
  }

  keydown(event: KeyboardEvent): void {
    // Keep phrase editor shortcuts from changing the underlying draft while choosing an import.
    if (event.key !== 'Escape') event.stopPropagation();
  }
}
