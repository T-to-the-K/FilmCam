# FilmCam

A one-tap film camera for Android. Pick the kind of camera you want the look of, and FilmCam gives you that look.

No settings panels. No accounts. No ads. No analytics.

<p align="center">
  <em>Pick a camera type → shoot. That's the whole app.</em>
</p>

## Status: prototype

This is v0.5.2. It shoots real photos through CameraX, and the selected film look is applied to the live viewfinder *and* to the saved photo — the same grade, in GLSL for the screen and in Kotlin for the file.

v0.5.2 fixes a launch crash in v0.5.1. The grade shader asked for `highp` in the
fragment stage, which is optional in GLES2 and is a compile error rather than a
downgrade on drivers that lack it — and a failed compile was killing the process
instead of falling back. GL setup failures are now reported to the UI, the view
swaps to an ungraded `PreviewView`, and the reason is printed on screen. The
shader guards its precision behind `GL_FRAGMENT_PRECISION_HIGH`, and the holder
is no longer resized from inside the layout pass.

v0.5.1 fixed the viewfinder itself. The `SurfaceTexture` frame-available
listener was never registered, so the GL surface only redrew when the look or
the rotation changed — a black screen, then a slideshow. The grade shader was
also taking 13 samples per pixel on an external texture, with `cos`/`sin` inside
an 8-tap halation loop, which is a fill-rate wall: 5 taps, no trigonometry, a
720p camera buffer, and a surface capped at 1280px that SurfaceFlinger scales
up. The grade itself is untouched, so saved photos are identical to v0.5.0.

The live frame rate is shown next to the zoom readout.

## Controls

| Gesture / control | What it does |
|---|---|
| Tap a film in the strip | Re-grade the viewfinder immediately |
| Pinch the viewfinder | Zoom, following the device's own zoom range |
| `−` / `+` | Step the zoom; the pill between them shows the current ratio |
| `⚡` | Flash on stills |
| `☀` | Torch |
| `⟳` | Flip between the front and back lens |
| Shutter | Shoot |

The layout is orientation-aware. Portrait keeps the control cluster along the
bottom edge; landscape moves it into a right-hand column, because in landscape
the right edge is where the thumb already is.

## The film looks

| Look | Character |
|------|-----------|
| **CCD** | Y2K digicam — high contrast, cool-shadow halation, slightly soft |
| **135** | Everyday 35mm — warm, gentle fade, fine grain |
| **120** | Medium format — square, low saturation, soft vignette |
| **Compact** | Punchy point-and-shoot — saturated, contrasty, tight blacks |
| **Disposable** | Single-use — heavy warmth, faded blacks |
| **Instant** | Fuji-style — very flat, lifted blacks, cool cast |
| **Lomo** | Toy cross-processed — magenta/green shift, strong vignette |
| **Super 8** | Motion-picture film — heavy grain, big vignette, warm |

## How the look is built

Order matters. **Tone map before you push colour.** The previous version boosted
saturation first and applied a linear contrast curve afterwards, which clips
whichever channel sits furthest from luma — measured, that drove 22% of pixels
out of range on average and turned bright scenes into solid white. The current
order is gains → soft S-curve → saturation → split toning → fade, then the
optical passes.

1. **Optical softness** — 5-tap blur, for sensor bloom
2. **Halation** — bright areas bleed into neighbours (the CCD/compact signature)
3. **Tone curve** — a soft S-curve that fixes 0 and 1 exactly, so it cannot clip; then saturation with a highlight roll-off so vivid hues desaturate toward white instead of posterising; then split toning (shadows and highlights get their own colour); then lifted blacks for the faded look
4. **Grain** — luminance-weighted noise, strongest in midtones like real film
5. **Vignette** — radial falloff

Each look is a parameter set in `film/FilmCamera.kt`, not a hardcoded branch. Adding a look means adding a data row.

Two implementations, one look:

- `gl/FilmShader.kt` — GLSL fragment shader for the real-time preview
- `pipeline/FilmStillProcessor.kt` — CPU path for the saved photo: the same per-pixel grade, then highlight-extract bloom, quarter-resolution grain weighted to the midtones, then vignette

The two implementations are kept in step deliberately; change one and change the
other, or the photo stops matching the preview.

## Privacy

**The app declares no `INTERNET` permission.** It cannot upload anything, because it is not permitted to open a network connection.

- Photos are written to `DCIM/FilmCam` via MediaStore, so they appear in your normal gallery
- Captures are downsampled to a 3500 px longest edge before grading, so a 108 MP
  frame cannot exhaust memory mid-save
- No analytics SDK, no crash reporter, no telemetry
- No third-party services of any kind
- The temporary full-resolution capture is deleted immediately after grading

## Build

Requires JDK 17+ and the Android SDK (compile SDK 36).

```bash
# point the SDK location
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`.

Minimum Android version is **Android 11 (API 30)**.

## Architecture

```
app/src/main/java/com/tk/filmcam/
├── MainActivity.kt              Compose entry point, permission handling
├── film/FilmCamera.kt           The film looks as data — no branching
├── gl/
│   ├── FilmShader.kt            GLSL film-grade fragment shader
│   └── FilmLookRenderer.kt      GL program wrapper
├── pipeline/
│   └── FilmStillProcessor.kt    CPU grade for saved photos
├── camera/
│   └── FilmCameraController.kt  CameraX binding, capture, MediaStore publish
└── ui/
    ├── FilmPicker.kt            The one-tap film strip
    └── theme/Theme.kt
```

## Roadmap

- [x] Zoom (pinch + buttons, ratio readout), flash, torch, working lens flip
- [x] Look strength retuned so every film is visibly distinct (measured, not guessed)
- [x] Film grade applied to the live viewfinder, not only on save
- [x] Looks rebuilt as aesthetic colour grades: no clipping, split-toned, pastel-to-vivid
- [x] Orientation-aware layout, portrait lock removed
- [ ] Wire the GLES look into the live preview
- [ ] Preview thumbnails per film look
- [ ] Frame counter per film type (24/36 exposures)
- [ ] **Rolls** — a finite number of exposures with a fixed grain seed per roll, then a develop moment before the images are revealed
- [ ] Retain negatives so a roll can be re-developed at a different exposure or temperature

## Licence

Apache-2.0. See [LICENSE](LICENSE).