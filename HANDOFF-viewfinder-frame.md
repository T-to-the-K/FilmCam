# FilmCam viewfinder: mini-box frame landed off-centre

**Status: RESOLVED in v0.5.5.** Root cause was a stale GL viewport, not geometry.
Keep §7 as a do-not-re-chase list and §9's build notes; the rest is the record
of getting there.

**The fix, in one line:** nothing in the project ever called `glViewport`, so after
`applyRenderScale` resized the surface from 1080x2400 to 576x1280 the viewport stayed
at the original size and the frame was drawn into the bottom-left of a larger
coordinate space. `FilmLookRenderer.onSurfaceChanged` and `onDrawFrame` now set
`glViewport(0, 0, viewWidth, viewHeight)`; the frame lands centred and symmetric.

**Measured, on device, before and after** (576x1280 buffer, `glReadPixels` dump):

```
before   x 76..575   y 0..698    500x699    off-centre right and high
after    x 40..535   y 310..969  496x660    insets L40 R40 T310 B310
```

The before figures were what a stale viewport predicts, exactly — including the
581px bottom black run, which is 2400-1819. That is what confirmed it, not the
reasoning.

**Repo:** `/home/tk/projects/FilmCam` (Android, Kotlin/Compose, CameraX, GLES 2.0)
**Device:** Pixel 8, serial `38271FDJH003T1`, Android 17, 1080x2400, density 2.625
**Branch:** shipped in v0.5.5 on top of `9aaac98` (v0.5.4)

---

## 0. Where this actually ended up

Beyond the viewport fix, three real bugs surfaced while framing the box:

1. **The viewfinder was mirrored.** `FilmShader.transform` derived the rotation
   from `SENSOR_ORIENTATION` instead of reading CameraX's `TransformationInfo`, and
   its sampling map transposed the buffer's v axis. The result was a reflection,
   not a rotation — which is why it survived a 288-case check against a reference
   implementation that shared the same omission, and why every edge-orientation
   measure read healthy. Now driven by
   `SurfaceRequest.TransformationInfo.getRotationDegrees()` / `isMirroring()`.
2. **Preview and capture had different fields of view.** Preview was bound 720p
   (16:9) with no aspect strategy while capture was left free, and CameraX crops
   whichever use case does not match the sensor. Both are now bound with the same
   4:3 `AspectRatioStrategy`.
3. **The frame's crop was wrong.** The crop was sized from the post-rotation
   aspect and carried a `(1 - s) / 2` translation, but coordinates arriving at the
   transform are already centred, so the translation slid the frame sideways by a
   third of the screen. See `FilmShader.cropFactors`.

The aspect-ratio picker is gone: `ShotRatio.kt`, the chips, the six strings,
`setShotRatio`/`onShotRatioChanged` and `cropToShotRatio` were all removed. The
whole field of view is shown in a centred 4:3 inset, no crop.

`FRAME_FILL` (0.86) sizes the box off the view's long edge so it is the same
visual size in both orientations. `FRAME_LIFT` (0.07) raises its centre so it
clears the controls along the bottom edge — applied to the centre, not an edge,
so size and aspect are untouched.

Verified on the Pixel: launches, graded viewfinder live at 7ms median / 2.47%
janky over 364 frames, geometry correct.

---

## 1. What was wanted

Replace FilmCam's selectable full-bleed aspect ratios with a Dazz Cam-style
centred inset frame: whole field of view, portrait box, interface around it. No
crop, no ratio picker. 4:3 stays 4:3 in the saved photo.

The whole ratio feature is already **removed** — enum, picker composable, six
strings, `setShotRatio`/`onShotRatioChanged`, `cropToShotRatio`. That part is
done and builds clean.

## 2. The symptom

User's own description, which was correct:

> "it was the same size as before just off the screen to the right"

Size is right. **Position is wrong** — the frame sits right and high, running
off the top and right edges of the surface.

## 3. What is verified

These are measurements, not inferences.

