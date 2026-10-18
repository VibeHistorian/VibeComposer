# VibeComposerJS Translation Rules

These rules define translation constraints and implementation conventions. Workspace behavior lives in [DAW_REDESIGN_PLAN.md](DAW_REDESIGN_PLAN.md); remaining migration work lives in [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md).

0. **The Angular translation project is in a buildout stage.** Breaking changes and replacing existing TypeScript, HTML, or CSS are acceptable when needed to meet the project goals.

   Target desktop and landscape layouts at viewport widths of at least 700 CSS pixels. Portrait mobile compatibility is out of scope; do not add requirements or browser viewport tests below 700px. Keep responsive behavior and input coverage within the supported range.

1. **Do not port the older legacy melody algorithm (`LegacyMelodySkeletonGenerator`) or its exclusive controls.** Only the newer block-based melody generation is in scope.

2. **Match Java's seeded random behavior.** Use the project's `java-random.ts`.

   Pass seeds as `bigint` whenever they may exceed JavaScript's safe integer range; converting an already-rounded `number` cannot preserve a Java `long` seed.

   Keep random streams and seed derivation explicit. Reproducibility depends on matching both the Java algorithm and the order in which each generator consumes random values.

3. **Port generation behavior, not the legacy control layout.** Follow [DAW_REDESIGN_PLAN.md](DAW_REDESIGN_PLAN.md) for workspace layout and interaction, [CONTEXTUAL_CONTROLS_PLAN.md](CONTEXTUAL_CONTROLS_PLAN.md) for settings scopes, role exceptions, and reroll policy, and [CONTROL_PLACEMENT_MAP.md](CONTROL_PLACEMENT_MAP.md) for control placement and Java lookups.

4. **Use historical project commands; commit each gesture once.**

5. **Use Web Audio and Web MIDI for audio and MIDI work.** Keep audio and MIDI processing asynchronous and non-blocking for the UI. Do not perform time-sensitive playback work in Angular rendering or change-detection paths.

6. **Keep generation settings serializable for JSON export and restoration.**

7. **Keep generation logic in framework-independent TypeScript.** Angular components and services should call this library; the library itself should not import Angular, access the DOM, or depend on Wails. Keep the core usable independently of the UI and desktop host.

8. **Keep browser operation independent of Wails.**

9. **Follow the Angular conventions.** Use signals, `@for`/`@if`/`@else`, zoneless change detection, and OnPush components. Keep component templates and styles in separate `.html` and `.css` files referenced by `templateUrl` and `styleUrl`. Omit the default `standalone: true`.

10. **Separate logical CSS sections with single-line comments.** Use a concise `/* Section name */` comment on its own line between groups of related rules in every CSS file.

11. **Use the project's style theme throughout the Angular UI.** Reuse semantic CSS custom properties in `frontend/src/styles.css` for colors, typography, surfaces, borders, radii, and other shared visual values. Add reusable theme tokens for missing shared values instead of one-off hard-coded styles.

12. **Treat `schemaVersion` as informational during translation buildout.** Validate imported project structure and values, but do not gate loading on a schema version or add compatibility migrations. Backward compatibility can be considered during the finishing phase.
