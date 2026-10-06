# VibeComposerJS Translation Rules

These rules guide the translation of VibeComposer's generation functionality into the TypeScript and Angular application. They are a starting point and can be refined as the rewrite progresses.

0. **The Angular translation project is in a buildout stage** - it's OK for there to be breaking changes, and it's OK to remove or replace existing TS/html/css if it doesn't align with the overall goals.

1. **Do not port legacy melody generation or its controls.** Treat the legacy melody generator and its associated settings as out of scope for the rewrite.

2. **Match Java's seeded random behavior.** → Use the project's `java-random.ts`.

   Pass seeds as `bigint` whenever they may exceed JavaScript's safe integer range; converting an already-rounded `number` cannot preserve a Java `long` seed.

   Keep random streams and seed derivation explicit. Reproducibility depends on matching both the Java algorithm and the order in which each generator consumes random values.

3. **Port generation behavior, not the current control layout.** Preserve the musical generation functionality that remains in scope, while redesigning how users reach its settings. Use the [UI design moodboard](frontend/UI_design_moodboard.png) as visual inspiration; it is not a requirement to reproduce the Java UI.

4. **Choose state boundaries and update flows that can support history a undo/redo model.** E.g. components mutating state through a service which records the interaction and produces a new historical state. Long interactions, such as mouse events, should only produce a new state once (e.g. on release) rather than on every occurrence.

5. **Use Web Audio and Web MIDI for audio and MIDI work.** Keep audio and MIDI processing asynchronous and non-blocking for the UI. Do not perform time-sensitive playback work in Angular rendering or change-detection paths.

6. **Persist generation settings even if full UI-state persistence remains undecided.** Make generation-relevant settings serializable as JSON so users can export and restore them. Session storage is also an acceptable initial persistence mechanism.

7. **Keep generation logic in a framework-independent TypeScript pseudo-library.** Angular components and services should call this library; the library itself should not import Angular, access the DOM, or depend on Wails. Keep the core usable independently of the UI and desktop host.

8. **Keep Wails thin at first.** Initially use Wails to package and run the Angular web app as a desktop application. Defer desktop-specific features and native Go APIs until later phases, keeping the browser app usable without the Wails bridge wherever practical.

9. **Use modern angular signals, @for/@if/@else syntax, and zoneless/OnPush strategies for optimal performance.**

10. **Keep Angular templates and styles in separate files.** Store component HTML in `.html` files and component CSS in `.css` files, referenced with `templateUrl` and `styleUrl` in the component metadata. Omit `standalone: true`; standalone is the default in Angular 19 and later.

11. **Separate logical CSS sections with single-line comments.** Use a concise `/* Section name */` comment on its own line between groups of related rules in every CSS file.

12. **Use the project's style theme throughout the Angular UI.** Reuse the semantic CSS custom properties in `frontend/src/styles.css` for colors, typography, surfaces, borders, radii, and other shared visual values. The theme is being extracted from existing components; as component styles are updated or added, use those tokens and add reusable theme tokens for missing shared values instead of introducing one-off hard-coded styles.

13. **Treat `schemaVersion` as informational during translation buildout.** Validate imported project structure and values, but do not gate loading on a schema version or add compatibility migrations. Backward compatibility can be considered during the finishing phase.
