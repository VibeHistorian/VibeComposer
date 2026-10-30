# Internal instruments and soundfonts

InstrumentSettingsService owns session-loaded SF2 banks and the selected instrument ID. Load files from Settings > MIDI; the transport selector offers TinySynth plus the loaded filenames. Files are read and resolved in the browser, are limited to 250 MiB each, and are not persisted in project JSON or browser storage.

A successful load selects the new bank. At most three banks are retained in load order; loading a fourth removes the oldest. Failed loads preserve the selection and existing banks. Selecting a different instrument stops playback and disposes the old engine and decoded cache. Unloading the selected bank switches to TinySynth. Export captures a bank reference at its start so selection changes do not invalidate an in-progress export.

Sf2Bank resolves bank/program presets through instrument/sample zones, including key/velocity ranges, tuning, attenuation, pan, volume envelopes, loops and exclusive groups. Sf2InstrumentEngine shares decoded sample buffers within its context. Melodic programs use bank 0; drums currently use bank 128/program 0. A missing preset raises an explicit error.

The pinned smplr patch adds per-region pan/scale tuning and delay/attack/hold/decay/sustain envelopes, with release from the current envelope level. Supported SF2 behavior is still a subset: modulators, LFOs, filter resonance/envelopes, key-scaled envelope times, linked stereo pairs and ROM/24-bit samples are not implemented. Mode-3 loops continue during release. Time parameters are capped at 40 seconds to fit export's tail allowance.

Tests use the local Super_Nintendo_Unofficial_update.sf2 under VibeComposerJS/ as a fixture without copying it into application assets. sf2-resolver.test.cjs covers all GM presets and the Standard kit plus synthetic inheritance fixtures. tiny-synth-browser.test.cjs renders all 129 presets independently and verifies the patch using controlled PCM. soundfont-loading.test.cjs checks file limits, invalid loads, selection and eviction; export-browser.test.cjs exercises the real settings file input, transport selection, eviction/unload and audio export.
