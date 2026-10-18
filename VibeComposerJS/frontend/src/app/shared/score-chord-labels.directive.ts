import { AfterViewInit, Directive, ElementRef, OnDestroy, afterRenderEffect, inject, signal } from '@angular/core';
import { UiPreferencesService } from '../core/ui/ui-preferences.service';

/** Hide a section's chord names together when any label would collide or be clipped. */
@Directive({ selector: '[vcScoreChordLabels]', host: { '[class.chords-hidden]': 'hidden()' } })
export class ScoreChordLabelsDirective implements AfterViewInit, OnDestroy {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly preferences = inject(UiPreferencesService);
  readonly hidden = signal(false);
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
    this.hidden.set([...slots].some(slot => {
      const label = slot.querySelector<HTMLElement>('.score-chord-label');
      const measure = slot.querySelector<HTMLElement>('.score-section-measure');
      return !!label && label.offsetWidth + 8 + (measure ? measure.offsetWidth + 8 : 0) > slot.clientWidth;
    }));
  }

  ngOnDestroy(): void {
    this.resize?.disconnect();
    this.mutations?.disconnect();
  }
}
