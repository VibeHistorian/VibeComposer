import { drumInstrumentName } from '../../core/music/drum-instruments';
import { musicalTime } from '../../core/ui/musical-time';
import { trackColor } from '../../core/ui/track-color';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { ModifierKeysService } from '../../core/ui/modifier-keys.service';
import type { ModifierState } from '../../core/ui/modifier-observer';
import {
  AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, inject, input, output, signal, untracked, viewChild,
} from '@angular/core';
import type { Application as PixiApplication, Color as PixiColor, Graphics as PixiGraphics } from 'pixi.js';
import { MidiOutputService } from '../../core/audio/midi-output.service';
import type { PhraseSource } from '../../core/music/phrase';
import { snapMidiToScale, type PhraseDragMode } from '../../core/music/phrase-edit';
import type { ArrangedPart } from '../../core/music/part-role';
import type { PhraseNote } from '../../core/music/phrase-note';
import {
  canvasPoint, clampViewport, contentHeight, laneHeight, maxVerticalScale, overviewPitchRange, pitchAt,
  scoreBeat, scoreHeight, scoreX, sectionViewport, VELOCITY_HEIGHT,
  type ScorePitchGeometry, type ScoreTimeGeometry,
} from './score-geometry';
import {
  addHitArea, boxNoteIds, HIT_BUCKET_WIDTH, hitAreaAt, noteDragZone, type NoteHitArea as HitArea,
} from './score-hit-testing';
import { previewScoreDrag, scoreDragChanged, type ScoreDrag, type ScoreInteractionPreview } from './score-interaction';

export interface ScoreCanvasTrack {
  readonly id: string;
  readonly name: string;
  readonly color: ArrangedPart;
  /** A combined score lane retains the IDs of its independent source tracks. */
  readonly drumPitch?: number;
  readonly trackOrder?: number;
  readonly trackIds?: readonly string[];
}

export interface ScoreCanvasNote extends PhraseNote {
  readonly part: string;
  readonly trackName?: string;
  readonly trackOrder?: number;
  readonly trackCount?: number;
  readonly phraseSource?: PhraseSource;
  readonly sectionId?: string;
  readonly color: ArrangedPart;
  readonly topPercent: number;
  readonly opacity: number;
}

export interface ScoreCanvasSection {
  readonly startBeat: number;
}

export interface ScoreSectionFocus {
  readonly startBeat: number;
  readonly endBeat: number;
}

export interface ScoreViewport {
  readonly scale: number;
  readonly offsetPercent: number;
  readonly width: number;
  readonly laneHeight: number;
  readonly verticalOffset: number;
  readonly scoreHeight: number;
}

type NoteHitArea = HitArea<ScoreCanvasNote>;

interface ScoreCanvasModel {
  readonly tracks: readonly ScoreCanvasTrack[];
  readonly notes: readonly ScoreCanvasNote[];
  readonly sections: readonly ScoreCanvasSection[];
  readonly totalBeats: number;
  readonly timelineStartBeat?: number;
  readonly selectedTrackId: string;
  readonly hiddenTrackIds: ReadonlySet<string>;
}

const MIN_TIME_GRID_SPACING = 8;
const COLORS: Readonly<Record<ArrangedPart, { note: string; velocity: string; glow: string }>> = {
  melody: { note: '--role-melody-strong', velocity: '--role-melody-strong', glow: '--role-melody-glow' },
  bass: { note: '--role-bass-strong', velocity: '--role-bass-strong', glow: '--role-bass-glow' },
  chords: { note: '--role-chords-strong', velocity: '--role-chords-strong', glow: '--role-chords-glow' },
  arpeggio: { note: '--role-arpeggio-strong', velocity: '--role-arpeggio-strong', glow: '--role-arpeggio-glow' },
  drums: { note: '--role-snare', velocity: '--role-drums', glow: '--role-snare-glow' },
};

@Component({
  selector: 'vc-score-canvas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './score-canvas.component.html',
  styleUrl: './score-canvas.component.css',
})
export class ScoreCanvasComponent implements AfterViewInit, OnDestroy {
  readonly tracks = input.required<readonly ScoreCanvasTrack[]>();
  readonly notes = input.required<readonly ScoreCanvasNote[]>();
  readonly ghostNotes = input<readonly ScoreCanvasNote[]>([]);
  readonly notesReady = input(true);
  readonly sections = input.required<readonly ScoreCanvasSection[]>();
  readonly chordBoundaries = input<readonly { beat: number; hue: number }[]>([]);
  readonly totalBeats = input.required<number>();
  readonly timelineStartBeat = input(0);
  readonly playheadPercent = input.required<number>();
  readonly selectedTrackId = input.required<string>();
  readonly hiddenTrackIds = input.required<ReadonlySet<string>>();
  readonly editBounds = input<ScoreSectionFocus | null>(null);
  readonly editable = input(false);
  readonly velocityVisible = input(false);
  readonly selectedNoteIds = input<readonly string[]>([]);
  readonly snapBeats = input(0.25);
  readonly placementDuration = input(0.5);
  private placementPreview: { trackId?: string; midi: number; startBeat: number } | null = null;
  private placementPointer: { clientX: number; clientY: number } | null = null;
  readonly snapToScale = input(true);
  readonly scalePitchClasses = input<readonly number[]>([]);
  private middlePress: { pointerId: number; startedEmpty: boolean; canApply: boolean } | null = null;
  readonly selectionChanged = output<readonly string[]>();
  readonly notesChanged = output<{ notes: readonly PhraseNote[]; duplicate: boolean }>();
  readonly splitRequested = output<{ id: string; beat: number }>();
  readonly deleteRequested = output<string>();
  readonly auditionRequested = output<readonly PhraseNote[]>();
  private lastAudition = 0;
  readonly noteChanged = output<PhraseNote>();
  readonly applyRequested = output<void>();
  readonly noteAdded = output<{ trackId?: string; midi: number; startBeat: number }>();
  readonly gutterRows = signal<readonly { midi: number; label: string; tooltip?: string; y: number; height: number; drum: boolean; black: boolean; inScale: boolean }[]>([]);
  readonly octaveLines = signal<readonly { midi: number; y: number; height: number; opacity: number }[]>([]);
  readonly timeTooltip = signal<{ text: string; x: number; y: number } | null>(null);
  readonly noteTooltip = signal<{ time: string; track: string; color: string; pitch: string; velocity: number; source: string; x: number; y: number } | null>(null);
  private pitchMin = 48;
  private pitchMax = 72;
  private drumPitches: number[] = [];
  private drumRows: { trackId: string; midi: number; label: string }[] = [];
  private harmonicHeight = 0;
  private drumHeight = 0;
  private drag: ScoreDrag<ScoreCanvasNote> | null = null;
  private box: { x: number; y: number; endX: number; endY: number; pointerId: number; previous: readonly string[]; split?: boolean } | null = null;
  private shape: { mode: 'pitch' | 'velocity'; pointerId: number; notes: Map<string, PhraseNote> } | null = null;
  private readonly touches = new Map<number, { x: number; y: number }>();
  private pinch: { distance: number; x: number; y: number } | null = null;
  private touchGesture = false;
  private touchTap: { x: number; y: number } | null = null;
  private readonly onContextMenu = (event: MouseEvent): void => { if (this.editable()) event.preventDefault(); };
  private suppressClick = false;
  readonly sectionFocus = input<ScoreSectionFocus | null>(null);
  readonly noteSelected = output<ScoreCanvasNote>();
  readonly noteTrackSelected = output<ScoreCanvasNote>();
  readonly trackSelected = output<ScoreCanvasTrack>();
  readonly seekRequested = output<number>();
  readonly viewportChanged = output<ScoreViewport>();

