import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';
import { initialVariationDraft, rerollVariationDraft, resetVariationDraft, variationScopeSections, variationScopeTracks,
  projectWithVariationPresence, rerollVariationPresence, type VariationPresenceDraft, type VariationScope } from '../../core/project/arrangement-variation-draft';
import { inspectArrangementVariation, type VariationInspection } from '../../core/music/arrangement-variation-inspection';
import { DEFAULT_VARIATION_POLICY, maximumSectionVariations, validVariationPolicy } from '../../core/music/arrangement-variations';
import { UiPreferencesService } from '../../core/ui/ui-preferences.service';
import { SectionTypeSettingsService } from '../../core/project/section-type-settings.service';
import { resolveVariationContext, variationAmountForContext, isRegisterVariation, VARIATION_GESTURE_LABELS } from '../../core/music/arrangement-variation-context';
import type { VariationPolicy } from '../../core/music/arrangement-variation.model';

@Component({
  selector: 'vc-arrangement-variations-dialog', changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './arrangement-variations-dialog.component.html', styleUrl: './arrangement-variations-dialog.component.css',
})
export class ArrangementVariationsDialogComponent {
  readonly scope = inject<VariationScope>(DIALOG_DATA);
  readonly dialog = inject(DialogRef);
  readonly projects = inject(ProjectService);
  readonly sectionTypes = inject(SectionTypeSettingsService);
  readonly preferences = inject(UiPreferencesService);
  readonly initialProject = this.projects.project();
  readonly wholeArrangement = !this.scope.sectionId && !this.scope.role && !this.scope.trackId;
  readonly initialPolicy = this.initialProject.arrangementVariationPolicy ?? DEFAULT_VARIATION_POLICY;
  readonly presenceDraft = signal<VariationPresenceDraft | undefined>(undefined);
  readonly candidateProject = computed(() => projectWithVariationPresence(this.initialProject, this.presenceDraft()));
  readonly tracks = variationScopeTracks(this.initialProject, this.scope);
  readonly sections = variationScopeSections(this.initialProject, this.scope);
  readonly draft = signal(initialVariationDraft(this.initialProject, this.scope));
  readonly policy = signal<VariationPolicy>({ ...(this.wholeArrangement ? this.initialPolicy
    : this.sections[0]?.variations?.policy ?? this.initialPolicy) });
  readonly maximumChanges = maximumSectionVariations(this.initialProject.tracks.length);
  readonly policyValid = computed(() => validVariationPolicy(this.policy(), this.initialProject.tracks.length));
  readonly stale = computed(() => this.projects.project() !== this.initialProject);
  readonly error = signal('');
  readonly title = this.scope.sectionId ? this.sections[0]?.type.replaceAll('_', ' ') ?? 'Section removed' : 'Whole arrangement';
  readonly targetLabel = this.scope.trackId ? this.tracks[0]?.name : this.scope.role ? this.scope.role : 'All tracks';
  readonly rows = computed(() => this.sections.map(section => {
    const current = { ...section, trackPresence: this.presenceDraft()?.[section.id] ?? section.trackPresence, variations: this.draft()[section.id] };
    const context = resolveVariationContext(this.initialProject.tracks, this.candidateProject().arrangement, this.initialProject.arrangement.indexOf(section));
    const tracks = new Set(this.tracks.map(track => track.id));
    const choices = (current.variations?.choices ?? []).filter(choice => tracks.has(choice.trackId)).map(choice => {
      const track = this.initialProject.tracks.find(track => track.id === choice.trackId)!;
      const inspection = inspectArrangementVariation(track, current, choice, this.initialProject.melodyGlobal);
      const explanation = choice.enabled ? inspection
        : inspectArrangementVariation(track, current, { ...choice, enabled: true }, this.initialProject.melodyGlobal);
      const effects = this.effectSummaries(explanation);
      let detail = explanation.detail;
      if (explanation.status === 'active') {
        if (!effects.length) { effects.push(detail); detail = ''; }
        else detail = detail === 'Active' ? '' : 'Manual edits are preserved';
      }
      const expectedAmount = variationAmountForContext(context, choice.kind);
      return { track, inspection, effects, detail, mismatch: choice.amount !== expectedAmount };
    });
    const presenceChanges = this.tracks.filter(track => current.trackPresence[track.id] !== section.trackPresence[track.id])
      .map(track => ({ track, present: current.trackPresence[track.id] }));
    return { section, intent: context.intent, context, gestureLabel: VARIATION_GESTURE_LABELS[context.gesture], choices, presenceChanges,
      returnLift: choices.some(item => isRegisterVariation(item.inspection.choice.kind) && item.inspection.choice.amount > 0 && item.inspection.status === 'active'),
      savedGestureLabel: current.variations?.gesture ? VARIATION_GESTURE_LABELS[current.variations.gesture] : undefined,
      gestureMismatch: current.variations?.gesture !== undefined && current.variations.gesture !== context.gesture };
  }));

