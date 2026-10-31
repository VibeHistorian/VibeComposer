import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ProjectService } from '../../core/project/project.service';
import { initialVariationDraft, rerollVariationDraft, resetVariationDraft, variationScopeSections, variationScopeTracks,
  projectWithVariationPresence, rerollVariationPresence, type VariationPresenceDraft, type VariationScope } from '../../core/project/arrangement-variation-draft';
import { inspectArrangementVariation } from '../../core/music/arrangement-variation-inspection';
import { DEFAULT_VARIATION_GENERATION, validVariationPolicy } from '../../core/music/arrangement-variations';
import { SectionTypeSettingsService } from '../../core/project/section-type-settings.service';
import { resolveArrangementIntent } from '../../core/music/arrangement-intent';
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
  readonly initialProject = this.projects.project();
  readonly wholeArrangement = !this.scope.sectionId && !this.scope.role && !this.scope.trackId;
  readonly initialGeneration = this.initialProject.arrangementVariationGeneration ?? DEFAULT_VARIATION_GENERATION;
  readonly generationEnabled = signal(this.initialGeneration.enabled);
  readonly rerollPresence = signal(false);
  readonly presenceDraft = signal<VariationPresenceDraft | undefined>(undefined);
  readonly candidateProject = computed(() => projectWithVariationPresence(this.initialProject, this.presenceDraft()));
  readonly tracks = variationScopeTracks(this.initialProject, this.scope);
  readonly sections = variationScopeSections(this.initialProject, this.scope);
  readonly draft = signal(initialVariationDraft(this.initialProject, this.scope));
  readonly policy = signal<VariationPolicy>({ ...(this.wholeArrangement ? this.initialGeneration.policy
    : this.sections[0]?.variations?.policy ?? this.initialGeneration.policy) });
  readonly policyValid = computed(() => validVariationPolicy(this.policy()));
  readonly stale = computed(() => this.projects.project() !== this.initialProject);
  readonly error = signal('');
  readonly title = this.scope.sectionId ? this.sections[0]?.type.replaceAll('_', ' ') ?? 'Section removed' : 'Whole arrangement';
  readonly targetLabel = this.scope.trackId ? this.tracks[0]?.name : this.scope.role ? this.scope.role : 'All tracks';
  readonly rows = computed(() => this.sections.map(section => {
    const current = { ...section, trackPresence: this.presenceDraft()?.[section.id] ?? section.trackPresence, variations: this.draft()[section.id] };
    const intent = resolveArrangementIntent(this.initialProject.arrangement, this.initialProject.arrangement.indexOf(section));
    const tracks = new Set(this.tracks.map(track => track.id));
    const choices = (current.variations?.choices ?? []).filter(choice => tracks.has(choice.trackId)).map(choice => {
      const track = this.initialProject.tracks.find(track => track.id === choice.trackId)!;
      const inspection = inspectArrangementVariation(track, current, choice);
      const expectedAmount = choice.kind === 'bass-weight' || choice.kind === 'chord-space'
        ? intent.energy >= .65 ? 1 : -1 : intent.motion === 'release' ? -1 : intent.tension >= .7 || intent.energy >= .65 ? 1 : -1;
      return { track, inspection, mismatch: choice.amount !== expectedAmount };
    });
    const presenceChanges = this.tracks.filter(track => current.trackPresence[track.id] !== section.trackPresence[track.id])
      .map(track => ({ track, present: current.trackPresence[track.id] }));
    return { section, intent, choices, presenceChanges };
  }));

  setPolicy(key: keyof VariationPolicy, event: Event): void {
    this.policy.update(policy => ({ ...policy, [key]: Number((event.target as HTMLInputElement).value) }));
  }
  reroll(): void {
    if (this.stale() || !this.policyValid()) return;
    const seed = BigInt(Math.floor(Math.random() * 0x1000000000000));
    this.presenceDraft.set(this.wholeArrangement && this.rerollPresence()
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
    const generation = { enabled: this.generationEnabled(), policy: this.policy() };
    const generationChanged = this.wholeArrangement && (generation.enabled !== this.initialGeneration.enabled
      || generation.policy.chance !== this.initialGeneration.policy.chance || generation.policy.maxChanges !== this.initialGeneration.policy.maxChanges);
    const result = this.projects.applyArrangementVariationDraft(this.initialProject, this.draft(), {
      ...(this.presenceDraft() ? { presence: this.presenceDraft() } : {}),
      ...(generationChanged ? { generation } : {}),
    });
    if (result === 'invalid') this.error.set('These variations could not be applied. Close and reopen to refresh.');
    else this.dialog.close();
  }
  value(value: unknown): string {
    return typeof value === 'boolean' ? value ? 'On' : 'Off' : typeof value === 'number'
      ? String(Math.round(value * 100) / 100) : String(value ?? 'Inherited');
  }
}
