# FilmCam

A one-tap film camera for Android. Pick the kind of camera you want the look of, and FilmCam gives you that look.

No settings panels. No accounts. No ads. No analytics.

<p align="center">
  <em>Pick a camera type → shoot. That's the whole app.</em>
</p>

## Status: prototype

This is v0.3.0. It shoots real photos through CameraX and the selected film look is applied to the saved photo.

**Known limitation, still true:** the live preview is the raw camera feed. The film grade lands on the saved photo, not in the viewfinder — so what you see while shooting is plainer than what ends up in your gallery. Wiring the GLES look into the preview surface is the next piece of work; the shader is already written (`gl/FilmShader.kt`) and ready to drive the preview.

## Controls

| Gesture / control | What it does |
|---|---|
| Tap a film in the strip | Switch the look applied on save |
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

Order matters, and it follows the same principle film emulation uses: **tonality before effects**. Applying grain before tone mapping gets it crushed by the contrast curve and it stops reading as grain.

1. **Optical softness** — 5-tap blur, for sensor bloom
2. **Halation** — bright areas bleed into neighbours (the CCD/compact signature)
3. **Tonality** — contrast around a mid pivot, then lifted blacks for the faded look
4. **Colour** — saturation, per-channel gain, warmth shift
5. **Grain** — luminance-weighted noise, strongest in midtones like real film
6. **Vignette** — radial falloff

Each look is a parameter set in `film/FilmCamera.kt`, not a hardcoded branch. Adding a look means adding a data row.

Two implementations, one look:

- `gl/FilmShader.kt` — GLSL fragment shader for the real-time preview
- `pipeline/FilmStillProcessor.kt` — CPU path for the saved photo: tonality and colour via `ColorMatrix`, then highlight-extract bloom, quarter-resolution grain weighted to the midtones, then vignette

## Privacy

**The app declares no `INTERNET` permission.** It cannot upload anything, because it is not permitted to open a network connection.

- Photos are written to `DCIM/FilmCam` via MediaStore, so they appear in your normal gallery
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
- [x] Orientation-aware layout, portrait lock removed
- [ ] Wire the GLES look into the live preview
- [ ] Preview thumbnails per film look
- [ ] Frame counter per film type (24/36 exposures)
- [ ] **Rolls** — a finite number of exposures with a fixed grain seed per roll, then a develop moment before the images are revealed
- [ ] Retain negatives so a roll can be re-developed at a different exposure or temperature

## Licence

Apache-2.0. See [LICENSE](LICENSE).