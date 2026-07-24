# Changelog

## v1.0 — 2026-07-24

First release: rehearse a theatrical play against its fully-voiced recording — your character's
lines go silent so you speak them, everyone else plays aloud.

### Play Store release notes (≤ 500 chars)

```
• Import a play: its recording (.m4b) + timed script (play.json).
• Pick one or several characters to rehearse — their lines are muted so YOU speak them; everyone else plays aloud.
• The script scrolls as a chat conversation in sync with the audio; tap any line to jump there.
• Previous/next scene controls in the app and on the lock screen; keeps playing in the background.
• Your position and chosen roles are remembered per play.
```

### Library
- **Home list** — imported plays with cover art, duration, listening progress, and the last
  rehearsed character. Swipe-free explicit delete with confirmation.
- **Two-step SAF import** — pick the `.m4b`, then the `play.json`; both are copied into private
  storage. The script is validated before the large audio copy, and a duration cross-check
  catches mismatched pairs.

### Training screen
- **Chat-style script** — other characters as left bubbles (name + line + acting direction),
  your character as right-aligned "me" bubbles, staging/cues as centered italic notes, scene
  titles as headers.
- **Rehearsal mute** — playback volume drops to zero exactly during your characters' line
  windows (timing stays on schedule) and restores after; works from the notification with the
  app backgrounded. A "Your line — speak now" banner shows while muted.
- **Multi-character rehearsal** — select several roles at once (checkbox picker); all their
  lines mute and render as "me" bubbles. Useful for playing multiple parts or practicing as a
  duo on one device.
- **Synced highlight + auto-follow** — the active line is highlighted and kept in view;
  grabbing the list pauses following, a jump-to-current button resumes it. Tap any bubble to
  seek the audio there.
- **Per-line progress bars** — every dialogue bubble carries a voice-message-style gauge with
  the line's spoken duration: full for lines already played, filling in real time on the
  current line, empty for what's coming.
- **Text-hiding drills** — two top-bar toggles (persisted): hide YOUR lines' text to test
  memorization, or hide the OTHERS' to practice reacting to the spoken audio instead of
  reading ahead. Hidden bubbles keep the speaker name and the gauge, and their width scales
  with the line's duration like a voice message.
- **Character picker** — speakers sorted by line count; the choice is persisted per play.

### Player
- Foreground Media3 session with previous/next **scene** buttons (scene boundaries from the
  script) on both the mini-player and the notification — a scene is the meaningful jump unit
  when rehearsing, not an arbitrary time increment. "Previous" restarts the current scene when
  more than 3s in, standard chapter-skip convention.
- Position auto-save every 5s and on pause, resume-paused-where-you-left on app launch.
- The screen stays awake while audio plays (rehearsing means long stretches without touching
  the display); pausing lets it time out normally.
