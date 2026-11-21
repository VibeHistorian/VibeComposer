import { ApplicationConfig, inject, provideAppInitializer, provideZonelessChangeDetection } from '@angular/core';
import { HostedContentService } from './core/ui/hosted-content.service';
import { WorkspaceStorage } from './core/project/workspace-storage';
import { BrowserDatabase } from './core/storage/browser-database';
import { SettingsStorage } from './core/ui/settings-storage';

export const appConfig: ApplicationConfig = {
  providers: [provideZonelessChangeDetection(), provideAppInitializer(async () => {
    const workspace = inject(WorkspaceStorage);
    const database = inject(BrowserDatabase);
    const settings = inject(SettingsStorage);
    const hostedContent = inject(HostedContentService);
    await database.initialize();
    await Promise.all([workspace.initialize(), settings.initialize()]);
    await hostedContent.initialize();
  })],
};
