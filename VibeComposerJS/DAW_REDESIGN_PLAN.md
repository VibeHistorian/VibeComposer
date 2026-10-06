# VibeComposerJS DAW Workspace Redesign Plan

## Goal

Reshape the translated app from four form-oriented pages into one compact, DAW-like workspace. Keep the song visible while the user creates parts, arranges sections, edits notes, or balances tracks. Put frequently used actions near the track or canvas they affect, with a quick settings popover for small changes and a full inspector for detailed work.

The supplied split-parts moodboard is the main layout reference: grouped tracks at left, an arrangement overview and note editor in the center, a contextual inspector at right, and persistent transport controls at the bottom. Reuse the existing dark theme and role-color tokens rather than adopting the moodboard's blue palette wholesale. The current app screenshot is useful as a reference for the form-like workflow to consolidate; the VibeComposer2.6 screenshot shows the dense legacy score and breadth of controls to organize.

References: [split-parts UI moodboard](frontend/UI_design_moodboard_split_parts.png) and [translation rules](TRANSLATION_RULES.md). [Legacy java VibeComposer2.6 screenshot](frontend/LEGACY_java_vibecomposer26.png). [Current form-like temporary design](frontend/CURRENT_formlike_design.png).

## Current starting point

- The shell routes to separate Create, Arrange, Edit, and Mix pages. Create currently stacks chord, bass, chord, arpeggio, and drum forms/previews; other pages each own their own main canvas.
- The shared shell already owns project actions, workspace navigation, and the transport dock. The feature README already calls for a timeline, contextual inspector, dense piano roll/drum grid, and role colors.
- Project state currently stores one settings object per supported role (`bass`, `chords`, `arpeggio`, `drums`) and one mix object per role. Phrase edits are keyed by role, so two independent bass parts cannot yet keep separate settings, edits, instruments, or channels.
- The current rewrite scope excludes the legacy melody generator and its controls. Keep the track model extensible, but initially expose only generation types that are actually implemented in the translation.

## Target workspace

Use one persistent workspace frame instead of replacing the whole page when a workspace is chosen.

```text
┌ Project / workspace modes / undo-redo  ─────────────────────────────────────┐
│ Track groups │ Arrangement overview + section rows  │ Full inspector        │
│              ├──────────────────────────────────────┤                       │
│              │ Dense score for the whole song       │                       │
│              │ Piano roll / drum grid editing       │                       │
├ Persistent transport, loop, position, tempo and key ────────────────────────┤
└─────────────────────────────────────────────────────────────────────────────┘
```

### Persistent song canvas

- Keep the arrangement overview visible in every mode. It shows section blocks across a shared ruler, a playhead, and one row for every defined track. Selecting a block or row updates the shared selection and inspector.
- Keep a dense, color-coded all-notes score preview visible in every mode, below the arrangement overview. It spans the song and follows the same ruler and playhead. Selecting a track can emphasize its notes while retaining the other tracks as a quiet context layer. The Edit mode exposes direct piano-roll or drum-grid tools in this same canvas; other modes keep the score useful as a preview.
- Treat Create, Arrange, Edit, and Mix as modes that change the primary tools and contextual actions, not as separate pages that remove the shared song view. For example, Create adds generation actions and progression controls around the selected track; Arrange emphasizes section editing; Edit enables note tools; Mix emphasizes track level, pan, mute, and solo controls.
- Keep the bottom transport dock mounted independently of mode changes. Preserve playback, loop, position, tempo, key, and undo/redo access in the shell.

### Track browser

- Group tracks by implemented instrument/generator type (initially Bass, Chords, Arpeggio, and Drums). Each group can collapse and has an Add Track action that creates another independent track of that type.
- Show each track's name, role color, instrument, and MIDI channel compactly. Provide selection, visibility, mute, solo, duplicate, reorder, and remove actions without opening a separate page.
- Let a track's notes remain independent when the track is duplicated or regenerated. Do not treat a role group as the generated MIDI track; it is only an organizational group.
- Give each track a stable ID so arrangement rows, phrase edits, selection, undo history, and MIDI export all refer to the same entity.

### Quick controls and inspector

- Add a track quick-settings popover from its row and from the selected-track header. Use it for common changes such as name, generation style/rhythm, instrument, channel, mute/solo, and regenerate. Keep it small, dismissible, and anchored to the invoking track.
- Keep the inspector open as the full settings surface for the current selection. Organize its contents into clear sections or tabs: Generation, Variation, Instrument & MIDI, and Mix. Show track-level settings when a track is selected, section-level controls for a selected arrangement block, and note properties for a selected note.
- Avoid duplicating entire forms in the track list. Keep quick actions and a few high-value status values there; put the complete parameter set in the inspector.

### Dense score and velocity display

- Render the notes for all tracks against a common song-time grid, including section boundaries and a synchronized playhead. Keep role color stable between the track list, arrangement rows, and score.
- Encode note velocity as a visible change in color intensity (bounded so quiet notes remain findable). Keep note length and pitch readable at dense zoom levels; show exact velocity in the velocity lane and on selection/hover. The selected track and selected note should remain clear when tracks share similar pitches and times.
- Include a velocity lane aligned with the score. In Edit mode, selecting a note exposes precise velocity editing; the all-notes preview remains readable in Create, Arrange, and Mix modes.
- Use the existing drum grid for drum editing and the piano roll for pitched tracks. Switch the editor toolset based on selected track type without changing the persistent arrangement overview.