**The geometry code is correct.** `FilmLookRenderer.frameRect` for a 576x1280
surface returns `-0.86,-0.516,0.86,0.516`. Simulated, that is:

```
x 40.3 .. 535.7   width 495.4
y 309.8 .. 970.2  height 660.5
centre x=540.0  y=1200.0   (screen centre)  -> error 0.0px on both axes
insets  L/R/T/B = 75.6px
```

It adapts correctly to landscape. Logged for a 1280x576 view:
`rect=-0.29024997,-0.86,0.29024997,0.86` — a correctly inset landscape frame.

**The GL pipeline is healthy.** No `glGetError` anywhere. `prog=3`, `tex=1`,
`aPosition=1`, `aTexCoord=0`, quad FloatBuffer written correctly, camera
delivering frames (screencap grows 37KB → 1.7MB once the scene is lit).

**The vertex shader applies no transform.** `FilmShader.kt:32` is
`gl_Position = aPosition;` — no matrix, so NDC passes straight through.
`uViewSize` is used only at `FilmShader.kt:182` for vignette aspect. **This rules
out** the "shader misinterprets NDC because uViewSize is stale" theory, which was
the main suspect before this was read.

**The fault is inside the GL layer, not the compositor.** This came from reading
the framebuffer back with `glReadPixels` (the one decisive measurement in the
session — see §5). The GL buffer is already wrong before SurfaceFlinger touches
it.

## 4. The measurement that matters

`glframe_576x1280_n22.png`, pulled off the device. 576x1280, the surface the
renderer actually drew into.

```
strict pure-black column runs (>99.5%): x 0..75            (76px)
strict pure-black row    runs (>99.5%): y 699..1279        (581px)

observed frame: x 76..575  y 0..698   -> 500 x 699
expected frame: x 40..536  y 310..970 -> 496 x 660
```

So in the GL buffer: left border 76px (expected 40), bottom border 581px
(expected 40), no top border, no right border, frame 500x699 instead of
496x660, clamped to the right edge.

**Caveat, important:** this measurement is confounded. A camera scene legitimately
contains pure-black pixels, so "find the first non-black pixel" finds the frame
edge only where the border happens to be black. The hard borders above are real
edges; the absent top/right borders may be the measurement failing, not the app.
**This is the weakest part of the whole note.** Re-measure against a flat,
evenly-lit surface before trusting the 500x699 figure.

## 5. How to reproduce the read-back

Diagnostics are installed on the device. `FilmLookRenderer.maybeDumpFramebuffer()`
runs `glReadPixels` after `glDrawArrays` and writes PNGs to:

```
/storage/emulated/0/Android/data/com.tk.filmcam/files/glframe_576x1280_nNN.png
```

Roughly 20 dumps from the current session are sitting there already. Pull with:

```
adb shell cat /storage/emulated/0/Android/data/com.tk.filmcam/files/glframe_576x1280_n22.png > out.png
```

(`adb pull` fails on this path; `cat` works.) Dumps fire once per frame counter up
to 240, so early ones catch the surface mid-resize and later ones catch it steady.

Analysis scripts, both in `/tmp/opencode/`:
- `measure_frame.py` — frame box, aspect, centring, insets from a screencap
- `framing_check.py` — offline port of `frameRect` + `cropFactors`, 28 cases pass

`framing_check.py` passing does **not** mean the on-screen frame is right. It
tests the math in isolation and never touched a real surface.

## 6. Logged sequence at startup

```
surfaceCreated program=3 tex=1
surfaceChanged 1080x2400          <- view laid out full size
draw#1..3: no cameraTexture yet   <- texture not created yet, benign
surfaceChanged 576x1280           <- applyRenderScale shrinks the buffer
creating SurfaceTexture 1024x768
draw#60 cam=1024x768 view=576x1280 orient=90 glErr=0
rect#60 frameAspect=0.75 view=576x1280 rect=-0.86,-0.516,0.86,0.516
```

Two things here, both possibly relevant and both unexamined:

