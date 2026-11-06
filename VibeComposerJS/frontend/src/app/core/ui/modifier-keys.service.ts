import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { ModifierObserver } from './modifier-observer';

@Injectable({ providedIn: 'root' })
export class ModifierKeysService extends ModifierObserver {
  constructor() { super(inject(DOCUMENT)); }
  ngOnDestroy(): void { this.destroy(); }
}