  setPolicy(key: keyof VariationPolicy, event: Event): void {
    this.policy.update(policy => ({ ...policy, [key]: Number((event.target as HTMLInputElement).value) }));
  }
  reroll(): void {
    if (this.stale() || !this.policyValid()) return;
    const seed = BigInt(Math.floor(Math.random() * 0x1000000000000));
    this.presenceDraft.set(this.wholeArrangement && this.preferences.rerollPresenceWithVariations()
      ? rerollVariationPresence(this.initialProject, this.sectionTypes.chances(), seed) : undefined);
    this.draft.set(rerollVariationDraft(this.candidateProject(), this.scope, this.draft(), seed, this.policy()));
  }
  reset(): void {
    if (!this.stale()) this.draft.set(resetVariationDraft(this.candidateProject(), this.scope, this.draft()));
  }
  toggle(sectionId: string, trackId: string, event: Event): void {
    if (this.stale()) return;
    const enabled = (event.target as HTMLInputElement).checked;
    this.draft.update(draft => {
      const value = draft[sectionId];
      return !value ? draft : { ...draft, [sectionId]: { ...value, choices: value.choices.map(choice => choice.trackId === trackId ? { ...choice, enabled } : choice) } };
    });
  }
  apply(): void {
    if (this.stale() || !this.policyValid()) return;
    const policy = this.policy();
    const policyChanged = this.wholeArrangement && (policy.chance !== this.initialPolicy.chance || policy.maxChanges !== this.initialPolicy.maxChanges);
    const result = this.projects.applyArrangementVariationDraft(this.initialProject, this.draft(), {
      ...(this.presenceDraft() ? { presence: this.presenceDraft() } : {}),
      ...(policyChanged ? { policy } : {}),
    });
    if (result === 'invalid') this.error.set('These variations could not be applied. Close and reopen to refresh.');
    else this.dialog.close();
  }
  value(value: unknown): string {
    if (Array.isArray(value)) return value.length ? value.join(', ') : 'None';
    return typeof value === 'boolean' ? value ? 'On' : 'Off' : typeof value === 'number'
      ? String(Math.round(value * 100) / 100) : String(value ?? 'Inherited');
  }
  private effectSummaries(inspection: VariationInspection): string[] {
    const low = inspection.changes.find(change => change.parameter === 'rangeMin');
    const high = inspection.changes.find(change => change.parameter === 'rangeMax');
    const range = low && high ? `Melody MIDI range: ${this.value(low.before)}–${this.value(high.before)} → ${this.value(low.effective)}–${this.value(high.effective)} · ${[...new Set([low.source, high.source])].join(' / ')}` : undefined;
    return [
      ...(range ? [range] : []),
      ...inspection.changes.filter(change => !range || change.parameter !== 'rangeMin' && change.parameter !== 'rangeMax')
        .map(change => `${change.label}: ${this.value(change.before)} → ${this.value(change.effective)} · ${change.source}`),
    ];
  }
}
