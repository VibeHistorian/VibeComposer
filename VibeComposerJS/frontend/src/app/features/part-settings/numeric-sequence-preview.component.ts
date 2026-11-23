import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { sequenceY, type NumericSequenceRange } from './numeric-sequence-dialog.component';

@Component({
  selector: 'vc-numeric-sequence-preview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true' },
  template: `
    @let points = coordinates();
    <svg viewBox="0 0 100 100" preserveAspectRatio="none">
      @if (display() !== 'steps') {
        <polyline [attr.points]="line()" />
      }
    </svg>
    @if (display() === 'steps') {
      @for (point of points; track $index) {
        <span class="step-dot" [style.left.%]="point.x" [style.top.%]="point.y"></span>
      }
    }
    @if (highlightedPoint(); as point) {
      <span class="step-highlight" [style.left.%]="point.x" [style.top.%]="point.y"></span>
    }
  `,
  styles: `
    :host { display: block; position: relative; width: 100%; height: 100%; }
    svg { display: block; width: 100%; height: 100%; }
    polyline { fill: none; stroke: var(--text-primary); vector-effect: non-scaling-stroke; }
    polyline { stroke-width: 1.5; }
    .step-dot { position: absolute; width: 4px; height: 4px; background: var(--accent-primary); transform: translate(-50%, -50%); }
    .step-highlight { position: absolute; width: 6px; height: 6px; border-radius: 50%; background: hsl(from var(--accent-primary) calc(h + 180) s l); transform: translate(-50%, -50%); pointer-events: none; }
  `,
})
export class NumericSequencePreviewComponent {
  readonly values = input.required<readonly number[]>();
  readonly range = input.required<NumericSequenceRange>();
  readonly normalized = input(true);
  readonly display = input<'line' | 'steps'>('line');
  readonly highlightedIndex = input<number | null>(null);

  readonly coordinates = computed(() => {
    const values = this.values();
    const range = this.normalized() ? { minimum: Math.min(...values), maximum: Math.max(...values) } : this.range();
    const padding = this.display() === 'steps' ? 10 : 5;
    return values.map((value, index) => ({
      x: (index + .5) / values.length * 100,
      y: sequenceY(value, range, padding, 100 - 2 * padding),
    }));
  });

  readonly line = computed(() => {
    const points = this.coordinates();
    if (points.length === 1) return `25,${points[0].y} 75,${points[0].y}`;
    return points.map(({ x, y }) => `${x},${y}`).join(' ');
  });
  readonly highlightedPoint = computed(() => this.coordinates()[this.highlightedIndex() ?? -1]);
}
