import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { clipGlideSegments, glideNeutral, glideValue, PITCH_GLIDE_SEMITONES, type GlideLane, type GlideSegment } from '../../core/music/phrase-glides';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { scoreBeat, scoreX, VELOCITY_HEIGHT, type ScoreTimeGeometry } from './score-geometry';

const PADDING = 4;
const HEIGHT = VELOCITY_HEIGHT - PADDING * 2;
interface GlidePoint { readonly beat: number; readonly value: number; }
interface GlideSource { readonly label: string; readonly segments: readonly GlideSegment[]; }
export interface ScoreGlideReference {
  readonly ghosts: readonly GlideSource[];
  readonly sources: readonly GlideSource[];
  readonly effective: readonly GlideSegment[];
}

@Component({
  selector: 'vc-score-glide-lane',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[style.height.px]': 'laneHeight', '[class.disabled]': 'disabled()' },
  template: `
    <canvas #surface role="img" [attr.aria-label]="label()" [title]="label()"
      (pointerdown)="begin($event)" (pointermove)="move($event)" (pointerup)="finish($event)"
      (pointercancel)="cancel()" (lostpointercapture)="cancel()" (pointerleave)="leave()"
      (contextmenu)="$event.preventDefault(); $event.stopPropagation()"></canvas>
    @if (cursor(); as point) {
      <span class="glide-cursor" aria-hidden="true" [style.left.px]="point.x">
        {{ point.text }}
        @if (point.sources) { <small>{{ point.sources }}</small> }
      </span>
    }
  `,
  styles: `
    :host { position: absolute; inset: auto 0 0; z-index: 2; }
    canvas { display: block; width: 100%; height: 100%; touch-action: none; cursor: crosshair; }
    :host(.disabled) canvas { cursor: default; }
    .glide-cursor { position: absolute; bottom: calc(100% + 4px); transform: translateX(-50%);
      white-space: nowrap; pointer-events: none; background: var(--surface-base);
      border: 1px solid var(--border-default); color: var(--text-primary); border-radius: var(--radius-small);
      padding: 3px 6px; font: 10px var(--font-mono); }
    .glide-cursor small { display: block; max-width: 210px; overflow: hidden; text-overflow: ellipsis; color: var(--text-secondary); }
  `,
})
export class ScoreGlideLaneComponent {
  readonly lane = input.required<GlideLane>();
  readonly geometry = input.required<ScoreTimeGeometry>();
  readonly bounds = input.required<{ readonly startBeat: number; readonly endBeat: number }>();
  readonly segments = input.required<readonly GlideSegment[]>();
  readonly reference = input<ScoreGlideReference | null>(null);
  readonly snapBeats = input.required<number>();
  readonly playheadBeat = input.required<number>();
  readonly disabled = input(false);
  readonly stroke = output<readonly GlideSegment[]>();
  readonly laneHeight = VELOCITY_HEIGHT;
  readonly label = computed(() => `${this.lane() === 'pitch' ? 'Pitch' : 'Volume'} glide. Drag across beats to draw a channel curve.`
    + (this.reference() ? ' Dashed lines show other phrases. The bright outline shows the channel output.' : ''));
  readonly cursor = signal<{ x: number; text: string; sources?: string } | null>(null);
  private readonly surface = viewChild<ElementRef<HTMLCanvasElement>>('surface');
  private readonly preferences = inject(UiPreferencesService);
  private readonly drawing = signal<readonly GlideSegment[]>([]);
  private drag: { id: number; first: GlidePoint; points: Map<number, number> } | null = null;

  constructor() {
    effect(() => {
      this.lane();
      this.bounds();
      this.disabled();
      this.segments();
      this.cancel();
    });
    effect(() => {
      this.preferences.appearance();
      const canvas = this.surface()?.nativeElement;
      const geometry = this.geometry(), bounds = this.bounds(), lane = this.lane();
      const segments = this.segments(), preview = this.drawing(), playheadBeat = this.playheadBeat();
      const reference = this.reference();
      if (canvas && geometry.width > 0) this.paint(canvas, geometry, bounds, lane, segments, preview, playheadBeat, reference);
    });
  }

  begin(event: PointerEvent): void {
    if (this.drag) { this.cancel(); return; }
    if (event.button !== 0 || this.disabled()) return;
    const canvas = event.currentTarget as HTMLCanvasElement;
    const point = this.point(event, canvas, false);
    if (!point) return;
    event.preventDefault();
    canvas.setPointerCapture(event.pointerId);
    this.drag = { id: event.pointerId, first: point, points: new Map([[point.beat, point.value]]) };
    this.showCursor(point);
  }

