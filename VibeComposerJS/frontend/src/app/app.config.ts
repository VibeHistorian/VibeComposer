import { ApplicationConfig, inject, provideAppInitializer, provideZonelessChangeDetection } from '@angular/core';
import { HostedContentService } from './core/ui/hosted-content.service';
import { WorkspaceStorage } from './core/project/workspace-storage';

export const appConfig: ApplicationConfig = {
  providers: [provideZonelessChangeDetection(), provideAppInitializer(async () => {
    const workspace = inject(WorkspaceStorage);
    const hostedContent = inject(HostedContentService);
    await workspace.initialize();
    await hostedContent.initialize();
  })],
};
