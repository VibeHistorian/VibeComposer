# Live recording

Choose a device with Settings → MIDI → Find MIDI inputs. MIDI access is requested only on discovery, without SysEx. Device selection is session-only; keyboard note input and the recording tick are saved UI preferences. Unsupported browsers and permission errors leave keyboard recording available.

Record live in the phrase inspector, duration popup or pattern popup starts a project-BPM loop. The phrase loop uses the phrase length; rhythm loops use four beats. In Settings → Shortcuts → General, Tap recording rhythm assigns the tap/hold key (backtick by default). Phrase recording uses physical white-key positions in row order: 1–0, Q–P, A–L, Z–M, beginning at C2. Shift raises the pitch one semitone. Text fields and shortcut capture retain native keyboard input.

Onsets snap down to the current time cell; release snaps to the closest boundary, with at least one cell. Held notes split at loop boundaries. Patterns add hits while held. Durations replace the held interval using the selected note/pause tool, preserving material outside it. Phrase input adds chromatic notes regardless of drawing snap-to-scale. MIDI velocity is retained; keyboard velocity is 90.

Stop commits a pattern recording as one project gesture. Duration recording stays in the popup draft until Validate and Save; phrases stay in their draft until Apply or Local Apply. Blur, page exit, port changes and disconnects release held inputs. Starting another recorder stops the previous one. Metronome scheduling uses Web Audio lookahead outside Angular rendering; its first beat is stronger than the other three beats.

Checks: `node --test scripts/live-recording.test.cjs` and, after `pnpm run build`, `node --test scripts/live-recording-browser.test.cjs`. Both are included in their normal aggregates. The browser suite simulates Web MIDI; physical device testing remains manual.
