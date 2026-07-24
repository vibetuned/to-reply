# Design

2Reply deliberately mirrors [ln-reader](https://github.com/vibetuned/ln-reader)'s
architecture — same stack, same versions, same patterns — so the two apps stay easy to
maintain together. The shared house style is codified in [NEW_APP_SPEC.md](NEW_APP_SPEC.md);
this document covers what 2Reply does with it and where it deliberately diverges.

## Stack

| Concern | Choice |
| --- | --- |
| Build | AGP 9.2.1, Gradle 9.4.1, JDK 21, Kotlin (AGP-bundled) |
| UI | Jetpack Compose + Material 3, single Activity, Compose Navigation |
| Playback | Media3 / ExoPlayer (`MediaSessionService`) |
| Persistence | Room (version 1), DataStore Preferences |
| Image loading | Coil 3 |
| Script parsing | `org.json` (platform built-in, no extra dependency) |
| DI | Manual — a single `AppContainer` exposes lazy singletons |
| Module layout | Single `:app` module, package-by-feature |

## Package layout

```
com.vibetuned.to_reply
├── ToReplyApplication             owns the AppContainer
├── MainActivity                   Compose content, POST_NOTIFICATIONS, cold-start restore,
│                                   keep-screen-on-while-playing
├── di/AppContainer                lazy singletons: DB, repos, parsers, player, mute engine, prefs
├── data/
│   ├── db/                        Room: PlayEntity, PositionEntity, DAOs, ToReplyDatabase
│   ├── model/                     Play (domain), PlayScript / PlayScene / PlayEntry / EntryType
│   ├── prefs/TrainingPreferences  DataStore: the two text-hiding drill toggles
│   ├── repo/                      PlayRepository (import / script cache), PositionRepository
│   └── script/PlayScriptParser    play.json → PlayScript (org.json, tolerant)
├── m4b/                           MP4 atom parser, lifted verbatim from ln-reader
│                                   (M4bSource, AtomReader, M4bParser)
├── player/                        PlaybackService, PlayerHolder, TrainingController (mute
│                                   engine), SceneSkip
└── ui/
    ├── common/                    appContainer() composable
    ├── home/                      HomeScreen + HomeViewModel (list, two-step import, delete)
    ├── navigation/                TopLevelDestination, ToReplyNavGraph, TrainingRoute
    ├── player/                    MiniPlayerBar (the app's only transport UI)
    ├── theme/                     Color / Theme / Type (Material 3 dynamic color)
    └── training/                  TrainingScreen + TrainingViewModel + SpeakerPickerSheet
                                    + TrainingUiState (the chat, drills, gauges)
```

## DI: AppContainer

Same rationale as ln-reader: Hilt still targets AGP 8's `BaseExtension`, which AGP 9 removed,
so DI is a hand-rolled container instantiated in `ToReplyApplication.onCreate()` and reached
from Compose via `appContainer()`. ViewModels are built with
`viewModel(factory = SomeViewModel.factory(deps…))`.

One process-scoped mutable flag lives here too: `lastPlayRestoreHandled`, which gates the
cold-start "reopen last play, paused" navigation to once per process (a `rememberSaveable`
would survive process death and wrongly skip the restore).

## Data layer

### Schema (Room v1)

```
plays(id PK, title, audioPath, scriptPath, coverPath, durationMs, importedAt,
      fileSize, selectedSpeaker)
positions(playId PK, positionMs, updatedAt)
```

Two tables only. Points worth knowing:

- **`selectedSpeaker` holds a list despite its singular name.** Multi-role rehearsal arrived
  after v1 shipped to a device; rather than migrate, the column now stores the chosen
  characters joined with the ASCII unit separator (U+001F — cannot occur in a speaker string
  coming out of JSON). An old single value contains no separator and decodes as a one-element
  list, so **no migration was needed**. The Kotlin property is `selectedSpeakers` with
  `@ColumnInfo(name = "selectedSpeaker")`; encode/decode helpers live in `repo/Mappers.kt`.
- It's updated via a targeted `@Query UPDATE` so a role change never races a full-row upsert.
- `positions.mostRecentExistingPlayId()` (an INNER JOIN against `plays`) powers the cold-start
  restore while skipping orphaned rows.

### Script entries are NOT in Room — by design

The script is a write-once artifact: copied at import, never edited, never partially queried.
Every consumer (chat UI, mute engine, speaker picker, scene skip) needs the *whole* script
*in order*, so there is no query Room answers that a file read doesn't. A ~200 KB / 600-entry
script parses in tens of milliseconds, once per open, then lives in a `MutableMap` cache
inside the process-scoped `PlayRepository` (evicted on delete). Keeping entries out of Room
keeps the script schema **soft** — new fields (direction, emotion, whatever comes next) never
need a DB migration. Precedent: ln-reader's sync manifest works the same way.

## PlayScript model + parser

`data/model/PlayScript.kt` is the domain heart:

- All times are converted from the JSON's `Double` seconds to `Long` **milliseconds once, at
  parse time** — everything downstream (Media3 positions, mute ranges, seeks) speaks the same
  integer unit.
- `flatEntries` — scenes flattened into one index space the whole training UI shares.
- `entryIndexAt(positionMs)` — binary search for the last entry whose start ≤ position (same
  semantics as ln-reader's `SyncManifest.beatAt`): during silences the previous line stays
  "active", which reads naturally in a chat.
- `speakerStats()` — dialogue speakers with counts, busiest first; feeds the picker. Only
  strings that literally appear in the script are offered, which is why exact-string speaker
  matching is safe.
- `muteRangesFor(speakers)` — the mute windows: dialogue entries by any selected speaker,
  positive-width only (the data contains zero-width staging/cues), mapped to **end-exclusive**
  ranges, sorted, and **merged when < 300 ms apart** so consecutive rehearsed lines don't
  produce a one-tick unmute blip — including exchanges *between* two rehearsed characters.

`data/script/PlayScriptParser.kt` uses `org.json` with `opt*` accessors throughout (tolerant
of missing fields), sorts scenes/entries defensively, and maps **unknown entry types to
STAGING** so a future script format degrades into harmless system notes instead of silently
shifting every index. A strict `validate()` (has scenes, has dialogue, has title, has
duration) runs at import only — regular opens trust the already-validated file.

## M4B parser

Lifted verbatim from ln-reader (`m4b/`, package rename only): duration from `moov/mvhd`,
title/author/album from `udta/ilst`, cover art from `covr` data atoms. Chapter parsing is
still present but unused — 2Reply's scene boundaries come from the script, which is the
timing source of truth. The parser reads only metadata byte ranges, never the audio.

## Import flow

```
FAB → SAF OpenDocument (audio MIME) → stash URI on the VM
    → SAF OpenDocument (json MIME)  → PlayRepository.import(audioUri, scriptUri, onProgress)
  1. playId = UUID; mkdir filesDir/plays/<id>/ ; cleanup += deleteRecursively
  2. copy play.json  → parse + validate            // fail in ms, before the 64 MB copy
  3. copy audio.m4b  with byte-count progress
  4. M4bParser.parse(local copy) → durationMs, title, cover.jpg
  5. |m4b duration − script total_duration| > 30 s → fail "wrong pair"
  6. insert PlayEntity; cleanup.clear(); prime the script cache
  finally: run cleanup                              // failure leaves no trace on disk
```

Two sequential single-file pickers beat `OpenMultipleDocuments`: per-step MIME filtering,
deterministic roles (no guessing which returned URI is which), and trivial cancel semantics.
Unlike ln-reader there is **no reference-in-place, no persistable URI permission, and no
remote/local heuristic** — both files are always copied, so the app never needs the network
(it has no INTERNET permission) and never touches the originals again.

Duplicate imports are allowed (UUID-keyed, no content hashing) — same trade-off as ln-reader;
duplicates are visible and deletable.

## Player

### Service half (`player/PlaybackService.kt`)

Same skeleton as ln-reader: `MediaSessionService` + `ExoPlayer` configured for spoken word
(`C.AUDIO_CONTENT_TYPE_SPEECH`, audio-focus, becoming-noisy), position saved to
`PositionRepository` every 5 s while playing and on every pause, keyed by
`currentMediaItem.mediaId` (= play id). `onTaskRemoved` keeps the service alive only while
playing.

The notification's custom layout diverges: **previous / next scene** buttons
(`CommandButton.ICON_PREVIOUS` / `ICON_NEXT` backed by custom session commands — the only
kind `DefaultMediaNotificationProvider` renders). Their handler hops through the service
scope to `PlayRepository.sceneStartsMs(playId)` (cached script) and seeks via `SceneSkip`.
The default SEEK_TO_PREVIOUS/NEXT player commands are removed on connect, as in ln-reader,
so the notification shows our buttons instead of track skips.

### Scene skipping (`player/SceneSkip.kt`)

A pure-function object shared by the notification handler and the mini-player so both behave
identically. `previousTargetMs` follows the audio-player chapter convention: > 3 s into the
current scene restarts it, otherwise jumps to the scene before; `nextTargetMs` returns null in
the last scene (no-op). Scene starts come from the script, not the m4b chapters.

### UI half

`PlayerHolder` (process-scoped, in `AppContainer`) is ln-reader's pattern verbatim: a
`MediaController` bound once, exposed as `StateFlow<MediaController?>`, with
`loadPlay(play, startMs, playWhenReady)` building the `MediaItem` (mediaId = play id,
`file://` URI into private storage, cover as artwork).

**There is no full player screen.** The `MiniPlayerBar` (root Scaffold's bottom bar, visible
everywhere including the training screen) *is* the transport: artwork, title, progress strip,
play/pause with a buffering spinner, and the two scene buttons. Now-playing state is read
straight off the controller via the `produceState` + `Player.Listener` + 1 s poll pattern.
Expanding it navigates to the training screen — which is this app's "full player".

## TrainingController — the rehearsal mute engine

The one genuinely new component; template was ln-reader's `SleepTimerController` (a
process-scoped peer of `PlayerHolder` driving playback through the same `MediaController`).

- **Process-scoped, not ViewModel-scoped**: muting must keep working when the user backgrounds
  the app and drives playback from the notification. The service runs in the same process, so
  "audio is possible" always implies "this controller is alive".
- API: `start(playId, speakers, muteRanges)` / `stop()`, plus `session: StateFlow<Session?>`
  and `isMuted: StateFlow<Boolean>` (drives the "speak now" UI). `start` is **idempotent for
  the same (playId, speakers)** — the training screen re-arms on every entry, and restarting
  would cause a volume blip mid-line. Ranges are precomputed by the caller
  (`PlayScript.muteRangesFor`), so the engine never parses a script.
- **Loop mechanics**: a `Dispatchers.Main.immediate` coroutine collects
  `playerHolder.controller` with `collectLatest` (a reconnect restarts the inner loop; a fresh
  controller starts at gain 1). Inside, a `Player.Listener` re-evaluates immediately on
  `onPositionDiscontinuity` — that's what makes tap-to-seek and notification seeks mute
  *before the first syllable* — and on `onMediaItemTransition`.
- **Deadline-aware tick**: base 100 ms, but each evaluation returns
  `min(100 ms, time-to-nearest-range-edge ÷ playbackSpeed)` (floor 20 ms), so ticks land
  essentially on mute boundaries. Worst-case edge error ~0.1 s disappears into the script's
  ~250 ms inter-line silences. Each tick is one binary search over the merged ranges — noise.
- **Guards and invariants**: if `currentMediaItem.mediaId != session.playId` the engine forces
  gain 1 and idles (a foreign load can never be muted by a stale session). Volume writes only
  happen on state *transitions*. The `finally` block restores gain 1 and removes the listener
  on any cancellation path. Muting is **player-level gain only** — never `AudioManager` stream
  volume — so device volume and other apps are untouched, and a fresh process after a kill
  starts at gain 1 with nothing to restore. This class is the app's **only volume writer**
  (the sleep timer wasn't ported), so there is no writer conflict by construction.

## Training screen

### State shape

`TrainingUiState.items` is a flattening of the script into sealed `ScriptItem`s —
`SceneHeader` / `Bubble(entryIndex, startMs, endMs, speaker, text, direction, emotion,
isMine)` / `StagingNote(isCue)` — with **stable keys** (`"s-<n>"` / `"e-<n>"`). A role change
rebuilds the list (flips `isMine`) but keys stay identical, so the LazyColumn keeps its scroll
anchor. An `IntArray` maps flat entry index → item index (scene headers shift them apart).

### Position → UI, with recomposition scoping

The ViewModel polls `controller.currentPosition` every 300 ms (`collectLatest` on the
controller flow) and:

- updates `activeItemIndex` **only when it changes** — screen-wide state moves at
  line-change frequency, not 3 Hz;
- mirrors the raw position into a **separate** `positionMs: StateFlow<Long>`, deliberately
  outside `TrainingUiState`. Only the *active* bubble's progress gauge collects it (a
  conditional composable read), so the 3 Hz ticks recompose exactly one row.

Per-line gauges derive their fill from the already-cheap comparisons: before active = full,
after = empty, active = live fraction smoothed by a 300 ms linear `animateFloatAsState` that
turns the stepped polls into a continuous sweep (and doubles as a fill/drain animation on
seeks). `seekTo` also sets the position flow optimistically so the tapped bubble doesn't lag
one poll.

### Auto-follow that knows who's scrolling

The classic Compose trap: `listState.isScrollInProgress` is true during both user drags *and*
`animateScrollToItem`, so it can't distinguish them. What can:
**`listState.interactionSource` emits `DragInteraction.Start` only for touch drags** —
programmatic animations never emit drag interactions. A collector flips `autoFollow = false`
on drag; a `LaunchedEffect(activeItemIndex, autoFollow)` animates the active item to about a
third from the top while following. A user touch mid-animation both cancels the in-flight
scroll (effect restart) and disables following — exactly the intended feel. Re-enabling is
explicit only (the jump-to-current FAB). `autoFollow` is plain `remember`: ephemeral by
design, re-entering the screen always resumes following.

### Drills

Two booleans from `TrainingPreferences` (DataStore — prefs are the source of truth; the
toggles write, collectors feed state back). Hiding a bubble's text keeps the speaker name
(added to "me" bubbles in that mode — normally alignment identifies them) and the gauge,
whose row is then given an explicit width **proportional to the line's duration** (72–272 dp,
capped at 20 s) — with no text to size the bubble, the gauge itself encodes line length,
voice-message style. Staging notes are never hidden; they're context, not lines to learn.