  move(event: PointerEvent): void {
    if (this.drag && this.drag.id !== event.pointerId) return;
    const canvas = event.currentTarget as HTMLCanvasElement;
    if (!this.drag && this.showChannelCursor(event, canvas)) return;
    const point = this.point(event, canvas, !!this.drag);
    if (!point) { this.cursor.set(null); return; }
    this.showCursor(point);
    if (!this.drag || point.beat === this.drag.first.beat) return;
    this.drag.points.set(point.beat, point.value);
    const points = [...this.drag.points].sort(([left], [right]) => left - right);
    const segments: GlideSegment[] = [];
    for (let index = 1; index < points.length; index++) {
      const [startBeat, startValue] = points[index - 1], [endBeat, endValue] = points[index];
      const previous = segments.at(-1);
      if (previous && Math.abs(previous.startValue + (endValue - previous.startValue)
        * (startBeat - previous.startBeat) / (endBeat - previous.startBeat) - startValue) < 1e-6) {
        segments[segments.length - 1] = { ...previous, endBeat, endValue };
      } else segments.push({ startBeat, endBeat, startValue, endValue });
    }
    this.drawing.set(segments);
  }

  finish(event: PointerEvent): void {
    if (this.drag?.id !== event.pointerId) return;
    this.move(event);
    const segments = this.drawing();
    this.cancel();
    if (segments.length) this.stroke.emit(segments);
  }

  cancel(): void {
    this.drag = null;
    this.drawing.set([]);
    this.cursor.set(null);
  }

  leave(): void { if (!this.drag) this.cursor.set(null); }

  private point(event: PointerEvent, canvas: HTMLCanvasElement, captured: boolean): GlidePoint | null {
    const rect = canvas.getBoundingClientRect(), geometry = this.geometry(), bounds = this.bounds();
    if (!geometry.width || !rect.width) return null;
    const beat = scoreBeat((event.clientX - rect.left) * geometry.width / rect.width, geometry);
    if (!captured && (beat < bounds.startBeat || beat > bounds.endBeat)) return null;
    const free = this.preferences.matchesModifiers('freeGrid', event);
    const step = free || !this.snapBeats() ? Math.max(0.001, geometry.totalBeats / geometry.horizontalScale / 320) : this.snapBeats();
    const targetBeat = Math.max(bounds.startBeat, Math.min(bounds.endBeat, Math.round(beat / step) * step));
    const y = (event.clientY - rect.top) * VELOCITY_HEIGHT / Math.max(1, rect.height);
    const fraction = Math.max(0, Math.min(1, (VELOCITY_HEIGHT - PADDING - y) / HEIGHT));
    // Round near the center to make the pitch neutral line easy to hit.
    const value = this.lane() === 'pitch'
      ? Math.round((2 * fraction - 1) * PITCH_GLIDE_SEMITONES * 10) / 10 : Math.round(fraction * 100) / 100;
    return { beat: targetBeat, value };
  }

  private showCursor(point: GlidePoint): void {
    const geometry = this.geometry();
    const x = Math.max(61, Math.min(geometry.width - 61, scoreX(point.beat, geometry)));
    const value = this.lane() === 'pitch' ? `${point.value > 0 ? '+' : ''}${point.value} st` : `${Math.round(point.value * 100)}%`;
    this.cursor.set({ x, text: `Beat ${Number((point.beat + 1).toFixed(2))} · ${value}` });
  }

  private showChannelCursor(event: PointerEvent, canvas: HTMLCanvasElement): boolean {
    const reference = this.reference();
    if (!reference) return false;
    const geometry = this.geometry(), rect = canvas.getBoundingClientRect();
    if (!rect.width || !geometry.width) return false;
    const x = (event.clientX - rect.left) * geometry.width / rect.width;
    const beat = scoreBeat(x, geometry);
    const atBeat = (source: GlideSource) => source.segments.find(segment => segment.startBeat <= beat && beat < segment.endBeat);
    const format = (value: number) => this.lane() === 'pitch'
      ? `${value > 0 ? '+' : ''}${Number(value.toFixed(2))} st` : `${Math.round(value * 100)}%`;
    const ghosts = reference.ghosts.flatMap(source => {
      const segment = atBeat(source);
      return segment ? [`${source.label} ${format(glideValue(segment, beat))}`] : [];
    });
    if (!ghosts.length) return false;
    const winner = reference.sources.find(atBeat);
    const segment = winner && atBeat(winner);
    const value = segment ? glideValue(segment, beat) : glideNeutral(this.lane());
    const inset = Math.min(116, geometry.width / 2);
    this.cursor.set({ x: Math.max(inset, Math.min(geometry.width - inset, x)),
      text: `Channel ${format(value)} · ${winner?.label ?? 'Neutral'}`,
      sources: ghosts.join(', ') });
    return true;
  }

