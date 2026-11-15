import { ApplicationConfig, inject, provideAppInitializer, provideZonelessChangeDetection } from '@angular/core';
import { HostedContentService } from './core/ui/hosted-content.service';

export const appConfig: ApplicationConfig = {
  providers: [provideZonelessChangeDetection(), provideAppInitializer(() => inject(HostedContentService).initialize())],
};
