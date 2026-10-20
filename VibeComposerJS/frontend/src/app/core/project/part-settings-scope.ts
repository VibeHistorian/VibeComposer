import type { ArrangedPart } from '../music/part-role';

export type PartSettingsScope =
  | { readonly kind: 'global-role'; readonly role: ArrangedPart }
  | { readonly kind: 'global-track'; readonly trackId: string }
  | { readonly kind: 'section-role'; readonly sectionId: string; readonly role: ArrangedPart }
  | { readonly kind: 'section-track'; readonly sectionId: string; readonly trackId: string };

export type LocalPartSettingsScope = Extract<PartSettingsScope, { readonly sectionId: string }>;

export type PartWorkflowResult = 'changed' | 'unchanged' | 'invalid';
