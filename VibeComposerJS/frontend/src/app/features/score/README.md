# Score ownership

`score-canvas.component.ts` owns Angular inputs/outputs, pointer capture and cancellation, viewport state, scrollbars, keyboard focus, and the Pixi lifecycle and drawing. Keep initialization, resize, listeners, rendering and destruction together.

- `score-geometry.ts`: plain viewport dimensions, pitch-row limits, coordinate conversions, offset clamping and section fitting. Canvas positions use CSS pixels; horizontal offsets use fractions of the full song. Client bounds may differ from canvas dimensions.
- `score-hit-testing.ts`: note hit rectangles, bucket indexing, harmonic/drum and velocity-handle targeting, overlap priority, edge zones and box selection. Callers supply geometry, areas, buckets and selection explicitly. Painted bounds remain separate from enlarged hit targets.
- `score-interaction.ts`: drag state, pure musical preview calculation and release-time change detection. `ScoreInteractionPreview` is the drawing contract for drag/paint notes and selection boxes. Helpers have no Angular, Pixi or DOM dependencies.
- `score-chord-labels.directive.ts`: chord labels aligned with the emitted viewport.

Pointer movement updates previews and auditions without emitting project edits. Release emits at most one note-change output; cancellation or a second touch discards previews. The enclosing [phrase editor](../edit/edit-workspace.component.ts) owns draft history and Apply/Local Apply/Cancel. Opening and closing it preserves the overview viewport; resizing preserves the top pitch.

Run `pnpm run test:score`, `pnpm run test:selection` and `pnpm run test:parts:phrases` from `frontend/`. After application changes, build and run `test:score:browser`, `test:phrase-import:browser` and `test:manual:browser`. See the [test guide](../../../../scripts/README.md) and [workspace contracts](../../../../../DAW_REDESIGN_PLAN.md).

Default phrase gestures: plain left click on empty grid seeks; Ctrl left click places a snapped note; Shift left click places a note without time snapping. Holding either placement modifier previews the same pitch, position and remembered duration without editing. Ctrl+Shift left drag duplicates selected notes or draws pitches elsewhere. Plain dragging on existing notes still moves or trims them.

Seeking in empty score space changes only playback position. It preserves the selected independent track, inspector scope and section selection in both overview and phrase editing. Overview note gestures default to left-click for manual phrase editing and middle-click for global or section track parameters, including for manual notes. Both bindings are configurable in Settings → Shortcuts → Score.

Right-clicking empty overview score space opens the shared five-role context menu, with Section and Global choices under each role. The canvas captures viewport-adjusted beat and pitch; the workspace resolves the section and `ProjectService.addTrack` rerolls the new track's settings and instrument using the role policies, then commits the track, octave transpose, presence and reroll counter in one history entry. Section enables presence only in the clicked section; Global enables every section. Transpose targets the generated phrase's median pitch octave in the clicked section within the supported ±36 range, respecting section settings and project transpose. Drums retain mapped instrument pitches. This menu is unavailable on notes and in the phrase editor.

Right-clicking an overview note opens Remove → From Section / From All Sections / Track. Presence removal keeps the track and its notes/settings; deleting Track removes the independent track and its section references. The note's stored section ID determines ownership across pickup/tail boundaries. Each command commits once and supports undo. Track deletion is disabled for the final track. The phrase editor retains its existing right-click gestures.
