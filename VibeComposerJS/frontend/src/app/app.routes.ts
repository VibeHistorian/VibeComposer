import { Routes } from '@angular/router';
import { ArrangeWorkspaceComponent } from './features/arrange/arrange-workspace.component';
import { CreateWorkspaceComponent } from './features/create/create-workspace.component';
import { EditWorkspaceComponent } from './features/edit/edit-workspace.component';
import { MixWorkspaceComponent } from './features/mix/mix-workspace.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'create' },
  { path: 'create', component: CreateWorkspaceComponent },
  { path: 'arrange', component: ArrangeWorkspaceComponent },
  { path: 'edit', component: EditWorkspaceComponent },
  { path: 'mix', component: MixWorkspaceComponent },
  { path: '**', redirectTo: 'create' },
];