### Compact parameter controls

- Replace suitable numeric label/value/slider rows with compact rotary knobs in the inspector and quick-settings surfaces. Use short codes on the control face (for example `CMP` for complexity, `VAR` for variation, `LEN` for note length, and `SWG` for swing), with the complete parameter name and current value available on hover and keyboard focus.
- Keep categorical choices as selects/segmented controls and booleans as toggles. Knobs are for bounded numeric parameters with a meaningful scale, not a universal replacement for every field.
- Each knob needs a visible value, a tooltip with the full label and units, keyboard adjustment, focus styling, and an accessible name/description. Support fine adjustment for precision; do not make hover the only way to discover what a code means.
- Prefer arranging related knobs in compact groups in the inspector, with advanced or infrequently changed controls collapsed under named sections.

## Project and track state changes

Move from role-keyed generation and mix settings toward a track collection. A draft shape is:

```ts
interface Track {
  id: string;
  role: 'melody' | 'bass' | 'chords' | 'arpeggio' | 'drums';
  name: string;
  colorRole: string;
  generatorSettings: MelodySettings | BassSettings | ChordSettings | ArpeggioSettings | DrumSettings;
  instrumentProgram: number;
  midiChannel: number; // store zero-based or one-based consistently; display channels 1–16
  mix: { volumePercent: number; panPercent: number; muted: boolean; solo: boolean };
  editedPhrase?: readonly PhraseNote[];
}
```

The final model should use a discriminated union so a track role can only have its matching settings type. Keep arrangement sections and represent their per-track inclusion/chance settings by track ID. Retain section-level chord overrides and the project's key, tempo, seed, progression, and name.

Update generation APIs to accept a track ID and that track's settings. Generate, edit, mute, solo, instrument assignment, and export operations must act on one track when requested, even when multiple tracks share a role. Keep generation code framework-independent and random stream/seed derivation explicit per the translation rules.

Bump the serialized schema version and add a migration from the current role-based schema. Map each existing role into one initial track, copying its generator settings, mix values, and edited phrase; preserve project metadata, progression, arrangement, seed, and section settings. Make old project files importable during this transition, and persist the new per-track settings in exported JSON and session storage.

Store the General MIDI program and MIDI channel on each track. Display MIDI channels 1–16 consistently; use the conventional channel 10 for percussion by default. Validate channel and program ranges, and surface channel collisions so users can intentionally share a channel or choose another. Export each defined track independently with its instrument, channel, notes, and applicable mix/controller data.

Keep user interactions flowing through the project service/history boundary. A drag or knob gesture should produce one undo step when committed, rather than one history entry per pointer movement. Keep playback and MIDI processing asynchronous and outside Angular rendering work.

## Delivery sequence

1. **Track-capable project model:** Add track IDs and typed per-track generation, phrase, instrument, channel, and mix state; update generation and export interfaces; add schema migration from the current four-role project. Preserve a one-track-per-role default so the existing composition opens the same way.
2. **Persistent shell and canvas:** Replace route-owned page frames with a shared workspace layout. Keep the arrangement overview, dense all-notes score preview, mode navigation, project header, and transport mounted across modes. Add track groups and selection.
3. **Track-level actions and inspector:** Add new/duplicate/reorder/remove track actions, independent generation, quick-settings popover, and the full contextual inspector. Move current role settings into per-track inspector sections.
4. **Editing and compact controls:** Integrate the existing piano-roll and drum-grid interactions with selected track IDs, add the aligned velocity lane and velocity-based score intensity, and convert suitable numeric rows into accessible knobs.
5. **Polish and responsive behavior:** Collapse the track browser or inspector at smaller widths while keeping a clear path back to them. Provide a compact canvas layout, visible selected-track context, keyboard navigation, tooltips, empty states, and focus states. Retain the shared theme tokens and consistent role colors.

## Completion criteria

- Users can create two or more tracks of the same implemented role, each with independent generator settings, phrase edits, General MIDI instrument, MIDI channel, and mix state.
- The arrangement overview and dense all-track score preview remain available while switching among Create, Arrange, Edit, and Mix modes; the transport remains persistent too.
- Track quick settings can be changed without leaving the canvas, and the full set of relevant settings is available in the inspector.
- Score notes use consistent role colors, respond visibly to velocity, align with a velocity lane, and synchronize with the arrangement ruler and playhead.
- Compact knobs replace appropriate numeric slider rows while remaining understandable and operable without a mouse hover.
- Existing project data migrates into a one-track-per-role starting arrangement, and exported MIDI preserves separate tracks, instrument programs, and channels.
- Generation remains reproducible for the same project and track settings, and edits remain undoable as coherent user actions.
- The layout continues to apply the shared theme tokens and remains usable when either side panel is collapsed.

## Design guardrails

- Keep legacy melody generation and its controls out of scope, as required by the translation rules. Add future generator roles only when their implementation is in scope.
- Preserve quick access and dense editing, but make the song canvas the visual anchor. Avoid rebuilding the legacy Java control wall inside one large inspector.
- Keep the all-notes view optimized as a preview; use the selected track's piano roll or drum grid for precise editing rather than shrinking every editor into the score overview.
- Treat the moodboard as a direction, not a pixel-perfect specification. Use the current theme tokens, actual translated generator parameters, and clear typography to resolve visual details. -- Theme decisions can be changed later globally through the use of globally defined variables (styles.css).
