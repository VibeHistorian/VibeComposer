import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import type { ArrangedPart, ArrangementSection, CompositionProject, CompositionTrack } from '../core/project/project.model';
import { ARRANGED_PARTS } from '../core/project/project.model';
import { shouldGenerateTrackInSection } from '../core/music/arrangement-generator';
import { layOutTrackPhrase } from '../core/music/phrase';

interface OverviewSection {
  readonly section: ArrangementSection;
  readonly startBeat: number;
  readonly startMeasure: number;
  readonly widthPercent: number;
}

interface InstrumentGroup {
  readonly role: ArrangedPart;
  readonly name: string;
  readonly tracks: readonly CompositionTrack[];
}

interface PreviewNote {
  readonly id: string;
  readonly leftPercent: number;
  readonly widthPercent: number;
  readonly topPercent: number;
  readonly opacity: number;
}

const ROLE_NAMES: Readonly<Record<ArrangedPart, string>> = {
  bass: 'Bass', chords: 'Chords', arpeggio: 'Arp', drums: 'Drums',
};

@Component({
  selector: 'vc-arrangement-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './arrangement-overview.component.html',
  styleUrl: './arrangement-overview.component.css',
})
export class ArrangementOverviewComponent {
  readonly project = input.required<CompositionProject>();
  readonly selectedSectionId = input<string | null>(null);
  readonly selectedTrackId = input('');
  readonly totalMeasures = input(0);
  readonly playheadPercent = input(0);
  readonly mixerRequested = output<void>();
  readonly sectionSelected = output<{ readonly sectionId: string; readonly trackId: string }>();
  readonly groupSelected = output<ArrangedPart>();
  readonly partToggled = output<{ readonly sectionId: string; readonly trackId: string; readonly present: boolean }>();

  readonly sections = computed<OverviewSection[]>(() => {
    const project = this.project();
    const totalMeasures = project.arrangement.reduce((sum, section) => sum + section.measures, 0);
    let startBeat = 0;
    let startMeasure = 1;
    return project.arrangement.map((section) => {
      const item = {
        section,
        startBeat,
        startMeasure,
        widthPercent: totalMeasures > 0 ? section.measures / totalMeasures * 100 : 0,
      };
      startBeat += section.measures * 4;
      startMeasure += section.measures;
      return item;
    });
  });

  readonly groups = computed<InstrumentGroup[]>(() => ARRANGED_PARTS.map((role) => ({
    role,
    name: ROLE_NAMES[role],
    tracks: this.project().tracks.filter((track) => track.role === role),
  })));

  readonly minimumWidth = computed(() => 88 + this.sections().reduce((width, section) =>
    width + Math.max(170, section.section.measures * 48), 0));

  private readonly trackNotes = computed(() => {
    const project = this.project();
    return new Map(project.tracks.map((track) => [track.id, layOutTrackPhrase(project, track)]));
  });

  private readonly cellNotes = computed(() => {
    const trackNotes = this.trackNotes();
    const previews = new Map<string, readonly PreviewNote[]>();
    for (const section of this.sections()) {
      const sectionEndBeat = section.startBeat + section.section.measures * 4;
      for (const group of this.groups()) {
        const notes = group.tracks.flatMap((track) => (trackNotes.get(track.id) ?? [])
          .filter((note) => note.startBeat >= section.startBeat && note.startBeat < sectionEndBeat)
          .map((note) => ({ ...note, id: `${track.id}:${note.id}:${note.startBeat}` })));
        if (notes.length === 0) {
          previews.set(this.cellKey(section.section.id, group.role), []);
          continue;
        }
        const minPitch = Math.min(...notes.map((note) => note.midi));
        const maxPitch = Math.max(...notes.map((note) => note.midi));
        const pitchSpan = Math.max(12, maxPitch - minPitch);
        const sectionBeats = section.section.measures * 4;
        previews.set(this.cellKey(section.section.id, group.role), notes.map((note) => ({
          id: note.id,
          leftPercent: (note.startBeat - section.startBeat) / sectionBeats * 100,
          widthPercent: Math.max(0.45, note.durationBeats / sectionBeats * 100),
          topPercent: (maxPitch - note.midi) / pitchSpan * 78 + 8,
          opacity: 0.48 + note.velocity / 127 * 0.48,
        })));
      }
    }
    return previews;
  });

  sectionLabel(type: string): string { return type.replaceAll('_', ' '); }
  roleName(role: ArrangedPart): string { return ROLE_NAMES[role]; }
  measureLabel(section: OverviewSection): string {
    return `${section.startMeasure.toString().padStart(2, '0')}–${(section.startMeasure + section.section.measures - 1).toString().padStart(2, '0')}`;
  }
  previewNotes(sectionId: string, role: ArrangedPart): readonly PreviewNote[] {
    return this.cellNotes().get(this.cellKey(sectionId, role)) ?? [];
  }
  trackPresent(section: ArrangementSection, track: CompositionTrack): boolean {
    return shouldGenerateTrackInSection(BigInt(this.project().seed), section, track);
  }
  isGroupSelected(group: InstrumentGroup): boolean {
    const selectedTrackId = this.selectedTrackId();
    return !!selectedTrackId && group.tracks.some((track) => track.id === selectedTrackId);
  }
  toggleTrack(section: ArrangementSection, track: CompositionTrack): void {
    this.partToggled.emit({
      sectionId: section.id,
      trackId: track.id,
      present: !this.trackPresent(section, track),
    });
  }
  selectSection(sectionId: string): void {
    this.sectionSelected.emit({ sectionId, trackId: this.selectedTrackId() });
  }

  private cellKey(sectionId: string, role: ArrangedPart): string { return `${sectionId}:${role}`; }
}
