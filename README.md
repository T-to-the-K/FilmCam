# FilmCam

A one-tap film camera for Android.

Pick the film look you want and shoot. FilmCam applies that grade to the live
viewfinder and to the saved photo, the same look in both. No settings panels, no
accounts, no ads, no analytics.

<p align="center">
  <em>Pick a film → shoot. That's the whole app.</em>
</p>

## How it works

Every look is a parameter set, not a hardcoded branch, so adding a film means
adding a data row. One grade, two implementations kept deliberately in step: a
GLSL shader for the real-time preview and a CPU path for the saved photo.

The order matters. Tone map before pushing colour: gains, then a soft S-curve
that cannot clip, then saturation with highlight roll-off, then split toning,
then lifted blacks for the faded look, then the optical passes.

## Controls

| Gesture / control | What it does |
|---|---|
| Tap a film in the strip | Re-grade the viewfinder immediately |
| Pinch the viewfinder | Zoom, following the device's own zoom range |
| `−` / `+` | Step the zoom; the pill between them shows the ratio |
| `⚡` | Flash on stills |
| `☀` | Torch |
| `⟳` | Flip between the front and back lens |
| Shutter | Shoot |

The layout is orientation-aware. Portrait keeps the controls along the bottom
edge; landscape moves them into a right-hand column, where the thumb already is.

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

## Privacy

**The app declares no `INTERNET` permission.** It cannot upload anything, because
it is not permitted to open a network connection.

- Photos are written to `DCIM/FilmCam` via MediaStore, so they appear in your gallery
- No analytics SDK, no crash reporter, no telemetry
- No third-party services of any kind

## Build

Requires JDK 17+ and the Android SDK (compile SDK 36).

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. Minimum Android version is
**Android 11 (API 30)**.

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

- [x] Zoom, flash, torch, working lens flip
- [x] Film grade applied to the live viewfinder, not only on save
- [x] Looks rebuilt as aesthetic colour grades: no clipping, split-toned
- [x] Orientation-aware layout, portrait lock removed
- [ ] Preview thumbnails per film look
- [ ] Frame counter per film type (24/36 exposures)
- [ ] **Rolls** — a finite number of exposures with a fixed grain seed per roll

## Licence

Apache-2.0. See [LICENSE](LICENSE).
