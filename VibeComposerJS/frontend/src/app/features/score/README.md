# Score ownership

`score-canvas.component.ts` owns Angular inputs/outputs, pointer capture and cancellation, viewport state, scrollbars, keyboard focus, and the Pixi lifecycle and drawing. Keep initialization, resize, listeners, rendering and destruction together.

- `score-geometry.ts`: plain viewport dimensions, pitch-row limits, coordinate conversions, offset clamping and section fitting. Canvas positions use CSS pixels; horizontal offsets use fractions of the full song. Client bounds may differ from canvas dimensions.
- `score-hit-testing.ts`: note hit rectangles, bucket indexing, harmonic/drum and velocity-handle targeting, overlap priority, edge zones and box selection. Callers supply geometry, areas, buckets and selection explicitly. Painted bounds remain separate from enlarged hit targets.
- `score-interaction.ts`: drag state, pure musical preview calculation and release-time change detection. `ScoreInteractionPreview` is the drawing contract for drag/paint notes and selection boxes. Helpers have no Angular, Pixi or DOM dependencies.
- `score-chord-labels.directive.ts`: chord labels aligned with the emitted viewport.

Pointer movement updates previews and auditions without emitting project edits. Release emits at most one note-change output; cancellation or a second touch discards previews. The enclosing [phrase editor](../edit/edit-workspace.component.ts) owns draft history and Apply/Local Apply/Cancel. Opening and closing it preserves the overview viewport; resizing preserves the top pitch.

Run `npm run test:score`, `npm run test:selection` and `npm run test:parts:phrases` from `frontend/`. After application changes, build and run `test:score:browser`, `test:phrase-import:browser` and `test:manual:browser`. See the [test guide](../../../../scripts/README.md) and [workspace contracts](../../../../../DAW_REDESIGN_PLAN.md).