  private readonly rendererHost = viewChild.required<ElementRef<HTMLDivElement>>('rendererHost');
  private readonly horizontalScroll = viewChild<ElementRef<HTMLDivElement>>('horizontalScroll');
  private readonly horizontalContent = viewChild<ElementRef<HTMLDivElement>>('horizontalContent');
  private readonly verticalScroll = viewChild<ElementRef<HTMLDivElement>>('verticalScroll');
  private readonly verticalContent = viewChild<ElementRef<HTMLDivElement>>('verticalContent');
  private staticGraphics?: PixiGraphics;
  private dynamicGraphics?: PixiGraphics;
  private colorClass?: typeof PixiColor;
  private readonly preferences = inject(UiPreferencesService);
  private readonly modifiers = inject(ModifierKeysService);
  private stopModifiers?: () => void;
  private readonly midi = inject(MidiOutputService);
  private readonly modelEffect = effect(() => {
    this.preferences.appearance();
    this.midi.drumMapping();
    const editable = this.editable();
    if (editable && !this.wasEditable) {
      this.overviewViewport = { horizontalScale: this.horizontalScale, horizontalOffset: this.horizontalOffset,
        verticalScale: this.verticalScale, verticalOffset: this.verticalOffset };
      this.verticalScale = 1;
      this.verticalOffset = 0;
      this.pendingEditPitchFocus = true;
    } else if (!editable && this.wasEditable && this.overviewViewport) {
      Object.assign(this, this.overviewViewport);
      this.overviewViewport = null;
      this.pendingSectionFocus = null;
      this.pendingEditPitchFocus = false;
    }
    const focus = this.sectionFocus();
    if (focus !== this.lastSectionFocus) {
      this.pendingSectionFocus = !editable && this.wasEditable ? null : focus;
      this.lastSectionFocus = focus;
    }
    this.wasEditable = editable;
    this.velocityVisible();
    this.snapBeats();
    this.snapToScale();
    this.scalePitchClasses();
    this.notesReady();
    this.ghostNotes();
    this.chordBoundaries();
    this.preferences.scoreChordGrid();
    this.preferences.scoreOutlineOtherTracks();
    this.preferences.scoreTriangleNotes();
    this.model = {
      tracks: this.tracks(),
      notes: this.notes(),
      sections: this.sections(),
      totalBeats: this.totalBeats(),
      timelineStartBeat: this.timelineStartBeat(),
      selectedTrackId: this.selectedTrackId(),
      hiddenTrackIds: this.hiddenTrackIds(),
    };
    this.noteTooltip.set(null); this.timeTooltip.set(null);
    this.drawStatic();
  });
  private readonly playheadEffect = effect(() => {
    this.playhead = this.playheadPercent();
    this.selectedNoteIds();
    this.drawDynamic();
  });

  private app?: PixiApplication;
  private resizeObserver?: ResizeObserver;
  private model?: ScoreCanvasModel;
  private hitAreas: NoteHitArea[] = [];
  private hitAreaBuckets = new Map<number, NoteHitArea[]>();
  private hitColumnCount = 0;
  private playhead = 0;
  private hoverX: number | null = null;
  private hoveredNote: { area: NoteHitArea; zone: 'start' | 'move' | 'duration' } | null = null;
  private focusedNoteIndex = -1;
  private destroyed = false;
  private width = 0;
  private height = 0;
  private horizontalScale = 1;
  // Offset in fractions of the full song, independent of canvas size.
  private horizontalOffset = 0;
  private verticalScale = 1;
  private verticalOffset = 0;
  private lastSectionFocus: ScoreSectionFocus | null = null;
  private wasEditable = false;
  private overviewViewport: { horizontalScale: number; horizontalOffset: number; verticalScale: number; verticalOffset: number } | null = null;
  private pendingSectionFocus: ScoreSectionFocus | null = null;
  private pendingEditPitchFocus = false;

  ngAfterViewInit(): void {
    const host = this.rendererHost().nativeElement;
    this.stopModifiers = this.modifiers.subscribe(this.onModifiersChanged);
    this.resizeObserver = new ResizeObserver(() => this.resizeRenderer());
    this.resizeObserver.observe(host);
    void this.initializeRenderer(host);
  }

  ngOnDestroy(): void {
    this.stopModifiers?.();
    this.destroyed = true;
    this.resizeObserver?.disconnect();
    const canvas = this.app?.canvas;
    canvas?.removeEventListener('click', this.onCanvasClick);
    canvas?.removeEventListener('contextmenu', this.onContextMenu);
    canvas?.removeEventListener('pointerdown', this.onPointerDown);
    canvas?.removeEventListener('pointerup', this.onPointerUp);
    canvas?.removeEventListener('pointercancel', this.onPointerCancel);
    canvas?.removeEventListener('auxclick', this.onAuxClick);
    canvas?.removeEventListener('dblclick', this.onDoubleClick);
    canvas?.removeEventListener('pointermove', this.onPointerMove);
    canvas?.removeEventListener('pointerleave', this.onPointerLeave);
    canvas?.removeEventListener('keydown', this.onCanvasKeydown);
    canvas?.removeEventListener('focus', this.onCanvasFocus);
    canvas?.removeEventListener('wheel', this.onCanvasWheel);
    this.app?.destroy({ removeView: true }, { children: true });
    this.app = undefined;
  }

  private async initializeRenderer(host: HTMLDivElement): Promise<void> {
    let app: PixiApplication | undefined;
    try {
      const pixi = await import('pixi.js');
      if (this.destroyed) return;
      this.colorClass = pixi.Color;
      this.staticGraphics = new pixi.Graphics({ roundPixels: false });
      this.dynamicGraphics = new pixi.Graphics({ roundPixels: false });
      app = new pixi.Application();
      await app.init({
        width: Math.max(1, host.clientWidth),
        height: Math.max(1, host.clientHeight),
        backgroundAlpha: 0,
        antialias: true,
        autoDensity: true,
        autoStart: false,
        clearBeforeRender: true,
        preference: 'webgl',
        resolution: Math.min(globalThis.devicePixelRatio || 1, 2),
      });
      if (this.destroyed) {
        app.destroy({ removeView: false }, { children: true });
        return;
      }
      this.app = app;
      app.stage.addChild(this.staticGraphics, this.dynamicGraphics);
      const canvas = app.canvas;
      // Pixi creates this element outside Angular's scoped template styles.
      // Absolute positioning prevents its backing size from growing the grid.
      canvas.style.position = 'absolute';
      canvas.style.inset = '0';
      canvas.style.touchAction = 'none';
      canvas.style.display = 'block';
      canvas.style.width = '100%';
      canvas.style.height = '100%';
      canvas.style.outline = 'none';
      canvas.setAttribute('role', 'application');
      canvas.setAttribute('tabindex', '0');
      canvas.setAttribute('aria-keyshortcuts', 'ArrowLeft ArrowRight ArrowUp ArrowDown Enter');
      host.appendChild(canvas);
      canvas.addEventListener('click', this.onCanvasClick);
      canvas.addEventListener('contextmenu', this.onContextMenu);
      canvas.addEventListener('pointerdown', this.onPointerDown);
      canvas.addEventListener('pointerup', this.onPointerUp);
      canvas.addEventListener('pointercancel', this.onPointerCancel);
      canvas.addEventListener('auxclick', this.onAuxClick);
      canvas.addEventListener('dblclick', this.onDoubleClick);
      canvas.addEventListener('pointermove', this.onPointerMove);
      canvas.addEventListener('pointerleave', this.onPointerLeave);
      canvas.addEventListener('keydown', this.onCanvasKeydown);
      canvas.addEventListener('focus', this.onCanvasFocus);
      // A non-passive listener is required to suppress browser Ctrl+wheel zoom.
      canvas.addEventListener('wheel', this.onCanvasWheel, { passive: false });
      this.resizeRenderer();
    } catch (error: unknown) {
      if (app?.renderer) app.destroy({ removeView: false }, { children: true });
      console.error('Could not initialize the score renderer.', error);
    }
  }

  private readonly onCanvasWheel = (event: WheelEvent): void => {
    this.noteTooltip.set(null); this.timeTooltip.set(null);
    event.preventDefault();
    event.stopPropagation();
    if (!this.app || this.width <= 0) return;
    const zoomTime = this.preferences.matchesShortcut('zoomTime', event);
    const zoomPitch = this.preferences.matchesShortcut('zoomPitch', event);
    const panTime = this.preferences.matchesShortcut('panTime', event);
    if (!zoomTime && !zoomPitch && !panTime && !this.preferences.matchesShortcut('panPitch', event)) return;
    const horizontal = zoomTime || panTime;
    const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? (horizontal ? this.width : this.scoreHeight) : 1;
    const delta = (event.deltaY || event.deltaX) * unit;
    if (zoomTime) {
      const bounds = this.app.canvas.getBoundingClientRect();
      const cursor = Math.min(1, Math.max(0, (event.clientX - bounds.left) / Math.max(1, bounds.width)));
      const anchor = this.horizontalOffset + cursor / this.horizontalScale;
      this.horizontalScale = Math.min(128, Math.max(1, this.horizontalScale * Math.exp(-delta * 0.002)));
      this.horizontalOffset = anchor - cursor / this.horizontalScale;
    } else if (zoomPitch) {
      if (this.harmonicHeight <= 0) return;
      const bounds = this.app.canvas.getBoundingClientRect();
      const cursorY = Math.min(this.harmonicHeight, Math.max(0, (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height)));
      const anchor = (this.verticalOffset + cursorY) / this.laneHeight;
      this.verticalScale = Math.min(this.maxVerticalScale, Math.max(1, this.verticalScale * Math.exp(-delta * 0.002)));
      this.verticalOffset = anchor * this.laneHeight - cursorY;
    } else if (panTime) {
      this.horizontalOffset += delta / (this.width * this.horizontalScale);
    } else {
      this.verticalOffset += event.deltaY * unit;
      this.horizontalOffset += event.deltaX * unit / (this.width * this.horizontalScale);
    }
    this.updateViewport();
    this.drawStatic();
  };

