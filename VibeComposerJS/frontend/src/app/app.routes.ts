import { Routes } from '@angular/router';
import { CreateWorkspaceComponent } from './features/create/create-workspace.component';
import { WorkspacePlaceholderComponent } from './features/workspace-placeholder.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'create' },
  { path: 'create', component: CreateWorkspaceComponent },
  {
    path: 'arrange',
    component: WorkspacePlaceholderComponent,
    data: { title: 'Arrange', description: 'Shape the song timeline and section changes here.' },
  },
  {
    path: 'edit',
    component: WorkspacePlaceholderComponent,
    data: { title: 'Edit', description: 'The piano roll and drum grid are planned for this workspace.' },
  },
  {
    path: 'mix',
    component: WorkspacePlaceholderComponent,
    data: { title: 'Mix', description: 'Instrument and playback controls will live in this workspace.' },
  },
  { path: '**', redirectTo: 'create' },
];
