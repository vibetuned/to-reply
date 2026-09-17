# Store screenshots

The full-resolution images behind the strips on the landing page, and the ones
uploaded to the two stores. The landing page links each thumbnail here, so
these stay checked in at native size.

```
phone/      1080x1920   Android phone      (Play Console)
tablet10/   1600x2560   Android 10" tablet (Play Console)
iphone/     1320x2868   iPhone 6.9"        (App Store Connect)
ipad13/     2064x2752   iPad 13"           (App Store Connect)
  01-library.png           The library: five plays, roles, listening progress
  02-training-normal.png   Reading along: the script as a chat, synced to the audio
  03-rehearsal-mute.png    Rehearsal mute: playhead inside your line, "speak now" banner
  04-text-drill.png        Memorization drill: your lines collapsed to a name + gauge
```

Optimised WebP thumbnails for the page itself live in
`../assets/shots/{android,ios}/` — same file names, height 1352, quality 80:

```sh
cwebp -q 80 -resize 0 1352 store-screenshots/phone/01-library.png \
  -o assets/shots/android/01-library.webp
```

All four sets show the same four moments of the same rehearsal — the same play,
the same character — so the two listings and the site tell one story.

## Where they come from

| Set | Produced by |
|---|---|
| `phone/`, `tablet10/` | `../../store/run_shots.sh` on the `ln_phone` / `ln_tablet10` emulators — see `../../store/README.md` |
| `iphone/`, `ipad13/` | `scripts/shoot-store.sh` in the [to-reply-ios](https://github.com/vibetuned/to-reply-ios) repo, on the iPhone 17 Pro Max and iPad Pro 13-inch simulators — see `metadata/screenshots/README.md` there |

Both capture scripts seed the same five public-domain demo plays and park the
playhead at fixed timestamps, so a rerun after a UI change reproduces the set
rather than a new set of near-misses.

The Android and iOS timestamps differ slightly for shots 2–4: the iOS shots are
taken with playback running, so each parked position sits far enough inside a
long line to absorb the few seconds of drift during launch.