  private updateViewport(): void {
    Object.assign(this, clampViewport(this.timeGeometry, this.pitchGeometry));
    const horizontalScroll = this.horizontalScroll()?.nativeElement;
    const horizontalContent = this.horizontalContent()?.nativeElement;
    const verticalScroll = this.verticalScroll()?.nativeElement;
    const verticalContent = this.verticalContent()?.nativeElement;
    if (horizontalScroll && horizontalContent) {
      horizontalContent.style.width = `${this.width * this.horizontalScale}px`;
      horizontalScroll.scrollLeft = this.horizontalOffset * this.width * this.horizontalScale;
    }
    if (verticalScroll && verticalContent) {
      // Use the measured box: clientHeight can round up and create subpixel overflow.
      // Drums and velocity controls stay pinned; only harmonic overflow scrolls.
      const overflow = Math.max(0, this.contentHeight - this.scoreHeight);
      const scrollable = overflow > 1e-6;
      verticalScroll.style.overflowY = scrollable ? 'auto' : 'hidden';
      const viewportHeight = verticalScroll.getBoundingClientRect().height;
      verticalContent.style.height = `${scrollable ? viewportHeight + overflow : Math.floor(viewportHeight)}px`;
      verticalScroll.scrollTop = this.verticalOffset;
    }
    this.viewportChanged.emit({ scale: this.horizontalScale, offsetPercent: this.horizontalOffset * 100,
      width: this.width, laneHeight: this.laneHeight, verticalOffset: this.verticalOffset, scoreHeight: this.scoreHeight });
  }

  private get timeGeometry(): ScoreTimeGeometry {
    return { width: this.width, horizontalScale: this.horizontalScale, horizontalOffset: this.horizontalOffset,
      totalBeats: this.model?.totalBeats ?? 1, startBeat: this.model?.timelineStartBeat ?? 0 };
  }

  private get pitchGeometry(): ScorePitchGeometry {
    return { height: this.height, velocityVisible: this.velocityVisible(), harmonicHeight: this.harmonicHeight,
      drumHeight: this.drumHeight, pitchMin: this.pitchMin, pitchMax: this.pitchMax,
      verticalScale: this.verticalScale, verticalOffset: this.verticalOffset };
  }

  private get scoreHeight(): number { return scoreHeight(this.pitchGeometry); }
  private get maxVerticalScale(): number { return maxVerticalScale(this.pitchGeometry, this.editable()); }
  private get contentHeight(): number { return contentHeight(this.pitchGeometry); }
  private get laneHeight(): number { return laneHeight(this.pitchGeometry); }

  private get interactionPreview(): ScoreInteractionPreview {
    return {
      notes: this.drag?.preview ?? (this.shape ? [...this.shape.notes.values()] : []),
      selectionBox: this.box,
      active: !!(this.drag || this.box || this.shape),
    };
  }

  onHorizontalScroll(): void {
    const offset = (this.horizontalScroll()?.nativeElement.scrollLeft ?? 0) / Math.max(1, this.width * this.horizontalScale);
    if (Math.abs(offset - this.horizontalOffset) * this.width * this.horizontalScale < 1) return;
    this.horizontalOffset = offset;
    this.drawStatic();
  }

  onVerticalScroll(): void {
    const offset = this.verticalScroll()?.nativeElement.scrollTop ?? 0;
    if (Math.abs(offset - this.verticalOffset) < 1) return;
    this.verticalOffset = offset;
    this.drawStatic();
  }

  private scoreX(beat: number): number {
    return scoreX(beat, this.timeGeometry);
  }

  private readonly onCanvasClick = (event: MouseEvent): void => {
    if (this.suppressClick) { this.suppressClick = false; return; }
    if (this.editable()) return;
    if (event.button && event.button !== 0) return;
    const area = this.hitAreaAt(event);
    this.focusedNoteIndex = area ? this.hitAreas.indexOf(area) : -1;
    this.describeCanvas(area?.note);
    this.drawDynamic();
    if (area) this.noteSelected.emit(area.note);
    else if (this.app && this.model && this.width > 0) {
      const bounds = this.app.canvas.getBoundingClientRect();
      const y = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
      if (y >= 0 && y < this.scoreHeight) {
        const track = this.model.tracks.find(track => track.color === (y >= this.harmonicHeight ? 'drums' : this.model?.tracks.find(item => item.id === this.model?.selectedTrackId)?.color)) ?? this.model.tracks[0];
        if (track) this.trackSelected.emit(track);
      }
      const fraction = (event.clientX - bounds.left) / Math.max(1, bounds.width);
      const beat = (this.horizontalOffset + fraction / this.horizontalScale) * this.model.totalBeats;
      this.seekRequested.emit(beat + (this.model.timelineStartBeat ?? 0));
    }
  };

  private readonly onPointerMove = (event: PointerEvent): void => {
    if (this.touches.has(event.pointerId)) {
      this.touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (this.touchTap && Math.hypot(event.clientX - this.touchTap.x, event.clientY - this.touchTap.y) > 8) this.touchTap = null;
      const next = this.pinchPosition();
      if (next && this.pinch && this.app) {
        const bounds = this.app.canvas.getBoundingClientRect();
        const previousX = (this.pinch.x - bounds.left) / Math.max(1, bounds.width);
        const nextX = (next.x - bounds.left) / Math.max(1, bounds.width);
        const anchor = this.horizontalOffset + previousX / this.horizontalScale;
        const ratio = next.distance / Math.max(1, this.pinch.distance);
        const previousY = (this.pinch.y - bounds.top) * this.height / Math.max(1, bounds.height);
        const nextY = (next.y - bounds.top) * this.height / Math.max(1, bounds.height);
        const pitchAnchor = (this.verticalOffset + previousY) / this.laneHeight;
        this.horizontalScale = Math.min(128, Math.max(1, this.horizontalScale * ratio));
        this.horizontalOffset = anchor - nextX / this.horizontalScale;
        if (this.harmonicHeight > 0) {
          this.verticalScale = Math.min(this.maxVerticalScale, Math.max(1, this.verticalScale * ratio));
          this.verticalOffset = pitchAnchor * this.laneHeight - nextY;
        }
        this.pinch = next;
        this.updateViewport();
        this.drawStatic();
      }
      if (this.touchGesture) return;
    }
    const canvas = this.app?.canvas;
    if (!canvas) return;
    const bounds = canvas.getBoundingClientRect();
    this.hoverX = Math.min(this.width, Math.max(0, (event.clientX - bounds.left) * this.width / Math.max(1, bounds.width)));
    if (this.drag) {
      const drag = this.drag;
      const dx = (event.clientX - drag.x) * this.width / Math.max(1, bounds.width);
      const dy = (event.clientY - drag.y) * this.height / Math.max(1, bounds.height);
      const previousPreview = drag.preview;
      drag.preview = previewScoreDrag(drag, { x: dx, y: dy }, {
        time: this.timeGeometry, laneHeight: this.laneHeight, snapBeats: this.snapBeats(),
        shiftKey: this.preferences.matchesModifiers('freeGrid', event)
          && !(drag.duplicate && this.preferences.matchesGestureHint('shapePitch', event)),
        scalePitchClasses: this.snapToScale() ? this.scalePitchClasses() : [],
        startBeat: this.editBounds()?.startBeat ?? 0,
        endBeat: this.editBounds()?.endBeat ?? this.model?.totalBeats ?? 1,
      });
      drag.current = drag.preview.find(note => note.id === drag.note.id) ?? drag.note;
      const pitchChanged = drag.preview.some((note, index) => note.midi !== previousPreview[index]?.midi);
      if (pitchChanged || (drag.mode === 'velocity' && performance.now() - this.lastAudition > 300)) {
        this.auditionRequested.emit(drag.preview);
        this.lastAudition = performance.now();
      }
      this.suppressClick ||= Math.abs(dx) + Math.abs(dy) > 3;
    }
    if (this.box) {
      this.box.endX = this.hoverX;
      this.box.endY = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
    }
    if (this.shape) {
      const beat = this.beatAt(event);
      const y = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
      const selectedIds = new Set(this.selectedNoteIds());
      for (const note of this.model?.notes ?? []) {
        if (this.shape.mode === 'velocity' && selectedIds.size && !selectedIds.has(note.id)) continue;
        if (note.startBeat <= beat && beat < note.startBeat + note.durationBeats) {
          const midi = note.color === 'drums' ? note.midi : this.snapPitch(this.pitchMax - Math.floor((y + this.verticalOffset) / this.laneHeight));
          const velocity = Math.max(1, Math.min(127, Math.round(127 * (1 - y / this.scoreHeight))));
          this.shape.notes.set(note.id, { ...note, ...(this.shape.mode === 'pitch' ? { midi } : { velocity }) });
        }
      }
    }
    this.placementPointer = { clientX: event.clientX, clientY: event.clientY };
    this.updatePlacement(event);
    const area = this.hitAreaAt(event);
    const y = (event.clientY - bounds.top) * this.height / Math.max(1, bounds.height);
    this.hoveredNote = this.editable() && area && y < this.scoreHeight
      && !this.drag && !this.box && !this.shape && event.pointerType !== 'touch'
      ? { area, zone: this.noteDragZone(area, this.hoverX, this.preferences.matchesGestureHint('trimStart', event)) } : null;
    canvas.style.cursor = this.hoveredNote ? (this.hoveredNote.zone === 'move' ? 'grab' : 'ew-resize')
      : area ? 'pointer' : 'default';
    this.drawDynamic();
    const hidden = this.box || this.shape || event.pointerType === 'touch' || y >= this.scoreHeight;
    if (hidden) this.timeTooltip.set(null);
    else if (this.drag) {
      const drag = this.drag;
      const end = drag.mode === 'duration';
      const current = drag.current.startBeat + (end ? drag.current.durationBeats : 0);
      const original = drag.note.startBeat + (end ? drag.note.durationBeats : 0);
      this.timeTooltip.set({ text: (end ? 'End ' : 'Start ') + musicalTime(current, false, this.preferences.midiPpq()) + ' (' + musicalTime(current - original, true, this.preferences.midiPpq()) + ')',
        x: Math.max(8, Math.min(this.hoverX + 14, this.width - 280)), y: Math.max(8, y > this.height - 60 ? y - 38 : y + 14) });
    } else if (!area) this.timeTooltip.set({ text: musicalTime(this.beatAt(event), false, this.preferences.midiPpq()),
      x: Math.max(8, Math.min(this.hoverX + 14, this.width - 140)), y: Math.max(8, y > this.height - 60 ? y - 38 : y + 14) });
    else this.timeTooltip.set(null);
    this.showNoteTooltip(this.drag || this.box || this.shape || event.pointerType === 'touch' ? undefined : area,
      this.hoverX, y);
  };