  private paint(canvas: HTMLCanvasElement, geometry: ScoreTimeGeometry, bounds: { startBeat: number; endBeat: number },
    lane: GlideLane, segments: readonly GlideSegment[], preview: readonly GlideSegment[], playheadBeat: number,
    reference: ScoreGlideReference | null): void {
    const ratio = Math.min(2, globalThis.devicePixelRatio || 1);
    const width = Math.round(geometry.width * ratio), height = Math.round(VELOCITY_HEIGHT * ratio);
    if (canvas.width !== width) canvas.width = width;
    if (canvas.height !== height) canvas.height = height;
    const context = canvas.getContext('2d');
    if (!context) return;
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.clearRect(0, 0, geometry.width, VELOCITY_HEIGHT);
    const style = getComputedStyle(canvas);
    const color = style.getPropertyValue(lane === 'pitch' ? '--accent-primary' : '--role-arpeggio-strong').trim();
    const y = (value: number) => PADDING + HEIGHT * (lane === 'pitch' ? (1 - value / PITCH_GLIDE_SEMITONES) / 2 : 1 - value);
    const neutral = y(glideNeutral(lane));
    const leftBeat = scoreBeat(0, geometry), rightBeat = scoreBeat(geometry.width, geometry);
    const start = Math.max(bounds.startBeat, leftBeat), end = Math.min(bounds.endBeat, rightBeat);
    const x = (beat: number) => scoreX(beat, geometry);
    const line = (x1: number, y1: number, x2: number, y2: number) => {
      context.beginPath(); context.moveTo(x1, y1); context.lineTo(x2, y2); context.stroke();
    };
    context.strokeStyle = color;
    context.globalAlpha = 0.5;
    context.lineWidth = 1;
    context.setLineDash([3, 3]);
    if (end > start) line(x(start), neutral, x(end), neutral);
    if (reference) {
      const trace = (curves: readonly GlideSegment[]) => {
        for (const s of clipGlideSegments(curves, leftBeat, rightBeat)) line(x(s.startBeat), y(s.startValue), x(s.endBeat), y(s.endValue));
      };
      context.strokeStyle = style.getPropertyValue('--text-secondary').trim();
      context.globalAlpha = 0.8;
      context.lineWidth = 2;
      for (const ghost of reference.ghosts) trace(ghost.segments);
      context.setLineDash([]);
      context.strokeStyle = style.getPropertyValue('--text-primary').trim();
      context.lineWidth = 3.5;
      trace(reference.effective);
      context.strokeStyle = color;
    }
    context.setLineDash([]);
    const draw = (curves: readonly GlideSegment[], alpha: number) => {
      const visible = clipGlideSegments(curves, start, end);
      for (const [index, segment] of visible.entries()) {
        const left = x(segment.startBeat), right = x(segment.endBeat);
        const y1 = y(segment.startValue), y2 = y(segment.endValue);
        context.fillStyle = color;
        context.globalAlpha = alpha * 0.2;
        context.beginPath(); context.moveTo(left, neutral); context.lineTo(left, y1);
        context.lineTo(right, y2); context.lineTo(right, neutral); context.fill();
        context.globalAlpha = alpha * 0.6;
        context.lineWidth = 1;
        const previous = visible[index - 1], next = visible[index + 1];
        if (segment.startBeat > start) {
          const from = previous?.endBeat === segment.startBeat ? y(previous.endValue) : neutral;
          if (from !== y1) line(left, from, left, y1);
        }
        if (next?.startBeat !== segment.endBeat && segment.endBeat < end && y2 !== neutral) line(right, y2, right, neutral);
        context.lineWidth = 2;
        context.globalAlpha = alpha;
        line(left, y1, right, y2);
      }
    };
    draw(segments, preview.length ? 0.45 : 0.9);
    draw(preview, 1);
    if (playheadBeat >= leftBeat && playheadBeat <= rightBeat) {
      context.globalAlpha = 0.65;
      context.strokeStyle = style.getPropertyValue('--text-primary').trim();
      context.lineWidth = 1;
      line(x(playheadBeat), 0, x(playheadBeat), VELOCITY_HEIGHT);
    }
  }
}
