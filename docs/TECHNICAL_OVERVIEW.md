# Technical Overview — iykyk Collage

## Stack
- **Language:** Kotlin, minSdk 26, targetSdk 34
- **UI:** Jetpack Compose + Material 3 (single-Activity, state-driven, no navigation
  library needed for 3 screens)
- **Concurrency:** Kotlin Coroutines + Flow — the whole pipeline is a single suspending
  `Flow<Pair<ProcessingProgress, VideoResult?>>` collected by the ViewModel
- **Face detection:** ML Kit Face Detection (on-device, bundled model via
  `com.google.mlkit.vision.DEPENDENCIES = face` manifest meta-data — no Play Services
  download step at runtime)
- **Face embedding:** TensorFlow Lite running MobileFaceNet (112×112 in, 192-d out,
  L2-normalized) — see `README.md` for exact model sourcing
- **Frame extraction:** `MediaMetadataRetriever`, sampled every 200ms (5fps)
- **Persistence:** none needed — everything lives in memory for the duration of
  processing one video; save/share writes directly to MediaStore / FileProvider
- **No backend, no network calls at runtime.**

## Architecture

```
UI (Compose)  →  ViewModel (StateFlow<UiState>)  →  CollagePipeline (Flow)
                                                          │
                       ┌──────────────────────────────────┼───────────────────────────┐
                       ▼                                  ▼                            ▼
              VideoFrameExtractor                FaceDetectorWrapper           FaceEmbedder
              (MediaMetadataRetriever)              (ML Kit, ACCURATE)      (TFLite MobileFaceNet)
                       │                                  │                            │
                       └──────────────┬───────────────────┴────────────────────────────┘
                                      ▼
                              FaceClusterer
                    (nearest-centroid clustering + appearance
                     segmentation by cosine similarity / time gap)
                                      │
                                      ▼
                       RepresentativeShotSelector
              (frontality / sharpness / eyes-open / smile scoring)
                                      │
                                      ▼
                             CollageGenerator
                    (Canvas-drawn rounded-tile grid, gradient card)
```

**Layering rationale:** each pipeline stage is a standalone class with one job and no
Android UI dependency (only `FaceDetectorWrapper` and `FaceEmbedder` touch Android APIs,
for bitmap/ML Kit/TFLite interop) — they're unit-testable in isolation and swappable
(e.g. dropping in a different embedding model only touches `FaceEmbedder`).

## Data flow / models
- `FaceObservation`: one detected face in one sampled frame — keeps the **full frame
  bitmap** (never a tight crop) plus the face box, embedding, head pose, eye/smile
  probabilities, and a computed sharpness score.
- `Appearance`: a continuous run of `FaceObservation`s for one person — this is the unit
  the spec's counting rule is defined over.
- `PersonCluster`: one unique identity — a running centroid embedding + all its
  `Appearance`s + the chosen best `FaceObservation` (`representative`).
- `ProcessingProgress` / `ProcessingStage`: sealed-class progress model streamed to the UI
  so the processing screen shows real stage + count, not a generic spinner.

## Threading
`CollagePipeline.process()` is a `flow { ... }` builder. Frame extraction runs on
`Dispatchers.Default` inside `VideoFrameExtractor`; per-frame detection and embedding are
explicitly wrapped in `withContext(Dispatchers.Default)` inside the pipeline loop.
The Activity/ViewModel only ever touches `Dispatchers.Main` implicitly via
`collectAsState()` — the main thread never runs decoding, detection, or TFLite inference,
satisfying the "keep processing off the main thread" requirement while still letting
Compose react to live progress.

## Identity grouping algorithm (the 50%-weighted requirement)
Implemented in `FaceClusterer.kt`:
1. For each sampled frame (in time order), every detected face is compared against
   **currently-open tracks** (a track = a cluster that was seen very recently) by cosine
   similarity of its embedding against the track's last observation.
2. If a track matches above `similarityThreshold` (0.62), the face extends that track.
3. Otherwise, it's compared against **all known cluster centroids** (running average of
   every embedding assigned to that cluster) — this is what lets a person who leaves and
   re-enters the frame later in the video be recognized as the *same* person, starting a
   *new* appearance.
4. If no cluster matches either, a brand-new `PersonCluster` is created.
5. A track is closed (its `Appearance` finalized) once its cluster hasn't been seen for
   more than `maxGapMs` (700ms) — this absorbs a missed single-frame detection (blink,
   quick head turn) without wrongly starting a new appearance, while a real cut-away and
   later return still correctly counts as two appearances.

This is deliberately an **online/incremental** algorithm rather than a full offline
hierarchical clustering pass, because the video length and person count aren't known in
advance and a single forward pass is fast enough for a 30-second clip; see
`README.md → Known limitations` for the trade-off this makes.

## Representative shot scoring
`RepresentativeShotSelector.kt` scores every observation of a person:

```
score = 0.35 × frontality + 0.30 × normalized_sharpness
      + 0.20 × eyes_open_score + 0.15 × smiling_probability
      − 0.25 (if face is clipped at frame edge)
      − 0.20 (if eyes_open_score < 0.35)
```
`frontality` comes from ML Kit's head-pose yaw angle; `normalized_sharpness` is the
per-face variance-of-Laplacian (computed in `SharpnessUtil.kt`) normalized against the
max sharpness seen anywhere in the video, so it's scale-invariant across clips.

## Collage rendering
`CollageGenerator.kt` draws directly onto an ARGB `Bitmap` via `android.graphics.Canvas`
(no Compose needed for the pixel output, so it's straightforward to save/share as a plain
JPEG): a gradient background, a header with video label + person count, and an
`sqrt(n) × sqrt(n)`-ish grid of rounded tiles, each generously cropped (2.2× the face box,
clamped to frame bounds) — deliberately avoiding a tight bounding-box crop per the brief.

## Save & share
`SaveShareUtil.kt` — `saveToGallery()` inserts into `MediaStore.Images.Media` under
`Pictures/iykyk-collage/` (scoped-storage safe, API 29+ uses `IS_PENDING` correctly);
`prepareShareUri()` + `shareIntent()` write to app cache and expose it via the manifest's
`FileProvider` for `ACTION_SEND` through the system share sheet.

## Testing strategy (given the time box)
Pipeline classes (`FaceClusterer`, `RepresentativeShotSelector`, `SharpnessUtil`) are pure
functions over `FaceObservation`/embeddings with no Android framework dependency beyond
`Bitmap`/`RectF`, so they're straightforward to unit test with synthetic embeddings and
mocked ML Kit output — that's the natural next addition beyond this time-boxed build.
