import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { generateBassline } from '../../core/music/bass-generator';
import { getDiatonicChords, KEYS } from '../../core/music/harmony';
import { generateDiatonicProgression } from '../../core/music/progression-generator';
import type { DiatonicChord, ScaleMode } from '../../core/music/harmony';
import type { BassRhythm } from '../../core/project/project.model';
import { ProjectService } from '../../core/project/project.service';

@Component({
  selector: 'vc-create-workspace',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './create-workspace.component.html',
  styleUrl: './create-workspace.component.css',
})
export class CreateWorkspaceComponent {
  readonly projects = inject(ProjectService);
  readonly project = this.projects.project;
  readonly keys = KEYS;
  readonly chords = computed(() => getDiatonicChords(this.project().key, this.project().scale));
  readonly bassRhythms: ReadonlyArray<{ value: BassRhythm; label: string }> = [
    { value: 'alternating', label: 'Alternating phrase' },
    { value: 'full', label: 'Every eighth note' },
    { value: 'half', label: 'Alternating eighths' },
    { value: 'tresillo', label: 'Tresillo' },
    { value: 'sparse', label: 'Sparse quarters' },
  ];
  readonly bassNotes = computed(() => {
    const project = this.project();
    return generateBassline(
      BigInt(project.seed), project.key, project.scale, project.progression,
      project.bass.rhythm, project.bass.noteVariation,
    );
  });
  readonly bassLengthBeats = computed(() => this.project().progression.length * 4);

  chordFor(degree: number): DiatonicChord | undefined {
    return this.chords().find((chord) => chord.degree === degree);
  }

  changeChord(index: number, event: Event): void {
    const degree = Number(this.valueFrom(event));
    this.projects.setChordDegree(index, degree);
  }

  generateProgression(): void {
    this.projects.setProgression(generateDiatonicProgression(
      BigInt(this.project().seed),
      this.project().progression.length,
    ));
  }

  updateBassRhythm(event: Event): void {
    const rhythm = this.valueFrom(event);
    if (this.bassRhythms.some((option) => option.value === rhythm)) {
      this.projects.updateBassSettings({ rhythm: rhythm as BassRhythm });
    }
  }

  updateBassNoteVariation(event: Event): void {
    const variation = Number(this.valueFrom(event));
    if (Number.isInteger(variation) && variation >= 0 && variation <= 100) {
      this.projects.updateBassSettings({ noteVariation: variation });
    }
  }

  bassNoteLeft(startBeat: number): number {
    return startBeat / this.bassLengthBeats() * 100;
  }

  bassNoteWidth(durationBeats: number): number {
    return durationBeats / this.bassLengthBeats() * 100;
  }

  bassNoteName(midi: number): string {
    const noteNames = ['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'];
    return `${noteNames[midi % 12]}${Math.floor(midi / 12) - 1}`;
  }

  updateName(event: Event): void {
    this.projects.updateSettings({ name: this.valueFrom(event).trim() || 'Untitled composition' });
  }

  updateKey(event: Event): void {
    this.projects.updateSettings({ key: this.valueFrom(event) });
  }

  updateScale(event: Event): void {
    const scale = this.valueFrom(event);
    if (scale === 'major' || scale === 'natural-minor') {
      this.projects.updateSettings({ scale: scale as ScaleMode });
    }
  }

  updateTempo(event: Event): void {
    const tempo = Number(this.valueFrom(event));
    if (Number.isInteger(tempo) && tempo >= 40 && tempo <= 240) {
      this.projects.updateSettings({ tempoBpm: tempo });
    } else {
      (event.target as HTMLInputElement).value = String(this.project().tempoBpm);
    }
  }

  updateSeed(event: Event): void {
    const seed = this.valueFrom(event).trim();
    if (/^-?\d+$/.test(seed)) {
      const value = BigInt(seed);
      if (value >= -(1n << 63n) && value <= (1n << 63n) - 1n) {
        this.projects.updateSettings({ seed: value.toString() });
        return;
      }
    }
    (event.target as HTMLInputElement).value = this.project().seed;
  }

  private valueFrom(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
