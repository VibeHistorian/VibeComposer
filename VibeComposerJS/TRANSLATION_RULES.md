# VibeComposerJS Translation Rules

These rules guide the translation of VibeComposer's generation functionality into the TypeScript and Angular application. They are a starting point and can be refined as the rewrite progresses.

1. **Do not port legacy melody generation or its controls.** Treat the legacy melody generator and its associated settings as out of scope for the rewrite.

2. **Match Java's seeded random behavior.** Use a shared `JavaRandom` helper rather than `Math.random()` in generation code. The helper should reproduce `java.util.Random`'s 48-bit state and the Java methods actually used by the generators. Start with this implementation and extend it as needed while preserving Java's semantics (including bounded `nextInt` behavior when used):

   ```typescript
   class JavaRandom {
     private seed: bigint;

     constructor(seed: bigint | number) {
       this.seed = (BigInt(seed) ^ 0x5DEECE66Dn) & ((1n << 48n) - 1n);
     }

     next(bits: number): number {
       this.seed =
         (this.seed * 0x5DEECE66Dn + 0xBn) & ((1n << 48n) - 1n);

       return Number(this.seed >> BigInt(48 - bits));
     }

     nextInt(): number {
       return this.next(32) | 0;
     }

     nextDouble(): number {
       const a = this.next(26);
       const b = this.next(27);
       return (a * 134217728 + b) / 9007199254740992;
     }
   }
   ```

   Pass seeds as `bigint` whenever they may exceed JavaScript's safe integer range; converting an already-rounded `number` cannot preserve a Java `long` seed.

   Keep random streams and seed derivation explicit. Reproducibility depends on matching both the Java algorithm and the order in which each generator consumes random values.

3. **Port generation behavior, not the current control layout.** Preserve the musical generation functionality that remains in scope, while redesigning how users reach its settings. Use the [UI design moodboard](frontend/UI_design_moodboard.png) as visual inspiration; it is not a requirement to reproduce the Java UI.

4. **Choose state boundaries and update flows that can support history a undo/redo model.** E.g. components mutating state through a service which records the interaction and produces a new historical state. Long interactions, such as mouse events, should only produce a new state once (e.g. on release) rather than on every occurrence.

5. **Use Web Audio and Web MIDI for audio and MIDI work.** Keep audio and MIDI processing asynchronous and non-blocking for the UI. Do not perform time-sensitive playback work in Angular rendering or change-detection paths.

6. **Persist generation settings even if full UI-state persistence remains undecided.** Make generation-relevant settings serializable as JSON so users can export and restore them. Session storage is also an acceptable initial persistence mechanism.

7. **Keep generation logic in a framework-independent TypeScript pseudo-library.** Angular components and services should call this library; the library itself should not import Angular, access the DOM, or depend on Wails. Keep the core usable independently of the UI and desktop host.

8. **Keep Wails thin at first.** Initially use Wails to package and run the Angular web app as a desktop application. Defer desktop-specific features and native Go APIs until later phases, keeping the browser app usable without the Wails bridge wherever practical.

9. **Use modern angular signals, @for/@if/@else syntax, and zoneless/OnPush strategies for optimal performance.**