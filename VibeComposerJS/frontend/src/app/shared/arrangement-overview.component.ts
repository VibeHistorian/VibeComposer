import { UiPreferencesService } from '../core/ui/ui-preferences.service';
import { sectionDurationBeats, sectionChordDurations } from '../core/music/section-context';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import type { ArrangedPart, ArrangementSection, CompositionProject, CompositionTrack, SectionType } from '../core/project/project.model';
import { ARRANGED_PARTS, SECTION_TYPES } from '../core/project/project.model';
import { shouldGenerateTrackInSection } from '../core/music/arrangement-generator';
import { layOutTrackPhrase } from '../core/music/phrase';
import type { SettingsTarget } from './workspace-ui.service';
import type { PlaybackRange } from '../core/audio/audio-playback.service';

interface OverviewSection {
  readonly section: ArrangementSection;
  readonly startBeat: number;
  readonly startMeasure: number;
}

interface InstrumentGroup {
  readonly role: ArrangedPart;
  readonly name: string;
  readonly tracks: readonly CompositionTrack[];
}

interface PreviewNote {
  readonly leftPercent: number;
  readonly widthPercent: number;
  readonly topPercent: number;
  readonly opacity: number;
}

interface OverviewHitArea {
  readonly key: string;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
  readonly label: string;
  readonly activate: (event?: MouseEvent | KeyboardEvent) => void;
  readonly togglePresence?: () => void;
  readonly reroll?: () => void;
  readonly reset?: () => void;
  readonly duplicate?: () => void;
  readonly remove?: () => void;
}

const RULER_HEIGHT = 24;
const MIN_ROW_HEIGHT = 52;
const TRACK_BUTTON_PITCH = 19;
const CELL_HORIZONTAL_PADDING = 10;
const MAX_TRACK_BUTTON_ROWS = 2;
const ROLE_COLORS: Readonly<Record<ArrangedPart, string>> = {
  melody: 'melody', bass: 'bass', chords: 'chords', arpeggio: 'arpeggio', drums: 'drum',
};

const ROLE_NAMES: Readonly<Record<ArrangedPart, string>> = {
  melody: 'Melody', bass: 'Bass', chords: 'Chords', arpeggio: 'Arp', drums: 'Drums',
};

@Component({
  selector: 'vc-arrangement-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './arrangement-overview.component.html',
  styleUrl: './arrangement-overview.component.css',
  host: { '(document:click)': 'closePickerOutside($event)', '(document:keydown.escape)': 'closeSectionPicker()' },
})
export class ArrangementOverviewComponent implements AfterViewInit, OnDestroy {
  readonly project = input.required<CompositionProject>();
  readonly selectedSectionIds = input<readonly string[]>([]);
  readonly selectedTrackId = input('');
  readonly settingsTarget = input<SettingsTarget | null>(null);
  readonly totalMeasures = input(0);
  readonly playheadPercent = input(0);
  readonly loopRange = input<PlaybackRange | null>(null);
  readonly mixerRequested = output<void>();
  readonly sectionAddRequested = output<SectionType>();
  readonly sectionTypes = SECTION_TYPES;
  readonly sectionPicker = signal<{ x: number; y: number } | null>(null);
  readonly canAddSection = computed(() => this.project().arrangement.length < 32
    && this.project().arrangement.reduce((sum, section) => sum + section.measures, 0) + 4 <= 128);