  private showNoteTooltip(area: NoteHitArea | undefined, x: number, y: number): void {
    if (!area) { this.noteTooltip.set(null); return; }
    const note = area.note;
    const name = note.trackName ?? this.model?.tracks.find(track => track.id === note.part)?.name ?? note.color;
    const order = note.trackOrder;
    const defaultName = `${note.color[0].toUpperCase()}${order}`;
    const sources: Record<PhraseSource, string> = {
      'global-generated': 'Global generated', 'global-edited': 'Global edited phrase',
      'section-generated': 'Section generated', 'section-edited': 'Section edited phrase',
      'global-section-harmony': 'Global track [Section harmony]',
    };
    this.noteTooltip.set({
      time: musicalTime(note.startBeat, false, this.preferences.midiPpq()),
      track: order && name !== defaultName ? `${name} (#${order})` : name,
      color: `var(--role-${note.color})`,
      pitch: note.color === 'drums' ? this.midi.drumLabel(note.midi)
        : `${['C', 'C♯', 'D', 'D♯', 'E', 'F', 'F♯', 'G', 'G♯', 'A', 'A♯', 'B'][note.midi % 12]}${Math.floor(note.midi / 12) - 1} [${note.midi}]`,
      velocity: note.velocity, source: sources[note.phraseSource ?? 'global-generated'],
      x: Math.max(8, Math.min(x + 14, this.width - 248)),
      y: y + 110 <= this.height ? y + 14 : Math.max(8, y - 102),
    });
  }

  private readonly onPointerLeave = (): void => {
    this.noteTooltip.set(null); this.timeTooltip.set(null);
    this.placementPointer = null;
    this.placementPreview = null;
    this.hoverX = null;
    this.hoveredNote = null;
    this.drawDynamic();
  };

  private readonly onCanvasFocus = (): void => {
    if (this.focusedNoteIndex < 0 && this.hitAreas.length > 0) {
      this.focusedNoteIndex = 0;
      this.describeCanvas(this.hitAreas[0].note);
      this.drawDynamic();
    }
  };

