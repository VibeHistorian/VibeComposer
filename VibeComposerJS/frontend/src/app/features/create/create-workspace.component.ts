import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { generateArpeggio } from '../../core/music/arpeggio-generator';
import { generateBassline } from '../../core/music/bass-generator';
import { generateChordPart } from '../../core/music/chord-generator';
import { generateDrumPart } from '../../core/music/drum-generator';
import { generateCompositionMidi } from '../../core/music/midi-export';
import { getDiatonicChords, KEYS } from '../../core/music/harmony';
import { generateDiatonicProgression } from '../../core/music/progression-generator';
import type { DiatonicChord, ScaleMode } from '../../core/music/harmony';
import type {
  ArpeggioPattern, ArpeggioRate, BassRhythm, ChordRhythm, ChordVoicing, DrumGroove,
} from '../../core/project/project.model';
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
  readonly chordRhythms: ReadonlyArray<{ value: ChordRhythm; label: string }> = [
    { value: 'full', label: 'Every eighth note' },
    { value: 'half', label: 'Alternating eighths' },
    { value: 'tresillo', label: 'Tresillo' },
    { value: 'sparse', label: 'Quarter notes' },
    { value: 'single', label: 'Downbeat only' },
  ];
  readonly chordVoicings: ReadonlyArray<{ value: ChordVoicing; label: string }> = [
    { value: 'close', label: 'Close' },
    { value: 'open', label: 'Open' },
  ];
  readonly arpPatterns: ReadonlyArray<{ value: ArpeggioPattern; label: string }> = [
    { value: 'up', label: 'Up' },
    { value: 'down', label: 'Down' },
    { value: 'up-down', label: 'Up and down' },
    { value: 'random', label: 'Seeded random' },
  ];
  readonly arpRates: ReadonlyArray<{ value: ArpeggioRate; label: string }> = [
    { value: 'eighth', label: 'Eighth notes' },
    { value: 'sixteenth', label: 'Sixteenth notes' },
  ];
  readonly drumGrooves: ReadonlyArray<{ value: DrumGroove; label: string }> = [
    { value: 'rock', label: 'Rock' },
    { value: 'four-on-floor', label: 'Four on the floor' },
    { value: 'half-time', label: 'Half time' },
    { value: 'sparse', label: 'Sparse' },
  ];
  readonly bassNotes = computed(() => {
    const project = this.project();
    return generateBassline(
      BigInt(project.seed), project.key, project.scale, project.progression,
      project.bass.rhythm, project.bass.noteVariation,
    );
  });
  readonly bassLengthBeats = computed(() => this.project().progression.length * 4);
  readonly chordHits = computed(() => {
    const project = this.project();
    return generateChordPart(
      BigInt(project.seed), project.key, project.scale, project.progression, project.chords,
    );
  });
  readonly arpeggioNotes = computed(() => {
    const project = this.project();
    return generateArpeggio(
      BigInt(project.seed), project.key, project.scale, project.progression, project.arpeggio,
    );
  });
  readonly drumHits = computed(() => {
    const project = this.project();
    return generateDrumPart(BigInt(project.seed), project.progression.length, project.drums);
  });

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

  updateChordRhythm(event: Event): void {
    const rhythm = this.valueFrom(event);
    if (this.chordRhythms.some((option) => option.value === rhythm)) {
      this.projects.updateChordSettings({ rhythm: rhythm as ChordRhythm });
    }
  }

  updateChordVoicing(event: Event): void {
    const voicing = this.valueFrom(event);
    if (this.chordVoicings.some((option) => option.value === voicing)) {
      this.projects.updateChordSettings({ voicing: voicing as ChordVoicing });
    }
  }

  updateChordNoteLength(event: Event): void {
    const noteLengthPercent = Number(this.valueFrom(event));
    if (Number.isInteger(noteLengthPercent) && noteLengthPercent >= 25 && noteLengthPercent <= 125) {
      this.projects.updateChordSettings({ noteLengthPercent });
    }
  }

  chordHitLeft(startBeat: number): number {
    return startBeat / this.bassLengthBeats() * 100;
  }

  chordHitWidth(durationBeats: number): number {
    return durationBeats / this.bassLengthBeats() * 100;
  }

  chordHitTitle(hit: readonly number[], symbol: string): string {
    return `${symbol}: ${hit.map((midi) => this.bassNoteName(midi)).join(' · ')}`;
  }

  updateArpeggioPattern(event: Event): void {
    const pattern = this.valueFrom(event);
    if (this.arpPatterns.some((option) => option.value === pattern)) {
      this.projects.updateArpeggioSettings({ pattern: pattern as ArpeggioPattern });
    }
  }

  updateArpeggioRate(event: Event): void {
    const rate = this.valueFrom(event);
    if (this.arpRates.some((option) => option.value === rate)) {
      this.projects.updateArpeggioSettings({ rate: rate as ArpeggioRate });
    }
  }

  updateArpeggioOctaves(event: Event): void {
    const octaves = Number(this.valueFrom(event));
    if (octaves === 1 || octaves === 2) {
      this.projects.updateArpeggioSettings({ octaves });
    }
  }

  arpeggioNoteLeft(startBeat: number): number {
    return startBeat / this.bassLengthBeats() * 100;
  }

  arpeggioNoteWidth(durationBeats: number): number {
    return durationBeats / this.bassLengthBeats() * 100;
  }

  updateDrumGroove(event: Event): void {
    const groove = this.valueFrom(event);
    if (this.drumGrooves.some((option) => option.value === groove)) {
      this.projects.updateDrumSettings({ groove: groove as DrumGroove });
    }
  }

  updateDrumSwing(event: Event): void {
    const swingPercent = Number(this.valueFrom(event));
    if (Number.isInteger(swingPercent) && swingPercent >= 50 && swingPercent <= 75) {
      this.projects.updateDrumSettings({ swingPercent });
    }
  }

  drumHitLeft(startBeat: number): number {
    return startBeat / this.bassLengthBeats() * 100;
  }

  drumHitTop(voice: string): number {
    return voice === 'kick' ? 5 : voice === 'snare' ? 19 : 33;
  }

  exportMidi(): void {
    const project = this.project();
    const bytes = generateCompositionMidi(project);
    const fileData = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
    const file = new Blob([fileData], { type: 'audio/midi' });
    const objectUrl = URL.createObjectURL(file);
    const link = document.createElement('a');
    const fileName = project.name.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '-').trim().slice(0, 80) || 'VibeComposer';
    link.href = objectUrl;
    link.download = `${fileName}.mid`;
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
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
