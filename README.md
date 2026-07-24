# 2Reply

2Reply is an Android rehearsal partner for theatrical plays — the app that plays every part
but yours. Feed it a fully-voiced `.m4b` recording of a play plus its timed `play.json`
script; pick the character (or characters) you're rehearsing, and their lines go **silent for
you to speak** while the rest of the cast answers, right on cue.

It is the sister app of [ln-reader](https://ln.vibetuned.com) and shares its entire
foundation — same stack, same versions, same architecture patterns (the shared house style is
documented in [NEW_APP_SPEC.md](NEW_APP_SPEC.md)). Where ln-reader consumes multi-voice
audiobooks from the LN pipeline, 2Reply consumes fully-voiced *plays* with per-line timing.

## 2Reply in a nutshell

An Android app to train role-playing for theatrical plays against a recording. Not on the
Play Store yet — build from source (below).

## Features

### Home / library
- Import a play from any SAF source with a **two-step picker**: the `.m4b` recording, then the
  `play.json` script. Both are **copied into private storage**; the script is validated before
  the large audio copy, and a duration cross-check rejects mismatched pairs.
- Each play shows its cover (from the m4b), duration, listening progress, and the roles you're
  rehearsing. Delete with confirmation removes the files too.
- On launch the app reopens your last play — paused, where you left it.

### Training screen
- The script renders as a **chat conversation**: other characters as left bubbles (speaker
  name, line, acting direction), your characters as right-aligned "me" bubbles, staging and
  cues as centered italic notes, scene titles as headers.
- The **active line highlights and auto-scrolls** with the audio (parked about a third from
  the top). Grabbing the list pauses following; a jump-to-current button snaps back. Tap any
  bubble to seek the audio to that line.
- A **"Your line — speak now"** banner shows while the playhead is inside one of your lines.
- **Per-line progress gauges** — every dialogue bubble carries a thin bar plus the line's
  spoken duration: full for played lines, filling live on the current one, empty for what's
  coming. During your muted lines it doubles as a pacing guide.

### Rehearsal mute
- Playback volume drops to zero **exactly during your characters' line windows** and restores
  after — the play stays on schedule while you deliver the line out loud.
- Pick **one role or several** (checkbox picker, sorted by line count) — rehearse two parts,
  or practice as a duo on one device. Back-to-back lines by rehearsed characters merge into
  one continuous muted window (no blip between them).
- Muting keeps working from the **media notification with the app backgrounded**, and reacts
  instantly to seeks — jumping into the middle of your own line mutes before the first
  syllable plays.

### Text-hiding drills
Two independent, persisted toggles in the training top bar. Hidden bubbles keep the speaker's
name and a **duration-scaled gauge** (voice-message style), so the conversation still reads:

| Your lines | Others' lines | You're practicing |
|---|---|---|
| shown | shown | **Reading along** — the default; speak on cue over your muted lines |
| shown | hidden | **Ear training** — no reading ahead, catch your cue from the audio |
| hidden | shown | **Memorization** — recall the line with only its name tag and timing |
| hidden | hidden | **Off-book run** — a pure audio-and-timing pass |

### Player
- Foreground media session — background playback, lock-screen controls.
- **Mini-player on every screen** (it *is* the training screen's transport): play/pause and
  **previous / next scene**. The notification carries the same scene buttons — a scene is the
  meaningful jump unit when rehearsing, not an arbitrary ±30 s. "Previous" restarts the
  current scene when you're more than 3 s in (standard chapter convention).
- Position auto-saves every 5 s while playing and on each pause; roles and drill settings are
  remembered per play / across launches.
- The **screen stays awake while playing** — hands-free through a whole scene; pausing lets it
  time out normally.

## The play format

A play is a pair of files (see `test_data/` for a reference):

- **`.m4b`** — a fully-voiced recording, every character spoken, one chapter per scene. Title
  and embedded cover art become the library entry.
- **`play.json`** — the timed script: `scenes[]`, each with `entries[]` carrying `start`/`end`
  (seconds), `type` (`dialogue` | `staging` | `cue`), `speaker`, `text`, an acting
  `direction`, and an `emotion` tag.

The script's timings are the source of truth for muting, highlighting, and scene skips — they
must match the recording. Import rejects pairs whose total durations differ by more than 30 s.

## Requirements

- Android 13 or newer (minSdk 33, targetSdk 36).
- A file manager / cloud app that exposes a `DocumentsProvider` for your `.m4b` and
  `play.json` files.

## Constraints / known limits

- **Speaker matching is exact.** "Caissière 1 (Roselyne)" and "Roselyne" count as different
  characters — select each variant you want muted.
- **Muting is player gain** (never device volume), engaged with ~0.1 s worst-case edge error —
  in practice invisible, because edges land inside the recording's inter-line pauses.
- **Re-importing the same play creates a duplicate** (UUID-keyed library, no content hashing).
- **Both files are copied** at import; a 90-minute play costs roughly the m4b's size in app
  storage. No streaming, no network — the app has no internet permission at all.
- **The script is required.** If `play.json` can't be read, the play is listen-only: no chat,
  no muting, no scene skips.
- Timings are used verbatim; a mismatched pair is rejected at import, but drift within the
  30 s guard isn't corrected.
- Unknown entry types render as staging notes; zero-length staging/cue entries flash by
  without breaking the flow.
- **Single-device only.** Uninstalling drops your library, positions, and chosen roles.

## Building

JDK 21 required. From the project root:

```sh
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. Install with `adb install`.

Architecture, design decisions, and tooling quirks live in [DESIGN.md](DESIGN.md).
Per-release notes live in [CHANGELOG.md](CHANGELOG.md).
The shared house style both apps follow lives in [NEW_APP_SPEC.md](NEW_APP_SPEC.md).