  private readonly onCanvasKeydown = (event: KeyboardEvent): void => {
    if (this.hitAreas.length === 0) return;
    if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
      event.preventDefault();
      this.focusedNoteIndex = (this.focusedNoteIndex + 1 + this.hitAreas.length) % this.hitAreas.length;
    } else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
      event.preventDefault();
      this.focusedNoteIndex = this.focusedNoteIndex <= 0 ? this.hitAreas.length - 1 : this.focusedNoteIndex - 1;
    } else if (event.key === 'Enter' && this.focusedNoteIndex >= 0 && this.focusedNoteIndex < this.hitAreas.length) {
      event.preventDefault();
      this.noteSelected.emit(this.hitAreas[this.focusedNoteIndex].note);
      return;
    } else {
      return;
    }
    const note = this.hitAreas[this.focusedNoteIndex].note;
    this.describeCanvas(note);
    this.drawDynamic();
  };

  private describeCanvas(note?: ScoreCanvasNote): void {
    const canvas = this.app?.canvas;
    if (!canvas) return;
    canvas.setAttribute('aria-label', note
      ? `Song score. Selected ${note.color} note, pitch ${note.midi}, velocity ${note.velocity}. Use arrow keys to choose a note and Enter to edit.`
      : `Song score with ${this.hitAreas.length} notes. Use arrow keys to choose a note and Enter to edit.`);
  }

  private hitAreaAt(event: MouseEvent | PointerEvent): NoteHitArea | undefined {
    const canvas = this.app?.canvas;
    if (!canvas) return undefined;
    const bounds = canvas.getBoundingClientRect();
    return hitAreaAt(canvasPoint(event.clientX, event.clientY, bounds, { width: this.width, height: this.height }), {
      width: this.width, height: this.height, scoreHeight: this.scoreHeight, harmonicHeight: this.harmonicHeight,
      drumHeight: this.drumHeight, trackCount: this.model?.tracks.length ?? 0,
      editable: this.editable(), velocityVisible: this.velocityVisible(), selectedIds: this.selectedNoteIds(),
      areas: this.hitAreas, columnCount: this.hitColumnCount, buckets: this.hitAreaBuckets,
    });
  }

  private beatAt(event: MouseEvent): number {
    const bounds = this.app!.canvas.getBoundingClientRect();
    return scoreBeat(canvasPoint(event.clientX, event.clientY, bounds, { width: this.width, height: this.height }).x, this.timeGeometry);
  }

  private snapPitch(midi: number): number {
    return snapMidiToScale(midi, this.snapToScale() ? this.scalePitchClasses() : []);
  }
  private inScale(midi: number): boolean {
    return this.editable() && this.snapToScale() && this.scalePitchClasses().includes(midi % 12);
  }
  scaleColoringActive(): boolean {
    return this.editable() && this.snapToScale() && this.scalePitchClasses().length > 0;
  }
  private emptyCanvasPoint(event: MouseEvent): boolean {
    const bounds = this.app?.canvas.getBoundingClientRect();
    return !!bounds && event.clientX >= bounds.left && event.clientX < bounds.left + bounds.width
      && event.clientY >= bounds.top && event.clientY < bounds.top + bounds.height && !this.hitAreaAt(event);
  }
  private readonly onPointerDown = (event: PointerEvent): void => {
    this.noteTooltip.set(null); this.timeTooltip.set(null);
    if (event.pointerType === 'touch' && this.app) {
      event.preventDefault();
      this.touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      this.app.canvas.setPointerCapture(event.pointerId);
      if (this.touches.size > 1) {
        this.touchGesture = true;
        this.suppressClick = true;
        this.touchTap = null;
        this.drag = null;
        this.box = null;
        this.shape = null;
        this.pinch = this.pinchPosition();
        this.drawDynamic();
        return;
      }
      if (this.touchGesture) return;
      // Defer empty-space note creation until release so a pinch cannot add a note.
      if (this.editable() && !this.hitAreaAt(event)) {
        this.touchTap = { x: event.clientX, y: event.clientY };
        return;
      }
    }
    if (event.button === 0) this.suppressClick = false;
    if (event.button === 1) {
      event.preventDefault();
      if (!this.editable()) {
        const area = this.hitAreaAt(event);
        if (area) this.noteTrackSelected.emit(area.note);
        return;
      }
    }
    if (!this.editable() || !this.app) return;
    event.preventDefault();
    this.app.canvas.focus?.();
    const area = this.hitAreaAt(event);
    if (this.preferences.matchesShortcut('dragVelocity', event)) this.middlePress = { pointerId: event.pointerId, startedEmpty: this.emptyCanvasPoint(event), canApply: false };
    const bounds = this.app.canvas.getBoundingClientRect();
    const { x, y } = canvasPoint(event.clientX, event.clientY, bounds, { width: this.width, height: this.height });
    if (this.preferences.matchesShortcut('seek', event) && !area && x >= 0 && x < this.width && y >= 0 && y < this.scoreHeight) {
      this.seekRequested.emit(this.beatAt(event));
      return;
    }
    const matches = (id: import('../../core/ui/shortcuts').ShortcutId) => this.preferences.matchesGesture(id, event);
    if (matches('boxSelect') || matches('boxExtend') || matches('splitNote')) {
      this.box = { x, y, endX: x, endY: y, pointerId: event.pointerId, previous: matches('boxExtend') ? this.selectedNoteIds() : [], split: matches('splitNote') };
    } else if (!area && matches('addNote') && !matches('shapePitch')) {
      this.selectionChanged.emit([]);
      this.onDoubleClick(event);
    } else if ((matches('shapePitch') || matches('shapeVelocity')) && (!area || !this.selectedNoteIds().includes(area.note.id) || matches('shapeVelocity'))) {
      this.shape = { mode: matches('shapeVelocity') ? 'velocity' : 'pitch', pointerId: event.pointerId, notes: new Map() };
      this.onPointerMove(event);
    } else if (area && (matches('seek') || matches('addNote') || matches('shapePitch') || matches('trimStart') || matches('dragVelocity') || matches('dragDuration') || matches('dragPitch'))) {
      const ids = this.selectedNoteIds().includes(area.note.id) ? this.selectedNoteIds() : [area.note.id];
      this.selectionChanged.emit(ids);
      const mode: PhraseDragMode = y >= this.scoreHeight ? 'velocity' : matches('dragDuration') ? 'duration' : matches('dragPitch') ? 'pitch' : matches('dragVelocity') ? 'velocity'
        : this.noteDragZone(area, x, matches('trimStart'));
      const originals = (this.model?.notes ?? []).filter(note => ids.includes(note.id));
      this.drag = { note: area.note, x: event.clientX, y: event.clientY, pointerId: event.pointerId,
        current: area.note, mode, originals, preview: originals, duplicate: matches('shapePitch'), lockPitch: area.note.color === 'drums' };
      this.auditionRequested.emit(originals);
      this.lastAudition = performance.now();
    } else if (matches('addNote')) {
      this.selectionChanged.emit([]);
      this.onDoubleClick(event);
    }
    this.placementPreview = null;
    this.app.canvas.setPointerCapture(event.pointerId);
    this.hoveredNote = null;
    this.drawDynamic();
  };

  private readonly onPointerUp = (event: PointerEvent): void => {
    if (this.touches.delete(event.pointerId)) {
      this.pinch = null;
      if (this.app?.canvas.hasPointerCapture(event.pointerId)) this.app.canvas.releasePointerCapture(event.pointerId);
      if (this.touchGesture) {
        if (!this.touches.size) this.touchGesture = false;
        return;
      }
      if (this.touchTap) {
        this.touchTap = null;
        this.seekRequested.emit(this.beatAt(event));
      }
    }
    if (this.middlePress?.pointerId === event.pointerId) {
      this.middlePress.canApply = this.middlePress.startedEmpty && this.emptyCanvasPoint(event) && !this.drag && !this.shape;
    }
    if (this.box?.pointerId === event.pointerId) {
      const box = this.box;
      this.box = null;
      const area = this.hitAreaAt(event);
      if (Math.abs(box.endX - box.x) + Math.abs(box.endY - box.y) < 3 && area) {
        if (box.split) {
          const grid = this.preferences.matchesModifiers('freeGrid', event) ? 0 : this.snapBeats();
          const beat = this.beatAt(event);
          this.splitRequested.emit({ id: area.note.id, beat: grid ? Math.round(beat / grid) * grid : beat });
        } else this.deleteRequested.emit(area.note.id);
      } else {
        const ids = boxNoteIds(this.hitAreas, box, { x: box.endX, y: box.endY });
        this.selectionChanged.emit([...new Set([...box.previous, ...ids])]);
      }
    }
    if (this.shape?.pointerId === event.pointerId) {
      this.notesChanged.emit({ notes: [...this.shape.notes.values()], duplicate: false });
      this.shape = null;
    }
    if (this.drag?.pointerId === event.pointerId) {
      const changed = scoreDragChanged(this.drag);
      const { current, preview, duplicate } = this.drag;
      this.drag = null;
      if (changed) {
        if (preview.length === 1 && !duplicate) this.noteChanged.emit(current);
        else this.notesChanged.emit({ notes: preview, duplicate });
      }
    }
    if (this.app?.canvas.hasPointerCapture(event.pointerId)) this.app.canvas.releasePointerCapture(event.pointerId);
    if (event.button === 0 && this.middlePress?.canApply) {
      this.middlePress = null;
      this.applyRequested.emit();
    }
    this.drawDynamic();
  };
  private pinchPosition(): { distance: number; x: number; y: number } | null {
    const [first, second] = [...this.touches.values()];
    return first && second ? { distance: Math.hypot(second.x - first.x, second.y - first.y),
      x: (first.x + second.x) / 2, y: (first.y + second.y) / 2 } : null;
  }
  private readonly onPointerCancel = (event?: PointerEvent): void => {
    if (event) this.touches.delete(event.pointerId);
    else this.touches.clear();
    this.placementPreview = null;
    this.pinch = null;
    this.touchTap = null;
    this.touchGesture = this.touches.size > 0;
    this.drag = null; this.box = null; this.shape = null; this.middlePress = null;
    this.drawDynamic();
  };
  private readonly onAuxClick = (event: MouseEvent): void => {
    if (!this.preferences.matchesShortcut('dragVelocity', event)) return;
    event.preventDefault();
    event.stopPropagation();
    if (!this.editable()) return;
    const canApply = this.middlePress?.canApply;
    this.middlePress = null;
    if (canApply && this.emptyCanvasPoint(event)) this.applyRequested.emit();
  };
  private updatePlacement(event: MouseEvent): void {
    const gesture = { button: 0, ctrlKey: event.ctrlKey, metaKey: event.metaKey, shiftKey: event.shiftKey, altKey: event.altKey } as MouseEvent;
    this.placementPreview = !event.buttons && !this.drag && !this.box && !this.shape
      && this.preferences.matchesGesture('addNote', gesture) && !this.preferences.matchesGesture('shapePitch', gesture)
      ? this.placementAt(event) : null;
  }
  private readonly onModifiersChanged = (state: ModifierState): void => {
    if (!state.active) { if (this.placementPointer) this.onPointerLeave(); return; }
    if (!this.editable() || !this.placementPointer || this.drag || this.box || this.shape) return;
    const event = { ...this.placementPointer, ctrlKey: state.ctrlKey, metaKey: state.metaKey,
      shiftKey: state.shiftKey, altKey: state.altKey } as MouseEvent;
    const previous = this.placementPreview;
    this.updatePlacement(event);
    const next = this.placementPreview;
    const area = this.hitAreaAt(event);
    const previousZone = this.hoveredNote?.zone;
    if (this.hoveredNote && area) {
      this.hoveredNote = { area, zone: this.noteDragZone(area, this.hoverX ?? 0, this.preferences.matchesGestureHint('trimStart', event)) };
      if (this.app) this.app.canvas.style.cursor = this.hoveredNote.zone === 'move' ? 'grab' : 'ew-resize';
    }
    if (previous?.midi !== next?.midi || previous?.startBeat !== next?.startBeat || previous?.trackId !== next?.trackId
      || !!previous !== !!next || previousZone !== this.hoveredNote?.zone) this.drawDynamic();
  };
  private readonly onDoubleClick = (event: MouseEvent): void => {
    if (event.detail > 1 || !this.preferences.matchesGesture('addNote', event)
      || this.preferences.matchesGesture('shapePitch', event)) return;
    const position = this.placementAt(event);
    if (position) this.noteAdded.emit(position);
  };
  private placementAt(event: MouseEvent): { trackId?: string; midi: number; startBeat: number } | null {
    if (!this.editable() || this.hitAreaAt(event) || !this.app || !this.model) return null;
    const bounds = this.app.canvas.getBoundingClientRect();
    const { x, y } = canvasPoint(event.clientX, event.clientY, bounds, { width: this.width, height: this.height });
    if (x < 0 || x >= this.width || y < 0 || y >= this.scoreHeight) return null;
    const midi = pitchAt(y, this.pitchGeometry, this.drumPitches);
    const grid = this.preferences.matchesModifiers('freeGrid', event) ? 0 : this.snapBeats();
    const rawBeat = scoreBeat(x, this.timeGeometry);
    const startBeat = grid ? Math.round(rawBeat / grid) * grid : rawBeat;
    const range = this.editBounds();
    if (range && (startBeat < range.startBeat || startBeat >= range.endBeat)) return null;
    const drum = y >= this.harmonicHeight && this.drumPitches.length > 0;
    const row = drum ? this.drumRows[Math.min(this.drumRows.length - 1,
      Math.floor((y - this.harmonicHeight) / this.drumHeight * this.drumRows.length))] : undefined;
    const track = row ? this.model.tracks.find(track => track.id === row.trackId) : undefined;
    return { ...(track ? { trackId: track.id } : {}), midi: drum ? midi : this.snapPitch(midi), startBeat };
  }

  private resizeRenderer(): void {
    const host = this.rendererHost().nativeElement;
    const width = Math.floor(host.clientWidth);
    const height = Math.floor(host.clientHeight);
    if (width <= 0 || height <= 0) return;
    this.width = width;
    this.height = height;
    this.app?.renderer.resize(width, height);
    this.drawStatic(true);
  }

  private noteDragZone(area: NoteHitArea, x: number, altKey = false): 'start' | 'move' | 'duration' {
    return noteDragZone(area, x, altKey);
  }

  private drawStatic(preservePitchPosition = false): void {
    this.noteTooltip.set(null); this.timeTooltip.set(null);
    this.placementPreview = null;
    const model = this.model;
    if (!model || !this.app || this.width <= 0 || this.height <= 0) return;
    const graphics = this.staticGraphics;
    const Color = this.colorClass;
    if (!graphics || !Color) return;
    graphics.clear();
    const focusedNote = this.hitAreas[this.focusedNoteIndex]?.note;
    this.hitAreas = [];
    this.hoveredNote = null;
    this.hitAreaBuckets.clear();
    const { tracks, notes, sections, selectedTrackId, hiddenTrackIds } = model;
    const totalBeats = Math.max(1, model.totalBeats);
    const focus = this.pendingSectionFocus;
    if (focus) {
      this.pendingSectionFocus = null;
      Object.assign(this, sectionViewport(focus.startBeat - (model.timelineStartBeat ?? 0), focus.endBeat - (model.timelineStartBeat ?? 0), totalBeats));
    }
    const scoreHeight = this.scoreHeight;
    const previousLaneHeight = this.laneHeight;
    this.hitColumnCount = Math.max(1, Math.ceil(this.width / HIT_BUCKET_WIDTH));
    const rootStyle = getComputedStyle(document.documentElement);
    const colorCache = new Map<string, { value: number; alpha: number }>();
    const color = (name: string): { value: number; alpha: number } => {
      const cached = colorCache.get(name);
      if (cached) return cached;
      const parsed = new Color(rootStyle.getPropertyValue(name).trim() || '#000000');
      const value = { value: parsed.toNumber(), alpha: parsed.alpha };
      colorCache.set(name, value);
      return value;
    };
    const fill = (x: number, y: number, width: number, height: number, name: string, alpha = 1): void => {
      const bottom = Math.min(scoreHeight, y + height);
      y = Math.max(0, y);
      height = bottom - y;
      if (width <= 0 || height <= 0) return;
      const parsed = color(name);
      graphics.rect(x, y, width, height).fill({ color: parsed.value, alpha: parsed.alpha * alpha });
    };
    const line = (x1: number, y1: number, x2: number, y2: number, name: string, alpha = 1): void => {
      const parsed = color(name);
      // Match a centered, one-CSS-pixel butt-cap stroke without constructing
      // and triangulating a stroked path for each axis-aligned grid line.
      if (x1 === x2) graphics.rect(x1 - 0.5, Math.min(y1, y2), 1, Math.abs(y2 - y1))
        .fill({ color: parsed.value, alpha: parsed.alpha * alpha });
      else graphics.rect(Math.min(x1, x2), y1 - 0.5, Math.abs(x2 - x1), 1)
        .fill({ color: parsed.value, alpha: parsed.alpha * alpha });
    };

    graphics.rect(0, 0, this.width, this.height).fill({ color: color('--surface-score').value });
    const ghosts = this.editable() ? this.ghostNotes() : [];
    const visibleNotes = notes.filter(note => !hiddenTrackIds.has(note.part));
    const harmonic = visibleNotes.filter(note => note.color !== 'drums');
    const notePitchMin = harmonic.length ? harmonic.reduce((min, note) => Math.min(min, note.midi), 127) : 48;
    const notePitchMax = harmonic.length ? harmonic.reduce((max, note) => Math.max(max, note.midi), 0) : 72;
    const phrasePitchMin = Math.max(0, notePitchMin - 2);
    const phrasePitchMax = Math.min(127, notePitchMax + 2);
    const drumTracks = tracks.filter(track => track.color === 'drums');
    this.drumRows = drumTracks.filter(track => !hiddenTrackIds.has(track.id)).map(track => {
      const midi = track.drumPitch ?? [...visibleNotes, ...ghosts].find(note => note.part === track.id)?.midi ?? 36;
      const duplicate = drumTracks.filter(other => (other.drumPitch ?? 36) === midi).length > 1;
      return { trackId: track.id, midi, label: drumInstrumentName(midi).toUpperCase()
        + (duplicate ? '[' + (track.trackOrder ?? drumTracks.indexOf(track) + 1) + ']' : '') };
    });
    for (const note of ghosts.filter(note => note.color === 'drums')) {
      if (!this.drumRows.some(row => row.trackId === note.part)) {
        this.drumRows.push({ trackId: note.part, midi: note.midi, label: drumInstrumentName(note.midi).toUpperCase() });
      }
    }
    this.drumRows.reverse();
    this.drumPitches = this.drumRows.map(row => row.midi);
    const hasDrums = this.drumRows.length > 0;
    this.drumHeight = hasDrums ? Math.min(scoreHeight * 0.27, Math.max(36, this.drumPitches.length * 14)) : 0;
    this.harmonicHeight = scoreHeight - this.drumHeight;
    const range = overviewPitchRange(notePitchMin, notePitchMax, this.harmonicHeight);
    this.pitchMin = this.editable() ? 0 : range.pitchMin;
    this.pitchMax = this.editable() ? 127 : range.pitchMax;
    if (this.pendingEditPitchFocus && this.notesReady()) {
      this.pendingEditPitchFocus = false;
      // Fit the phrase initially, but keep every MIDI pitch reachable by zooming and scrolling.
      this.verticalScale = Math.min(this.maxVerticalScale,
        this.harmonicHeight > 0 ? 128 / (phrasePitchMax - phrasePitchMin + 1) : 1);
      const phraseCenter = (notePitchMin + notePitchMax) / 2;
      this.verticalOffset = (this.pitchMax - phraseCenter + 0.5) * this.laneHeight - this.harmonicHeight / 2;
    } else {
      this.verticalScale = Math.min(this.maxVerticalScale, this.verticalScale);
      if (preservePitchPosition) {
        // Layout changes resize pitch rows; retain the same top pitch rather than a pixel offset.
        this.verticalOffset *= this.laneHeight / previousLaneHeight;
      }
    }
    this.updateViewport();
    const rows: { midi: number; label: string; tooltip?: string; y: number; height: number; drum: boolean; black: boolean; inScale: boolean }[] = [];
    for (let midi = this.pitchMax; midi >= this.pitchMin && this.harmonicHeight > 0; midi--) {
      const y = (this.pitchMax - midi) * this.laneHeight - this.verticalOffset;
      if (y + this.laneHeight <= 0 || y >= this.harmonicHeight) continue;
      const black = [1, 3, 6, 8, 10].includes(midi % 12);
      const h = Math.min(this.harmonicHeight, y + this.laneHeight) - Math.max(0, y);
      fill(0, y, this.width, h, black && !this.scaleColoringActive() ? '--score-pitch-band' : '--score-row-even');
      // Shade blocked pitches before drawing pitch and time grid subdivisions.
      if (this.scaleColoringActive() && !this.inScale(midi)) fill(0, Math.max(0, y), this.width, h, '--score-out-of-scale');
      if (this.inScale(midi)) fill(0, Math.max(0, y), this.width, h, '--score-in-scale');
      if (midi % 12 !== 11) line(0, Math.max(0, y), this.width, Math.max(0, y), '--grid-subdivision');
      rows.push({ midi, label: midi % 12 === 0 ? `C${Math.floor(midi / 12) - 1}` : '', y: Math.max(0, y), height: h, drum: false, black, inScale: this.inScale(midi) });
    }
    const drumRowHeight = this.drumHeight / Math.max(1, this.drumPitches.length);
    this.drumRows.forEach(({ midi, label }, index) => {
      const y = this.harmonicHeight + index * drumRowHeight;
      fill(0, y, this.width, drumRowHeight, index % 2 ? '--score-row-odd' : '--score-row-even');
      line(0, y, this.width, y, '--border-subtle');
      rows.push({ midi, label, tooltip: this.midi.drumLabel(midi), y, height: drumRowHeight, drum: true, black: false, inScale: false });
    });
    if (this.drumHeight > 0) line(0, this.harmonicHeight + 0.5, this.width, this.harmonicHeight + 0.5, '--border-strong');
    // Horizontal panning changes note geometry but leaves the pitch gutter
    // unchanged. Retain its signal value so Angular can skip those DOM updates.
    const previousRows = untracked(this.gutterRows);
    if (previousRows.length !== rows.length || rows.some((row, index) => {
      const previous = previousRows[index];
      return row.midi !== previous.midi || row.label !== previous.label || row.tooltip !== previous.tooltip
        || row.y !== previous.y || row.height !== previous.height || row.drum !== previous.drum
        || row.black !== previous.black || row.inScale !== previous.inScale;
    })) this.gutterRows.set(rows);
    const beatWidth = this.width * this.horizontalScale / totalBeats;
    // Reveal successively finer divisions only when they have room in CSS
    // pixels. At overview scale, continue thinning beyond individual bars.
    let gridStep = this.editable() ? this.snapBeats() * 4 : 1;
    while (beatWidth * gridStep / 4 < MIN_TIME_GRID_SPACING) gridStep *= 2;
    // Fade smoothly with zoom rather than jumping in brightness at each
    // subdivision threshold. Keep a faint floor for orientation at song scale.
    const timeGridAlpha = Math.max(0.22, Math.min(1, Math.sqrt(beatWidth / (MIN_TIME_GRID_SPACING * 4))));
    const origin = model.timelineStartBeat ?? 0;
    const firstStep = Math.ceil((origin + this.horizontalOffset * totalBeats) * 4 / gridStep) * gridStep;
    const lastStep = Math.min((origin + totalBeats) * 4, Math.ceil((origin + (this.horizontalOffset + 1 / this.horizontalScale) * totalBeats) * 4));
    for (let sixteenth = firstStep; sixteenth <= lastStep; sixteenth += gridStep) {
      const x = Math.floor(this.scoreX(sixteenth / 4)) + 0.5;
      const beat = sixteenth / 4;
      const gridColor = sixteenth % 16 === 0 ? '--grid-measure'
        : sixteenth % 4 === 0 ? '--grid-beat'
          : sixteenth % 2 === 0 ? '--grid-eighth' : '--grid-step';
      line(x, 0, x, this.height, gridColor, timeGridAlpha * (sixteenth % 16 === 0 ? 1 : 0.92));
      if (beat === origin + totalBeats) break;
    }
    for (const section of sections) {
      const x = Math.floor(this.scoreX(section.startBeat)) + 0.5;
      if (x < 0 || x > this.width) continue;
      line(x, 0, x, scoreHeight, '--grid-measure', timeGridAlpha);
    }
    if (this.preferences.scoreChordGrid()) for (const boundary of this.chordBoundaries()) {
      const x = Math.floor(this.scoreX(boundary.beat)) + 0.5;
      if (x < 0 || x > this.width) continue;
      const tint = new Color(`hsl(${boundary.hue} ${rootStyle.getPropertyValue('--chordlet-foreground-tone').trim()})`);
      graphics.rect(x - 0.5, 0, 1, scoreHeight).fill({ color: tint.toNumber(), alpha: tint.alpha * 0.175 });
    }
    // Pitch increases upward: the B/C boundary sits below C, not above it.
    // Use absolute MIDI octaves so brightness stays consistent while zooming or scrolling.
    const resolution = this.app.renderer?.resolution ?? 1;
    const octaveLineHeight = Math.max(1, Math.round(resolution)) / resolution;
    const octaveLines: { midi: number; y: number; height: number; opacity: number }[] = [];
    for (let midi = 12; midi <= 120; midi += 12) {
      const y = (this.pitchMax - midi + 1) * this.laneHeight - this.verticalOffset;
      if (midi <= this.pitchMin || midi > this.pitchMax || y <= 0 || y >= this.harmonicHeight) continue;
      // Filled, pixel-aligned strips avoid antialiasing different octaves to different brightnesses.
      const top = Math.round(y * resolution) / resolution;
      const height = Math.min(octaveLineHeight, this.harmonicHeight - top);
      if (height <= 0) continue;
      const opacity = 0.1 + 0.4 * midi / 120;
      fill(0, top, this.width, height, '--score-octave-line', opacity);
      octaveLines.push({ midi, y: top, height, opacity });
    }
    const previousLines = untracked(this.octaveLines);
    if (previousLines.length !== octaveLines.length || octaveLines.some((line, index) => {
      const previous = previousLines[index];
      return line.midi !== previous.midi || line.y !== previous.y
        || line.height !== previous.height || line.opacity !== previous.opacity;
    })) this.octaveLines.set(octaveLines);
    line(0, scoreHeight + 0.5, this.width, scoreHeight + 0.5, '--border-default');
    line(0, 0.5, this.width, 0.5, '--border-default');
    line(0.5, 0, 0.5, this.height, '--border-default');
    line(this.width - 0.5, 0, this.width - 0.5, this.height, '--border-default');

    const trackIndexes = new Map(tracks.flatMap((track, index) => (track.trackIds ?? [track.id]).map(id => [id, index] as const)));
    const progressive = this.preferences.appearance().progressiveTrackColoring !== false;
    const noteColorCache = new Map<string, { value: number; alpha: number }>();
    const tokens = new Map<string, string>();
    const token = (name: string): string => {
      let value = tokens.get(name);
      if (value === undefined) { value = rootStyle.getPropertyValue(name).trim(); tokens.set(name, value); }
      return value;
    };
    // Ghosts paint first and never enter the hit areas used by any interaction.
    for (const { note, ghost } of [
      ...ghosts.map(note => ({ note, ghost: true })),
      ...notes.map(note => ({ note, ghost: false })),
    ]) {
      const sourceIndex = trackIndexes.get(note.part);
      const trackIndex = note.color === 'drums' ? 1 : 0;
      if (!ghost && (sourceIndex === undefined || hiddenTrackIds.has(note.part))) continue;
      if (note.color === 'drums' && (!this.drumHeight || !this.drumRows.some(row => row.trackId === note.part))) continue;
      const x = this.scoreX(note.startBeat);
      const noteWidth = Math.max(3, note.durationBeats * beatWidth);
      if (x + noteWidth < 0 || x > this.width) continue;
      const centerY = note.color === 'drums'
        ? this.harmonicHeight + (this.drumRows.findIndex(row => row.trackId === note.part) + 0.5) * drumRowHeight
        : (this.pitchMax - note.midi + 0.5) * this.laneHeight - this.verticalOffset;
      const top = note.color === 'drums' ? this.harmonicHeight : 0;
      const bottom = note.color === 'drums' ? scoreHeight : this.harmonicHeight;
      if (centerY < top || centerY >= bottom) continue;
      const rowHeight = note.color === 'drums' ? drumRowHeight : this.laneHeight;
      const y = Math.max(top, centerY - rowHeight * 0.375);
      const noteHeight = Math.min(bottom, centerY + rowHeight * 0.375) - y;
      const selectionOpacity = selectedTrackId === note.part ? 1 : 0.8;
      const opacity = selectedTrackId === note.part ? note.opacity : this.editable() ? 0.3 : 0.3 + note.velocity / 127 * 0.5;
      const noteColors = COLORS[note.color];
      const order = progressive ? (note.trackOrder ?? 1) - 1 : 0;
      const count = note.trackCount ?? 1;
      const paletteKey = `${note.color}:${order}:${count}`;
      let parsedNote = noteColorCache.get(paletteKey);
      if (!parsedNote) {
        const mixed = new Color(trackColor(note.color, order, count, token));
        parsedNote = { value: mixed.toNumber(), alpha: mixed.alpha };
        noteColorCache.set(paletteKey, parsedNote);
      }
      if (ghost) {
        const parsed = parsedNote;
        if (noteHeight > 0) this.noteShape(graphics, x, y, noteWidth, noteHeight, Math.min(3, noteHeight / 2))
          .stroke({ color: parsed.value, alpha: note.opacity * parsed.alpha * 0.5, width: 1 });
        continue;
      }
      if (noteHeight > 0 && selectedTrackId === note.part && (!this.editable() || note.color === 'drums')) {
        for (let radius = 6; radius >= 1; radius--) {
          if (!this.editable() && this.preferences.scoreTriangleNotes()) {
            const glow = color(noteColors.glow);
            this.noteShape(graphics, x - radius, y - radius, noteWidth + radius * 2, noteHeight + radius * 2, 0)
              .fill({ color: glow.value, alpha: glow.alpha * (7 - radius) / 15 });
          } else {
            fill(x - radius, y - radius, noteWidth + radius * 2, noteHeight + radius * 2,
              noteColors.glow, (7 - radius) / 15);
          }
        }
      }
      const parsed = parsedNote;
      if (noteHeight > 0) {
        const shape = this.noteShape(graphics, x, y, noteWidth, noteHeight, Math.min(3, noteHeight / 2));
        if (!this.editable() && this.preferences.scoreOutlineOtherTracks() && selectedTrackId !== note.part) {
          shape.stroke({ color: parsed.value, alpha: opacity * parsed.alpha, width: 1 });
        } else {
          const fillOpacity = 0.15 + Math.max(0, Math.min(127, note.velocity)) / 127 * 0.65;
          shape.fill({ color: parsed.value, alpha: fillOpacity * parsed.alpha })
            .stroke({ color: parsed.value, alpha: opacity * parsed.alpha, width: 1 });
        }
      }
      if (this.velocityVisible()) {
        const velocityColor = parsedNote;
        const velocityHeight = Math.max(2, note.velocity / 127 * VELOCITY_HEIGHT);
        graphics.rect(x, this.height - velocityHeight, 2, velocityHeight)
          .fill({ color: velocityColor.value, alpha: (selectedTrackId === note.part ? 0.9 : 0.32) * velocityColor.alpha * selectionOpacity });
      }
      if (noteHeight <= 0) continue;
      const hitHalfHeight = Math.max(3, rowHeight * 0.375);
      const hitY = Math.max(top, centerY - hitHalfHeight);
      const hitArea: NoteHitArea = {
        note,
        trackIndex,
        visualBounds: { x, y, width: noteWidth, height: noteHeight },
        x: Math.max(0, x - 1),
        y: hitY,
        width: Math.min(this.width, x + Math.max(5, noteWidth + 2)) - Math.max(0, x - 1),
        height: Math.min(bottom, centerY + hitHalfHeight) - hitY,
      };
      this.hitAreas.push(hitArea);
      addHitArea(hitArea, this.hitColumnCount, this.hitAreaBuckets);
    }

    this.focusedNoteIndex = focusedNote ? this.hitAreas.findIndex((area) => area.note.id === focusedNote.id && area.note.part === focusedNote.part && area.note.startBeat === focusedNote.startBeat) : -1;
    this.describeCanvas(this.hitAreas[this.focusedNoteIndex]?.note);
    // Overlay inputs must not become dependencies of the static model effect.
    untracked(() => this.drawDynamic());
  }

  private noteShape(graphics: PixiGraphics, x: number, y: number, width: number, height: number, radius: number): PixiGraphics {
    return !this.editable() && this.preferences.scoreTriangleNotes()
      ? graphics.moveTo(x, y).lineTo(x + width, y + height / 2).lineTo(x, y + height).closePath()
      : graphics.roundRect(x, y, width, height, radius);
  }

  private drawDynamic(): void {
    if (!this.app || this.width <= 0 || this.height <= 0) return;
    const graphics = this.dynamicGraphics;
    const Color = this.colorClass;
    if (!graphics || !Color) return;
    graphics.clear();
    const accent = new Color(getComputedStyle(document.documentElement).getPropertyValue('--accent-primary').trim() || '#32d7f4');
    if (this.hoverX !== null) {
      const hoverX = Math.floor(this.hoverX) + 0.5;
      graphics.moveTo(hoverX, 0).lineTo(hoverX, this.height)
        .stroke({ color: accent.toNumber(), alpha: accent.alpha * 0.25, width: 1 });
    }
    const x = (this.playhead / 100 - this.horizontalOffset) * this.width * this.horizontalScale;
    if (x >= 0 && x <= this.width) {
      const position = Math.min(this.width - 1, x);
      const left = Math.floor(position);
      const fraction = position - left;
      // Approximate gamma correction keeps the split line from dimming between columns.
      for (const [column, weight] of [[left, 1 - fraction], [left + 1, fraction]]) {
        if (weight === 0) continue;
        graphics.moveTo(column + 0.5, 0).lineTo(column + 0.5, this.height)
          .stroke({ color: accent.toNumber(), alpha: accent.alpha * Math.pow(weight, 1 / 2.2), width: 1 });
      }
    }
    const style = getComputedStyle(document.documentElement);
    const beat = this.playhead / 100 * (this.model?.totalBeats ?? 1) + (this.model?.timelineStartBeat ?? 0);
    for (const area of this.hitAreas) {
      if (beat < area.note.startBeat || beat >= area.note.startBeat + area.note.durationBeats) continue;
      const bounds = !this.editable() && this.preferences.scoreTriangleNotes() ? (area.visualBounds ?? area) : area;
      const glowStrength = 1 - (beat - area.note.startBeat) / area.note.durationBeats;
      const glow = new Color(style.getPropertyValue(COLORS[area.note.color].note).trim() || '#32d7f4');
      this.noteShape(graphics, bounds.x - 3, bounds.y - 2, bounds.width + 6, bounds.height + 4, 3)
        .stroke({ color: glow.toNumber(), alpha: 0.325 * glowStrength, width: 3 });
      this.noteShape(graphics, bounds.x, bounds.y + bounds.height / 4, bounds.width, bounds.height / 2, 2)
        .fill({ color: glow.toNumber(), alpha: 0.4 * glowStrength });
    }
    const placement = this.placementPreview;
    if (placement && this.editable()) {
      const drumIndex = placement.trackId ? this.drumRows.findIndex(row => row.trackId === placement.trackId) : -1;
      const rowHeight = drumIndex >= 0 ? this.drumHeight / this.drumRows.length : this.laneHeight;
      const centerY = drumIndex >= 0 ? this.harmonicHeight + (drumIndex + 0.5) * rowHeight
        : (this.pitchMax - placement.midi + 0.5) * rowHeight - this.verticalOffset;
      const duration = Math.min(this.placementDuration(), (this.editBounds()?.endBeat ?? this.model?.totalBeats ?? 1) - placement.startBeat);
      graphics.roundRect(this.scoreX(placement.startBeat), centerY - rowHeight * 0.375,
        Math.max(3, this.scoreX(placement.startBeat + duration) - this.scoreX(placement.startBeat)), rowHeight * 0.75, Math.min(3, rowHeight / 2))
        .fill({ color: accent.toNumber(), alpha: 0.3 }).stroke({ color: accent.toNumber(), alpha: 0.7, width: 1 });
    }
    const interaction = this.interactionPreview;
    for (const note of interaction.notes) {
      const area = this.hitAreas.find(area => area.note.id === note.id);
      if (area) {
        const rowHeight = area.note.color === 'drums' ? this.drumHeight / Math.max(1, this.drumPitches.length) : this.laneHeight;
        const centerY = area.note.color === 'drums'
          ? this.harmonicHeight + (this.drumRows.findIndex(row => row.trackId === area.note.part) + 0.5) * rowHeight
          : (this.pitchMax - note.midi + 0.5) * rowHeight - this.verticalOffset;
        const noteHeight = rowHeight * 0.75;
        graphics.roundRect(this.scoreX(note.startBeat), centerY - noteHeight / 2, Math.max(3, note.durationBeats / (this.model?.totalBeats ?? 1) * this.width * this.horizontalScale), noteHeight, Math.min(3, noteHeight / 2))
          .fill({ color: accent.toNumber(), alpha: 0.9 });
        if (this.velocityVisible()) {
          const velocityHeight = note.velocity / 127 * (VELOCITY_HEIGHT - 6);
          graphics.rect(this.scoreX(note.startBeat), this.height - velocityHeight, 3, velocityHeight)
            .fill({ color: accent.toNumber(), alpha: 0.9 });
        }
      }
    }
    for (const area of this.hitAreas) {
      if (this.selectedNoteIds().includes(area.note.id)) graphics.roundRect(area.x - 1, area.y - 1, area.width + 2, area.height + 2, 3)
        .stroke({ color: accent.toNumber(), alpha: 1, width: 2 });
    }
    if (interaction.selectionBox) {
      const b = interaction.selectionBox;
      graphics.rect(Math.min(b.x, b.endX), Math.min(b.y, b.endY), Math.abs(b.endX - b.x), Math.abs(b.endY - b.y))
        .fill({ color: accent.toNumber(), alpha: 0.12 }).stroke({ color: accent.toNumber(), alpha: 0.8, width: 1 });
    }
    const hover = this.hoveredNote;
    if (hover && this.editable() && !interaction.active) {
      const bounds = hover.area.visualBounds ?? hover.area;
      const leftWidth = Math.min(5, hover.area.width / 3);
      const rightWidth = Math.min(7, hover.area.width / 3);
      // Use the same edge zones as pointer editing, clipped to the painted note.
      const left = Math.max(bounds.x, hover.area.x + leftWidth);
      const right = Math.min(bounds.x + bounds.width, hover.area.x + hover.area.width - rightWidth);
      const x = hover.zone === 'start' ? bounds.x : hover.zone === 'move' ? left : right;
      const end = hover.zone === 'start' ? left : hover.zone === 'move' ? right : bounds.x + bounds.width;
      const width = Math.max(0, end - x);
      if (width > 0) {
        // White at 20% mixes with the rendered note, including its current brightness.
        graphics.rect(x, bounds.y, width, bounds.height).fill({ color: 0xffffff, alpha: 0.2 });
        // Fading strips form a white glow only on the active part's outer edges.
        for (let distance = 1; distance <= 6; distance++) {
          const alpha = 0.3 * Math.pow(1 - distance / 7, 2);
          const left = x - (hover.zone === 'start' ? distance : 0);
          const right = end + (hover.zone === 'duration' ? distance : 0);
          graphics.moveTo(left, bounds.y - distance + 0.5).lineTo(right, bounds.y - distance + 0.5)
            .stroke({ color: 0xffffff, alpha, width: 1 });
          graphics.moveTo(left, bounds.y + bounds.height + distance - 0.5).lineTo(right, bounds.y + bounds.height + distance - 0.5)
            .stroke({ color: 0xffffff, alpha, width: 1 });
          if (hover.zone !== 'move') {
            const edge = hover.zone === 'start' ? left + 0.5 : right - 0.5;
            graphics.moveTo(edge, bounds.y - distance).lineTo(edge, bounds.y + bounds.height + distance)
              .stroke({ color: 0xffffff, alpha, width: 1 });
          }
        }
      }
    }
    const focused = this.hitAreas[this.focusedNoteIndex];
    if (focused) {
      graphics.roundRect(focused.x - 1, focused.y - 1, focused.width + 2, focused.height + 2, 3)
        .stroke({ color: accent.toNumber(), alpha: 0.95, width: 1 });
    }
    this.app.render();
  }
}
