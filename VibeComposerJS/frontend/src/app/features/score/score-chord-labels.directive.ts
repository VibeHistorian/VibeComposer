import { AfterViewInit, Directive, ElementRef, OnDestroy, afterRenderEffect, inject, signal } from '@angular/core';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';

/** Hide a section's chord names together when any label would collide or be clipped. */
@Directive({ selector: '[vcScoreChordLabels]', host: {
  '[class.chords-hidden]': 'hidden()', '[class.measures-compact]': 'compactMeasures()'
} })
export class ScoreChordLabelsDirective implements AfterViewInit, OnDestroy {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly preferences = inject(UiPreferencesService);
  readonly hidden = signal(false);
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
      return !!range && (label?.offsetWidth ?? 0) + range.offsetWidth + 16 > slot.clientWidth;
    }));
    this.hidden.set([...slots].some(slot => {
      const label = slot.querySelector<HTMLElement>('.score-chord-label');
      const measure = slot.querySelector<HTMLElement>(this.compactMeasures() ? '.measure-end' : '.measure-range');
      return !!label && label.offsetWidth + 8 + (measure ? measure.offsetWidth + 8 : 0) > slot.clientWidth;
    }));
  }

  ngOnDestroy(): void {
    this.resize?.disconnect();
    this.mutations?.disconnect();
  }
}