1. `surfaceChanged` fires **twice**. The view is `fillMaxSize()` (1080x2400) and
   `applyRenderScale` then shrinks the buffer to 576x1280 — a surface resize
   while the camera is streaming. First three frames drew with no texture at all.
2. `applyRenderScale` (`FilmGlPreview.kt:111`) scales both axes by one factor,
   but `MAX_RENDER_EDGE = 1280` is what forces the 576x1280 buffer and the
   compositor's `toDisplayTransform scale x=1.875 y=1.875` in the first place.
   Removing the downscale is the cheapest way to test whether it matters.

## 7. Things already ruled out — do not re-chase these

- **Aspect-buffer mismatch.** I claimed `applyRenderScale` rounded the axes
  independently and broke the aspect. Wrong: 1080x2400 → 576x1280 is already a
  uniform 0.533 scale. Reverted.
- **Display rotation bug.** SurfaceFlinger showed `bounds={0,0,2400,1080}` and I
  read it as the display being landscape. Wrong — that is SurfaceFlinger's
  coordinate convention. Display is genuinely `rotation 0`, portrait.
- **Landscape geometry not adapting.** I read a log line from before the surface
  resize had settled and concluded `frameRect` ignored orientation. Wrong — it
  adapts correctly (§3).
- **Shader / NDC mismatch.** Ruled out by reading `FilmShader.kt:32` (§3).

## 8. Where to start, in hindsight

All three of these were on the list and item 2 was the answer:

1. ~~Re-measure against a flat surface.~~ Worth doing, but it never would have
   found this. Scene content was a red herring — the frame's *edges* were the
   measurement, and those were correct.
2. ~~Read `updateQuad` and the NDC convention.~~ **This was it.** Specifically the
   part that got skipped: check whether anything calls `glViewport`. Nothing did.
3. ~~Try removing the downscale.~~ Not the cause. `MAX_RENDER_EDGE` stayed at
   1280; downscaling was never the problem and removing it only costs fill rate.

If you are ever again staring at a correct-in-isolation transform producing a wrong
frame, check the viewport before re-reading the transform.

## 9. Housekeeping

- ~~**Remove the diagnostics before shipping.**~~ Done. Per-frame `Log.i` in
  `FilmLookRenderer` (`diagDrawn` counters, `rect#`, `draw#`),
  `maybeDumpFramebuffer`, `dumpDir`/`dumpedAt`, and the `FilmGlPreview` logging
  were all added during this session and none existed before. All removed. Four
  log lines remain for the whole session — surface created, two resizes, texture
  created.
- ~~`FilmCameraController.gradeFile` has a leftover dead identity check.~~ Done.
- Fallback `PreviewView` uses `FILL_CENTER` (MainActivity.kt:194), which
  full-bleeds. Same class of problem as the mini box, only on the fallback path.
- Build constraints: R8 OOM-kills on this 7.6GB host. Use unminified:
  `/tmp/opencode/build_nomin.sh`. **That script writes Gradle's real exit code to
  `/tmp/opencode/build_nomin.rc`** — an earlier version piped into `grep` and read
  grep's exit code, reporting `EXIT=0` for a build that had failed. Check what it
  captured before believing it.
- USB drops during long builds. `lsusb` showing no Google device means it's a
  hardware link problem, not adb — the kernel logged
  `usb3-port3: unable to enumerate USB device` repeatedly. A USB 2 port and a
  different cable both helped.

## 10. Honest assessment

Superseded by §0. Kept because the failure mode is the point: the geometry was
provably correct in isolation and still produced a wrong frame, because the
viewport it was expressed in had moved underneath it. Four theories were proposed
and disproved (§7) before anyone asked whether a GL viewport had ever been set at
all. Read this file as a record of that, not as a description of the current
state.

The lesson worth keeping: an offline simulation of `frameRect` is not evidence
about what the GPU drew. It described a correct frame for a viewport that was
wrong, and no amount of re-reading correct code would have found it.