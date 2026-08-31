# Play Store screenshots

Eight screenshots for the Play Console listing — four phone, four 10" tablet — plus the
scripts that produced them, so the set can be regenerated after any UI change.

```
screenshots/phone/       1080x1920   (9:16)
screenshots/tablet-10/   1600x2560   (5:8)
  01-library.png            The library: five imported plays, roles and listening progress
  02-training-normal.png    Training, reading along: chat script synced to the audio
  03-rehearsal-mute.png     Rehearsal mute: playhead inside a LOMOV line, "speak now" banner
  04-text-drill.png         Memorization drill: your lines collapsed to a name + duration gauge
```

All eight are 24-bit RGB PNG, within the Play Console limits (320–3840 px per side,
ratio under 2:1, well under 8 MB).

## Devices

Both are the existing ln-reader AVDs, Android 16 (API 36, `google_apis`, x86_64):

| AVD | resolution | density |
|---|---|---|
| `ln_phone` | 1080x1920 | 420 |
| `ln_tablet10` | 1600x2560 | 276 physical, **360 override** (`wm density`) |

Dark mode with Material You dynamic colour, which is where the blue-grey palette comes from.

## Reproducing

```sh
# 1. build + install, then boot ONE emulator
./gradlew :app:assembleDebug
emulator -avd ln_phone &            # or ln_tablet10
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.vibetuned.to_reply android.permission.POST_NOTIFICATIONS

# 2. load the demo plays, 3. shoot
store/seed_plays.sh
store/run_shots.sh store/screenshots/phone phone
```

| script | what it does |
|---|---|
| `seed_plays.sh` | Loads the five demo plays from `~/mac_share/demo/plays` |
| `run_shots.sh` | Drives the app into each of the four states and captures |
| `cap.sh` | Helpers: park the playhead, force a drill toggle, tap by content-description |
| `demo_bar.sh` | SysUI demo mode — fixed 10:00 clock, full battery, no notification icons |
| `make_covers.py` | Regenerates the two cover images in `covers/` |

`seed_plays.sh` needs `adb root` (works on these `google_apis` images). It wipes the plays and
positions tables first, then writes each play into `filesDir/plays/<uuid>/` and inserts the Room
row directly — reproducing exactly what `PlayRepository.import()` would have written, without
driving the two-step SAF picker eight times. It **replaces** any existing library on the device.

`run_shots.sh` parks the playhead by writing `positions.positionMs` and restarting the app: a
cold start reopens the last-played play, paused, at the saved position. Those positions are what
make each shot deterministic:

| shot | position | why |
|---|---|---|
| library | 751 s | Any position; the shot is Home, reached with Back |
| training-normal | 238 s | Chubukov's blessing — an *other* character's line, so audio is live and no banner |
| rehearsal-mute | 786 s | Inside Lomov's "Oxen Meadows are mine!" — muted, banner up |
| text-drill | 1545 s | The Squeezer-vs-Guess argument — short alternating lines, so the collapsed gauges vary |

## Two things worth knowing

**Cover art.** The demo `.m4b` files carry no `covr` atom — only a title tag — so every library
row would fall back to the same grey theatre-mask placeholder. `covers/` holds two generated
typographic covers (The Proposal, Trifles); `seed_plays.sh` installs those two and leaves the
other three on the placeholder, so the library shows both states as a real library would.
They are stand-ins for artwork the pipeline would normally embed, not real published cover art.

**Large screens.** Bubbles are capped at `widthIn(max = 320.dp)` with no large-screen
adaptation, so on the tablet the conversation splits into two narrow columns hugging the left
and right edges with a wide empty gutter between them, and the library list fills only the top
third. The tablet screenshots show this honestly. Capping the conversation column and centring
it (or a two-pane layout) would make the tablet shots — and the tablet experience — noticeably
denser.