### Keep-screen-on

A root-level composable mirrors `Player.isPlaying` (listener only, no polling) into
`view.keepScreenOn`. Playing = screen stays awake through hands-free scenes; paused = normal
timeout. Backgrounded, the flag is moot and the foreground service carries playback.

## Storage layout

```
/data/data/com.vibetuned.to_reply/files/
  └── plays/<playId>/
        ├── audio.m4b     copied recording (the only thing the player ever reads)
        ├── play.json     copied script (parsed on demand, cached in PlayRepository)
        └── cover.jpg     first embedded covr image, when present
```

Everything is internal; deleting a play removes the directory, its rows, and its script-cache
entry in one call.

## Navigation

```
home
training?playId={playId}&autoPlay={autoPlay}
```

Documented deviation from the house style: **no `NavigationBar`** — with a single top-level
destination a bottom nav would be one useless tab. The root Scaffold's bottom bar is the
`MiniPlayerBar` alone; the `TopLevelDestination` enum stays so a second tab is a one-line
addition. The mini-player is *not* hidden on the training screen (ln-reader hides it on its
full player) because it is that screen's transport.

## AGP 9 quirks worth remembering

Identical to ln-reader — see [NEW_APP_SPEC.md](NEW_APP_SPEC.md) §2 for the full list:
no `org.jetbrains.kotlin.android` plugin, no `kotlinOptions {}` (use `jvmToolchain(17)`),
`android.disallowKotlinSourceSets=false` for KSP, and no Hilt until it supports AGP 9.

## Decisions retired / rejected

- **Script entries in Room** — rejected up front; see [Data layer](#data-layer). A migration
  per script-format tweak was the deal-breaker.
- **A full player screen** — dropped. The training screen is the destination; a separate
  now-playing screen would duplicate it with less information. The mini-player carries the
  transport everywhere instead.
- **±10 s / ±30 s skips** — replaced by scene skips (user decision): a scene is the meaningful
  rehearsal jump unit. The ExoPlayer seek increments are still configured for headset buttons.
- **`OpenMultipleDocuments` import** — rejected for the two-step picker (deterministic file
  roles, per-step MIME filters, clean cancel).
- **Speaker-name normalization** ("Caissière 1 (Roselyne)" ≡ "Roselyne") — deliberately not
  implemented in v1; some parenthetical variants are genuinely distinct characters, and the
  picker only offers strings that exist in the script, so exact matching is always safe.
  Revisit with product input.
- **Import dedup** — skipped, matching ln-reader; honest dedup needs a content hash (seconds
  of IO per 64 MB import) or trusting `scenario_id` as a natural key (it isn't one across
  revisions of the same play). Backlog: store `scenarioId` and warn — not block — on match.
