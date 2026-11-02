import { AfterViewInit, Directive, ElementRef, OnDestroy, afterRenderEffect, inject, signal } from '@angular/core';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';

/** Fit chord names independently, reserving space for measure numbers. */
@Directive({ selector: '[vcScoreChordLabels]', host: {
  '[class.measures-compact]': 'compactMeasures()'
} })
export class ScoreChordLabelsDirective implements AfterViewInit, OnDestroy {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly preferences = inject(UiPreferencesService);
  readonly compactMeasures = signal(false);
  private resize?: ResizeObserver;
  private mutations?: MutationObserver;
  private readonly appearanceEffect = afterRenderEffect(() => {
    this.preferences.appearance();
    this.measure();
  });

  ngAfterViewInit(): void {
    const host = this.element.nativeElement;
    this.resize = new ResizeObserver(() => this.measure());
    this.resize.observe(host);
    this.mutations = new MutationObserver(() => this.measure());
    this.mutations.observe(host, { childList: true, subtree: true, characterData: true });
    this.measure();
  }

  private measure(): void {
    const slots = this.element.nativeElement.querySelectorAll<HTMLElement>('.score-chord-slot');
    this.compactMeasures.set([...slots].some(slot => {
      const label = slot.querySelector<HTMLElement>('.score-chord-label');
      const range = slot.querySelector<HTMLElement>('.measure-range');
      return !!range && (label?.querySelector<HTMLElement>('.score-chord-name')?.offsetWidth ?? 0) + 6 + range.offsetWidth + 16 > slot.clientWidth;
    }));
    for (const slot of slots) {
      const label = slot.querySelector<HTMLElement>('.score-chord-label');
      const name = label?.querySelector<HTMLElement>('.score-chord-name');
      if (!label || !name) continue;
      const measure = slot.querySelector<HTMLElement>(this.compactMeasures() ? '.measure-end' : '.measure-range');
      const available = Math.max(0, slot.clientWidth - 8 - (measure ? measure.offsetWidth + 8 : 0));
      label.style.maxWidth = `${available}px`;
      label.classList.toggle('chord-clipped', name.offsetWidth + 6 > available);
    }
  }

  ngOnDestroy(): void {
    this.resize?.disconnect();
    this.mutations?.disconnect();
  }
}
