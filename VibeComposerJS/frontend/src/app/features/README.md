# Feature areas

Keep each user-facing part of the rewrite in its own feature area. Natural starting points from the Java application include composition, melody, bass, chords, drums, arrangement, instruments, and playback. Add an area when its first Angular screen or workflow is ready, then route to it from the app shell.

Feature components should own their screen behavior and use `core/` for app-wide services. Keep shared presentation pieces in `shared/`; avoid moving domain logic across areas until there is a concrete reuse need.