  toggleSectionPicker(event: MouseEvent): void {
    event.stopPropagation();
    if (this.sectionPicker()) { this.closeSectionPicker(); return; }
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.sectionPicker.set({ x: Math.max(8, Math.min(innerWidth - 278, bounds.right - 270)),
      y: Math.max(8, Math.min(innerHeight - 220, bounds.bottom + 6)) });
    setTimeout(() => document.querySelector<HTMLElement>('.section-picker button')?.focus(), 0);
  }
  closeSectionPicker(): void { this.sectionPicker.set(null); }
  closePickerOutside(event: Event): void {
    if (!(event.target as HTMLElement).closest('.section-picker, .add-section-button')) this.closeSectionPicker();
  }
  chooseSection(type: SectionType): void {
    if (!this.canAddSection()) return;
    this.closeSectionPicker(); this.sectionAddRequested.emit(type);
    document.querySelector<HTMLElement>('.add-section-button')?.focus();
  }
  sectionPickerKey(event: KeyboardEvent): void {
    const buttons = [...(event.currentTarget as HTMLElement).querySelectorAll<HTMLButtonElement>('button')];
    const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); this.closeSectionPicker(); document.querySelector<HTMLElement>('.add-section-button')?.focus(); return; }
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowDown' ? 3 : event.key === 'ArrowUp' ? -3 : 0;
    if (!step && !['Home','End'].includes(event.key)) return;
    event.preventDefault();
    buttons[event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1 : (index + step + buttons.length) % buttons.length]?.focus();
  }
  readonly randomizeRequested = output<void>();
  readonly sectionSelected = output<{ readonly sectionId: string; readonly trackId: string;
    readonly ctrlKey?: boolean; readonly metaKey?: boolean; readonly shiftKey?: boolean }>();
  readonly selectionCleared = output<void>();
  readonly groupSelected = output<ArrangedPart>();
  readonly cellSelected = output<{ readonly sectionId: string; readonly role: ArrangedPart }>();
  readonly trackSelected = output<{ readonly sectionId: string; readonly trackId: string }>();
  readonly partToggled = output<{ readonly sectionId: string; readonly trackId: string; readonly present: boolean }>();
  readonly tracksChangeRequested = output<{ readonly sectionId: string; readonly role?: ArrangedPart;
  readonly trackId?: string; readonly action: 'reroll' | 'reset' }>();
  readonly sectionDuplicateRequested = output<string>();
  readonly sectionRemoveRequested = output<string>();

  readonly sections = computed<OverviewSection[]>(() => {
    const project = this.project();
    let startBeat = 0;
    let startMeasure = 1;
    return project.arrangement.map((section) => {
      const item = {
        section,
        startBeat,
        startMeasure,
      };
      startBeat += sectionDurationBeats(project, section);
      startMeasure += section.measures;
      return item;
    });
  });

  readonly groups = computed<InstrumentGroup[]>(() => ARRANGED_PARTS.map((role) => ({
    role,
    name: ROLE_NAMES[role],
    tracks: this.project().tracks.filter((track) => track.role === role),
  })));

  private readonly trackNotes = computed(() => {
    const project = this.project();
    return new Map(project.tracks.map((track) => [track.id, layOutTrackPhrase(project, track)]));
  });

  private readonly cellNotes = computed(() => {
    const trackNotes = this.trackNotes();
    const previews = new Map<string, readonly PreviewNote[]>();
    for (const section of this.sections()) {
      const sectionEndBeat = section.startBeat + sectionDurationBeats(this.project(), section.section);
      for (const group of this.groups()) {
        const notes = this.overviewTracks(section.section, group).flatMap((track) => (trackNotes.get(track.id) ?? [])
          .filter((note) => note.startBeat >= section.startBeat && note.startBeat < sectionEndBeat));
        if (notes.length === 0) {
          previews.set(this.cellKey(section.section.id, group.role), []);
          continue;
        }
        const minPitch = Math.min(...notes.map((note) => note.midi));
        const maxPitch = Math.max(...notes.map((note) => note.midi));
        const pitchSpan = Math.max(12, maxPitch - minPitch);
        const sectionBeats = sectionDurationBeats(this.project(), section.section);
        previews.set(this.cellKey(section.section.id, group.role), notes.map((note) => ({
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
  measureLabel(section: OverviewSection): string {
    return `${section.startMeasure.toString().padStart(2, '0')}–${(section.startMeasure + section.section.measures - 1).toString().padStart(2, '0')}`;
  }
  previewNotes(sectionId: string, role: ArrangedPart): readonly PreviewNote[] {
    return this.cellNotes().get(this.cellKey(sectionId, role)) ?? [];
  }
  trackPresent(section: ArrangementSection, track: CompositionTrack): boolean {
    return shouldGenerateTrackInSection(section, track);
  }
  private overviewTracks(section: ArrangementSection, group: InstrumentGroup): readonly CompositionTrack[] {
    if (sectionDurationBeats(this.project(), section) >= 4) return group.tracks;
    const first = group.tracks.find(track => this.trackPresent(section, track));
    return first ? [first] : [];
  }
  isGroupSelected(group: InstrumentGroup): boolean {
    const target = this.settingsTarget();
    return target?.kind === 'global-role' && target.role === group.role;
  }
  toggleTrack(section: ArrangementSection, track: CompositionTrack): void {
    this.partToggled.emit({
      sectionId: section.id,
      trackId: track.id,
      present: !this.trackPresent(section, track),
    });
  }
  selectSection(sectionId: string, event?: MouseEvent | KeyboardEvent): void {
    this.sectionSelected.emit({ sectionId, trackId: this.selectedTrackId(),
      ...(event ? { ctrlKey: event.ctrlKey, metaKey: event.metaKey, shiftKey: event.shiftKey } : {}) });
  }

  private cellKey(sectionId: string, role: ArrangedPart): string { return `${sectionId}:${role}`; }

  private readonly scrollHost = viewChild.required<ElementRef<HTMLDivElement>>('scrollHost');
  private readonly scrollContent = viewChild.required<ElementRef<HTMLDivElement>>('scrollContent');
  private readonly viewport = viewChild.required<ElementRef<HTMLDivElement>>('viewport');
  private readonly scoreCanvas = viewChild.required<ElementRef<HTMLCanvasElement>>('scoreCanvas');
  private readonly playheadCanvas = viewChild.required<ElementRef<HTMLCanvasElement>>('playheadCanvas');
  private resizeObserver?: ResizeObserver;
  private viewportSize: { readonly width: number; readonly height: number } | null = null;
  private frame = 0;
  private ready = false;
  private width = 0;
  private height = 0;
  private readonly labelWidth = 24;
  private rowHeight = MIN_ROW_HEIGHT;
  private pixelsPerBeat = 12;
  private horizontalScale = 1;
  private effectiveHorizontalScale = 1;
  private pendingZoomAnchor: { beat: number; cursorFraction: number } | null = null;
  private offsetX = 0;
  private offsetY = 0;
  private hits: OverviewHitArea[] = [];
  private focusedKey: string | null = null;
  private accent = '#32d7f4';

  private readonly preferences = inject(UiPreferencesService);
  private readonly modelEffect = effect(() => {
    this.preferences.appearance();
    this.sections();
    this.groups();
    this.cellNotes();
    this.selectedSectionIds();
    this.selectedTrackId();
    this.settingsTarget();
    this.loopRange();
    this.scheduleDraw();
  });

  private readonly playheadEffect = effect(() => {
    const percent = this.playheadPercent();
    this.drawPlayhead(percent);
  });

  ngAfterViewInit(): void {
    this.ready = true;
    this.scoreCanvas().nativeElement.addEventListener('wheel', this.onCanvasWheel, { passive: false });
    const host = this.scrollHost().nativeElement;
    this.resizeObserver = new ResizeObserver((entries) => {
      const bounds = entries.find((entry) => entry.target === host)?.contentRect;
      if (!bounds) return;
      // Unlike clientWidth/clientHeight, contentRect preserves fractional CSS
      // pixels and excludes scrollbars. Rounding up can create both scrollbars,
      // then removing them on the next draw creates an endless resize loop.
      this.viewportSize = { width: bounds.width, height: bounds.height };
      this.scheduleDraw();
    });
    this.resizeObserver.observe(host);
  }

  ngOnDestroy(): void {
    this.ready = false;
    this.scoreCanvas().nativeElement.removeEventListener('wheel', this.onCanvasWheel);
    this.resizeObserver?.disconnect();
    cancelAnimationFrame(this.frame);
  }

  onScroll(): void { this.scheduleDraw(); }

  private readonly onCanvasWheel = (event: WheelEvent): void => {
    if (!event.ctrlKey) return;
    event.preventDefault();
    event.stopPropagation();
    if (!this.ready || this.width <= this.labelWidth) return;
    const bounds = this.scoreCanvas().nativeElement.getBoundingClientRect();
    const timelineWidth = this.width - this.labelWidth;
    const cursorX = Math.min(timelineWidth, Math.max(0,
      (event.clientX - bounds.left) * this.width / Math.max(1, bounds.width) - this.labelWidth));
    this.pendingZoomAnchor = {
      beat: (this.scrollHost().nativeElement.scrollLeft + cursorX) / this.pixelsPerBeat,
      cursorFraction: cursorX / timelineWidth,
    };
    const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? timelineWidth : 1;
    const delta = (event.deltaY || event.deltaX) * unit;
    this.horizontalScale = Math.min(Math.max(128, this.effectiveHorizontalScale),
      Math.max(1, this.effectiveHorizontalScale * Math.exp(-delta * 0.002)));
    this.scheduleDraw();
  };

  private scheduleDraw(): void {
    if (!this.ready || this.frame) return;
    this.frame = requestAnimationFrame(() => {
      this.frame = 0;
      if (this.ready) this.drawOverview();
    });
  }

  private beatX(beat: number): number { return this.labelWidth + beat * this.pixelsPerBeat - this.offsetX; }

  private drawOverview(): void {
    const host = this.scrollHost().nativeElement;
    const size = this.viewportSize;
    if (!size) return;
    const sections = this.sections();
    const groups = this.groups();
    const totalBeats = sections.reduce((sum, item) => sum + sectionDurationBeats(this.project(), item.section), 0);
    const minimumHeight = RULER_HEIGHT + groups.length * MIN_ROW_HEIGHT;
    const trackCount = Math.max(0, ...groups.map((group) => group.tracks.length));
    const minimumCellWidth = trackCount > 0
      ? CELL_HORIZONTAL_PADDING + TRACK_BUTTON_PITCH * Math.ceil(trackCount / MAX_TRACK_BUTTON_ROWS) : 0;
    const fullSectionBeats = sections.map(item => sectionDurationBeats(this.project(), item.section)).filter(beats => beats >= 4);
    const minimumPixelsPerBeat = fullSectionBeats.length ? minimumCellWidth / Math.min(...fullSectionBeats) : 0;
    const needsHorizontalScroll = (): boolean => totalBeats > 0 && (this.horizontalScale > 1
      || minimumPixelsPerBeat * totalBeats > Math.max(1,
        Math.floor(Math.min(host.clientWidth, size.width)) - this.labelWidth) + 1e-7);
    // Page zoom can round native scrollbar sizes differently from CSS content.
    // Decide each axis explicitly, rather than allowing our own size writes to
    // toggle automatic scrollbars and invalidate the next observer measurement.
    host.style.overflowX = needsHorizontalScroll() ? 'scroll' : 'hidden';
    const fitGridHeight = (): void => {
      const scrollbarHeight = Math.max(0, (host.offsetHeight ?? host.clientHeight) - host.clientHeight);
      host.style.height = `${minimumHeight + scrollbarHeight}px`;
    };
    // The arrangement owns its intrinsic height; only the score grows with the
    // workspace. Measure native scrollbars so zoom never clips the last row.
    fitGridHeight();
    // Resolve horizontal scrolling first: its scrollbar reduces available height.
    host.style.overflowY = minimumHeight > host.clientHeight ? 'scroll' : 'hidden';
    // A newly needed vertical scrollbar can reduce the timeline below its
    // button-fit minimum. Resolve that extra dependency in the same draw.
    if (host.style.overflowX === 'hidden' && needsHorizontalScroll()) {
      host.style.overflowX = 'scroll';
      fitGridHeight();
      host.style.overflowY = minimumHeight > host.clientHeight ? 'scroll' : 'hidden';
    }
    // A model/scroll redraw can precede the next observer callback when the host
    // shrinks. Cap against the current client size until that measurement arrives.
    this.width = Math.floor(Math.min(host.clientWidth, size.width));
    this.height = Math.floor(Math.min(host.clientHeight, size.height));
    if (this.width <= 0 || this.height <= 0) return;
    // Full sections retain the two-row button-fit floor. Sub-four-beat sections
    // show a single included track per role and never raise the arrangement zoom.
    const timelineWidth = Math.max(1, this.width - this.labelWidth);
    this.effectiveHorizontalScale = Math.max(this.horizontalScale,
      minimumPixelsPerBeat * totalBeats / timelineWidth);
    this.pixelsPerBeat = timelineWidth / Math.max(1, totalBeats) * this.effectiveHorizontalScale;
    this.rowHeight = MIN_ROW_HEIGHT;
    const content = this.scrollContent().nativeElement;
    // Use viewport dimensions directly when fitting. Reconstructing them from
    // fractional beat/row sizes can overflow by a rounding error and add scrollbars.
    const contentWidth = totalBeats > 0 && this.effectiveHorizontalScale > 1
      ? Math.max(this.width, Math.ceil(this.labelWidth + timelineWidth * this.effectiveHorizontalScale))
      : this.width;
    content.style.width = `${contentWidth}px`;
    content.style.height = `${Math.max(this.height, minimumHeight)}px`;
    const viewport = this.viewport().nativeElement;
    viewport.style.width = `${this.width}px`;
    viewport.style.height = `${this.height}px`;
    const anchor = this.pendingZoomAnchor;
    this.pendingZoomAnchor = null;
    const offset = anchor ? anchor.beat * this.pixelsPerBeat - anchor.cursorFraction * timelineWidth : host.scrollLeft;
    host.scrollLeft = Math.min(Math.max(0, totalBeats * this.pixelsPerBeat - timelineWidth), Math.max(0, offset));
    this.offsetX = host.scrollLeft;
    this.offsetY = host.scrollTop;
    const dpr = Math.min(globalThis.devicePixelRatio || 1, 2);
    for (const canvas of [this.scoreCanvas().nativeElement, this.playheadCanvas().nativeElement]) {
      const width = Math.round(this.width * dpr);
      const height = Math.round(this.height * dpr);
      if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
      }
    }
    const canvas = this.scoreCanvas().nativeElement;
    const context = canvas.getContext('2d');
    if (!context) return;
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    context.clearRect(0, 0, this.width, this.height);
    const style = getComputedStyle(document.documentElement);
    const monoFont = style.getPropertyValue('--font-mono').trim();
    const headingFont = style.getPropertyValue('--font-heading').trim();
    const colors = new Map<string, string>();
    const color = (name: string): string => {
      let value = colors.get(name);
      if (!value) { value = style.getPropertyValue(name).trim() || '#000000'; colors.set(name, value); }
      return value;
    };
    this.accent = color('--accent-primary');
    const fill = (x: number, y: number, width: number, height: number, name: string): void => {
      context.fillStyle = color(name);
      context.fillRect(x, y, width, height);
    };
    const text = (value: string, x: number, y: number, name: string, font = `10px ${monoFont}`): void => {
      context.font = font;
      context.fillStyle = color(name);
      context.fillText(value, x, y);
    };
    const hit = (area: OverviewHitArea, left: number, top: number): void => {
      const x = Math.max(left, area.x);
      const y = Math.max(top, area.y);
      const right = Math.min(this.width, area.x + area.width);
      const bottom = Math.min(this.height, area.y + area.height);
      if (right > x && bottom > y) this.hits.push({ ...area, x, y, width: right - x, height: bottom - y });
    };
    this.hits = [];
    fill(0, 0, this.width, this.height, '--surface-base');

    // Clip the timeline behind the fixed group labels and ruler.
    context.save();
    context.beginPath(); context.rect(this.labelWidth, RULER_HEIGHT, Math.max(0, this.width - this.labelWidth), Math.max(0, this.height - RULER_HEIGHT)); context.clip();
    for (let row = 0; row < groups.length; row++) {
      const group = groups[row];
      const y = RULER_HEIGHT + row * this.rowHeight - this.offsetY;
      if (y + this.rowHeight <= RULER_HEIGHT || y >= this.height) continue;
      const role = ROLE_COLORS[group.role];
      const noteColor = color(group.role === 'drums' ? '--role-drums' : `--role-${role}-strong`);
      for (const item of sections) {
        const x = this.beatX(item.startBeat);
        const width = sectionDurationBeats(this.project(), item.section) * this.pixelsPerBeat;
        if (x + width <= this.labelWidth || x >= this.width) continue;
        fill(x, y, width, this.rowHeight, `--role-${role}-cell-surface`);
        fill(x, y, 1, this.rowHeight, '--surface-base');
        fill(x, y + this.rowHeight - 1, width, 1, '--border-subtle');
        const target = this.settingsTarget();
        const customized = Object.keys(item.section.rolePartOverrides?.[group.role] ?? {}).length > 0
          || group.tracks.some((track) => Object.keys(item.section.trackPartOverrides?.[track.id] ?? {}).length > 0);
        const cellSelected = target?.kind === 'section-role' && target.sectionId === item.section.id && target.role === group.role;
        if (cellSelected) {
          context.strokeStyle = this.accent;
          context.strokeRect(x + 0.5, y + 0.5, width - 1, this.rowHeight - 1);
        }
        hit({ key: `cell:${item.section.id}:${group.role}`, x, y, width, height: this.rowHeight,
          label: `Select ${group.name} settings in ${this.sectionLabel(item.section.type)}, bars ${this.measureLabel(item)}. ${customized ? 'Customized' : 'Inherited'}.`,
          activate: () => this.cellSelected.emit({ sectionId: item.section.id, role: group.role }),
          reroll: () => this.tracksChangeRequested.emit({ sectionId: item.section.id, role: group.role, action: 'reroll' }),
          reset: () => this.tracksChangeRequested.emit({ sectionId: item.section.id, role: group.role, action: 'reset' }) }, this.labelWidth, RULER_HEIGHT);
        const visibleTracks = this.overviewTracks(item.section, group);
        const togglesPerRow = Math.max(1, Math.floor((width - CELL_HORIZONTAL_PADDING + 1e-7) / TRACK_BUTTON_PITCH));
        const toggleRows = Math.ceil(visibleTracks.length / togglesPerRow);
        const previewHeight = Math.max(4, this.rowHeight - 10 - Math.max(1, toggleRows) * TRACK_BUTTON_PITCH);
        context.save();
        context.beginPath(); context.rect(x, y + 5, width, previewHeight); context.clip();
        const durations = sectionChordDurations(this.project(), item.section);
        let slotBeat = 0;
        for (let bar = 1; bar < item.section.measures; bar++) {
          slotBeat += durations[(bar - 1) % durations.length];
          fill(x + slotBeat * this.pixelsPerBeat, y + 5, 1, previewHeight, '--border-subtle');
        }
        context.fillStyle = noteColor;
        for (const note of this.previewNotes(item.section.id, group.role)) {
          const noteX = x + note.leftPercent / 100 * width;
          const noteWidth = Math.max(1, note.widthPercent / 100 * width);
          if (noteX + noteWidth <= this.labelWidth || noteX >= this.width) continue;
          context.globalAlpha = note.opacity;
          context.fillRect(noteX, y + 5 + note.topPercent / 100 * previewHeight, noteWidth, 2);
        }
        context.globalAlpha = 1;
        context.restore();
        context.save(); context.beginPath(); context.rect(x, y, width, this.rowHeight); context.clip();
        for (let index = 0; index < visibleTracks.length; index++) {
          const track = visibleTracks[index];
          const trackNumber = group.tracks.indexOf(track) + 1;
          const toggleRow = Math.floor(index / togglesPerRow);
          const rowCount = Math.min(togglesPerRow, visibleTracks.length - toggleRow * togglesPerRow);
          const centerX = x + (width - rowCount * TRACK_BUTTON_PITCH + 3) / 2 + index % togglesPerRow * TRACK_BUTTON_PITCH + 8;
          const centerY = y + this.rowHeight - (toggleRows - toggleRow - 1) * TRACK_BUTTON_PITCH - 12;
          const present = this.trackPresent(item.section, track);
          context.globalAlpha = present ? 0.6 : 0.48;
          context.beginPath(); context.arc(centerX, centerY, 8, 0, Math.PI * 2);
          context.fillStyle = color(present ? `--role-${role}-surface` : '--surface-button'); context.fill();
          context.strokeStyle = present ? noteColor : color('--border-default'); context.stroke();
          context.textAlign = 'center';
          text(String(trackNumber), centerX, centerY + 3, present ? (group.role === 'drums' ? '--role-drums' : `--role-${role}-text`) : '--text-muted', `9px ${monoFont}`);
          context.textAlign = 'start'; context.globalAlpha = 1;
          if (target?.kind === 'section-track' && target.sectionId === item.section.id && target.trackId === track.id) {
            context.strokeStyle = this.accent;
            context.strokeRect(centerX - 10, centerY - 10, 20, 20);
          }
          const trackCustomized = Object.keys(item.section.trackPartOverrides?.[track.id] ?? {}).length > 0
            || Object.keys(item.section.rolePartOverrides?.[group.role] ?? {}).length > 0;
          const manualNotes = item.section.trackPhrases?.[track.id] !== undefined
            || track.editedPhrase !== undefined && !item.section.generatedTrackIds?.includes(track.id);
          if (manualNotes) {
            context.fillStyle = item.section.trackPhrases?.[track.id] !== undefined ? this.accent : noteColor;
            context.beginPath(); context.arc(centerX + 6, centerY - 6, 2.5, 0, Math.PI * 2); context.fill();
          } else if (trackCustomized) {
            context.textAlign = 'center';
            text('*', centerX + 6, centerY - 2, '--text-primary', `bold 10px ${monoFont}`);
            context.textAlign = 'start';
          }
          const buttonLeft = Math.max(x, centerX - 8), buttonRight = Math.min(x + width, centerX + 8);
          hit({ key: `toggle:${item.section.id}:${track.id}`, x: buttonLeft, y: centerY - 8, width: buttonRight - buttonLeft, height: 16,
            label: `Select ${track.name ?? group.name + ' track ' + trackNumber} in ${this.sectionLabel(item.section.type)}. ${present ? 'Present' : 'Absent'}. ${manualNotes ? 'Manual notes override part settings.' : trackCustomized ? 'Customized track part.' : ''} Middle-click or press I to toggle presence.`,
            activate: () => this.trackSelected.emit({ sectionId: item.section.id, trackId: track.id }),
            reroll: () => this.tracksChangeRequested.emit({ sectionId: item.section.id, trackId: track.id, action: 'reroll' }),
            reset: () => this.tracksChangeRequested.emit({ sectionId: item.section.id, trackId: track.id, action: 'reset' }),
            togglePresence: () => this.toggleTrack(item.section, track) }, this.labelWidth, RULER_HEIGHT);
        }
        context.restore();
      }
    }
    context.restore();

    context.save();
    context.beginPath(); context.rect(this.labelWidth, 0, Math.max(0, this.width - this.labelWidth), RULER_HEIGHT); context.clip();
    for (const item of sections) {
      const x = this.beatX(item.startBeat);
      const width = sectionDurationBeats(this.project(), item.section) * this.pixelsPerBeat;
      if (x + width <= this.labelWidth || x >= this.width) continue;
      fill(x, 0, width, RULER_HEIGHT, this.selectedSectionIds().includes(item.section.id) ? '--surface-accent' : '--surface-base');
      fill(x, 0, 1, RULER_HEIGHT, '--border-subtle');
      context.save(); context.beginPath(); context.rect(x + 6, 0, Math.max(0, width - 12), RULER_HEIGHT); context.clip();
      const range = this.measureLabel(item);
      context.font = `10px ${monoFont}`;
      const rangeWidth = context.measureText(range).width;
      context.save(); context.beginPath();
      context.rect(x + 7, 0, Math.max(0, width - rangeWidth - 22), RULER_HEIGHT); context.clip();
      text(this.sectionLabel(item.section.type), x + 7, 15, '--text-primary', `600 10px ${headingFont}`);
      context.restore();
      context.textAlign = 'right';
      text(range, x + width - 7, 15, '--text-muted');
      context.textAlign = 'start';
      context.restore();
      hit({ key: `heading:${item.section.id}`, x, y: 0, width, height: RULER_HEIGHT,
        label: `${this.sectionLabel(item.section.type)}, bars ${this.measureLabel(item)}`,
        activate: (event) => this.selectSection(item.section.id, event),
        reroll: () => this.tracksChangeRequested.emit({ sectionId: item.section.id, action: 'reroll' }),
        reset: () => this.tracksChangeRequested.emit({ sectionId: item.section.id, action: 'reset' }),
        duplicate: () => this.sectionDuplicateRequested.emit(item.section.id),
        remove: () => this.sectionRemoveRequested.emit(item.section.id) }, this.labelWidth, 0);
    }
    context.restore();

    context.save(); context.beginPath(); context.rect(0, RULER_HEIGHT, this.labelWidth, Math.max(0, this.height - RULER_HEIGHT)); context.clip();
    for (let row = 0; row < groups.length; row++) {
      const group = groups[row];
      const y = RULER_HEIGHT + row * this.rowHeight - this.offsetY;
      if (y + this.rowHeight <= RULER_HEIGHT || y >= this.height) continue;
      fill(0, y, this.labelWidth, this.rowHeight, this.isGroupSelected(group) ? '--surface-accent' : '--surface-low');
      fill(0, y + this.rowHeight - 1, this.labelWidth, 1, '--border-subtle');
      context.fillStyle = color(group.role === 'drums' ? '--role-drums' : `--role-${ROLE_COLORS[group.role]}`);
      context.beginPath(); context.arc(this.labelWidth / 2, y + this.rowHeight / 2, 5, 0, Math.PI * 2); context.fill();
      hit({ key: `group:${group.role}`, x: 0, y, width: this.labelWidth, height: this.rowHeight,
        label: `Select ${group.name} group`, activate: () => this.groupSelected.emit(group.role) }, 0, RULER_HEIGHT);
    }
    context.restore();
    fill(0, 0, this.labelWidth, RULER_HEIGHT, '--surface-base');
    fill(0, RULER_HEIGHT - 1, this.width, 1, '--border-subtle');
    const loop = this.loopRange();
    if (loop) {
      const left = Math.max(this.labelWidth, this.beatX(loop.startBeat));
      const right = Math.min(this.width, this.beatX(loop.endBeat));
      if (right > left) fill(left, RULER_HEIGHT - 2, right - left, 2, '--loop-line');
    }
    fill(this.labelWidth - 1, RULER_HEIGHT, 1, this.height - RULER_HEIGHT, '--border-subtle');
    const focused = this.hits.find((area) => area.key === this.focusedKey);
    if (focused) {
      context.strokeStyle = this.accent;
      context.strokeRect(focused.x + 1, focused.y + 1, focused.width - 2, focused.height - 2);
      canvas.setAttribute('aria-label', `${focused.label} Use arrow keys to navigate and Enter to activate.`);
    }
    this.drawPlayhead(this.playheadPercent());
  }

  private drawPlayhead(percent: number): void {
    if (!this.ready || this.width <= 0 || this.height <= 0) return;
    const context = this.playheadCanvas().nativeElement.getContext('2d');
    if (!context) return;
    const dpr = Math.min(globalThis.devicePixelRatio || 1, 2);
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    context.clearRect(0, 0, this.width, this.height);
    const totalBeats = this.sections().reduce((sum, item) => sum + sectionDurationBeats(this.project(), item.section), 0);
    const x = this.beatX(Math.min(100, Math.max(0, percent)) / 100 * totalBeats);
    if (x < this.labelWidth || x > this.width) return;
    context.fillStyle = this.accent;
    context.fillRect(Math.min(this.width - 1, Math.floor(x)), RULER_HEIGHT, 1, this.height - RULER_HEIGHT);
  }

  private hitAt(event: MouseEvent): OverviewHitArea | undefined {
    const bounds = this.scoreCanvas().nativeElement.getBoundingClientRect();
    const x = (event.clientX - bounds.left) * this.width / Math.max(1, bounds.width);
    const y = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
    for (let index = this.hits.length - 1; index >= 0; index--) {
      const area = this.hits[index];
      if (x >= area.x && x < area.x + area.width && y >= area.y && y < area.y + area.height) return area;
    }
    return undefined;
  }

  onCanvasClick(event: MouseEvent): void {
    if (event.button && event.button !== 0) return;
    const area = this.hitAt(event);
    if (!area) {
      this.selectionCleared.emit();
      return;
    }
    this.focusedKey = area.key;
    area.activate(event);
    this.scheduleDraw();
  }

  onCanvasPointerdown(event: PointerEvent): void {
    if (event.button === 1) event.preventDefault();
  }

  onCanvasAuxclick(event: MouseEvent): void {
    if (event.button !== 1 && event.button !== 2) return;
    event.preventDefault();
    const area = this.hitAt(event);
    if (event.shiftKey) {
      if (event.button === 1) area?.reroll?.();
      else area?.reset?.();
    } else if (event.button === 1) {
      if (area?.duplicate) area.duplicate();
      else area?.togglePresence?.();
    } else area?.remove?.();
  }

  onCanvasContextmenu(event: MouseEvent): void {
    const area = this.hitAt(event);
    if (area && (event.shiftKey ? area.reset : area.remove)) event.preventDefault();
  }

  onPointerMove(event: PointerEvent): void {
    const area = this.hitAt(event);
    const canvas = this.scoreCanvas().nativeElement;
    canvas.style.cursor = area ? 'pointer' : 'default';
    canvas.title = area ? area.label + (area.reroll ? ' Shift+middle-click rerolls settings; Shift+right-click resets settings and manual notes.' : '')
      + (area.duplicate ? ' Middle-click duplicates; right-click removes this section.' : '') : '';
  }

  onCanvasKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.selectionCleared.emit();
      return;
    }
    if (!this.hits.length) return;
    const index = this.hits.findIndex((area) => area.key === this.focusedKey);
    if (event.key.toLowerCase() === 'i') {
      const area = this.hits[Math.max(0, index)];
      if (!area.togglePresence) return;
      event.preventDefault();
      area.togglePresence();
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.hits[Math.max(0, index)].activate(event);
    } else if (event.key.startsWith('Arrow')) {
      event.preventDefault();
      const offset = event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1;
      this.focusedKey = this.hits[(index + offset + this.hits.length) % this.hits.length].key;
    } else return;
    this.scheduleDraw();
  }
}
