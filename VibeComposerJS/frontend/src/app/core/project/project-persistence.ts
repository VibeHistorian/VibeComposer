interface PersistenceEnvironment {
  setTimeout(callback: () => void, delay: number): ReturnType<typeof setTimeout>;
  clearTimeout(timer: ReturnType<typeof setTimeout>): void;
  page: Pick<Window, 'addEventListener' | 'removeEventListener'>;
  document?: Pick<Document, 'addEventListener' | 'removeEventListener' | 'visibilityState'>;
}

/** Defer encoding as well as storage writes, without starving saves during sustained edits. */
export class ProjectPersistence {
  private quietTimer?: ReturnType<typeof setTimeout>;
  private deadlineTimer?: ReturnType<typeof setTimeout>;
  private pending = false;
  private destroyed = false;

  constructor(private readonly write: () => void, private readonly environment: PersistenceEnvironment = {
    setTimeout: (callback, delay) => setTimeout(callback, delay),
    clearTimeout: timer => clearTimeout(timer),
    page: globalThis,
    document: globalThis.document,
  }) {
    environment.page.addEventListener?.('pagehide', this.flush);
    environment.document?.addEventListener('visibilitychange', this.onVisibilityChange);
  }

  schedule(): void {
    if (this.destroyed) return;
    if (!this.pending) {
      this.pending = true;
      this.deadlineTimer = this.environment.setTimeout(this.flush, 1000);
    }
    if (this.quietTimer !== undefined) this.environment.clearTimeout(this.quietTimer);
    this.quietTimer = this.environment.setTimeout(this.flush, 150);
  }

  readonly flush = (): void => {
    if (!this.pending) return;
    this.pending = false;
    if (this.quietTimer !== undefined) this.environment.clearTimeout(this.quietTimer);
    if (this.deadlineTimer !== undefined) this.environment.clearTimeout(this.deadlineTimer);
    this.quietTimer = this.deadlineTimer = undefined;
    this.write();
  };

  private readonly onVisibilityChange = (): void => {
    if (this.environment.document?.visibilityState === 'hidden') this.flush();
  };

  destroy(): void {
    this.flush();
    this.destroyed = true;
    this.environment.page.removeEventListener?.('pagehide', this.flush);
    this.environment.document?.removeEventListener('visibilitychange', this.onVisibilityChange);
  }
}
