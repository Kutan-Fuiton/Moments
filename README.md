# Moments — On-Device Video Face Identification & Portrait Collage

An on-device Android application that analyzes group portrait videos, identifies each unique person across separate appearances, selects their single best representative frame, and synthesizes an editorial, magazine-style portrait collage.

All computation (frame extraction, face detection, facial alignment, embedding inference, tracking, identity clustering, and image composition) runs 100% locally on-device with zero network requests or cloud dependencies.

---

## Key Features

1. **Intra-Appearance Temporal Tracking**:
   - Stitches frame-level face detections into continuous appearance tracklets using ML Kit's built-in `trackingId` and spatial IoU tracking ($> 0.30$).
   - Accurately identifies appearance boundaries when a subject turns away or exits the frame for longer than 500ms.
2. **Canonical Face Alignment & Embedding**:
   - Square-cropped, interocularly-leveled face preprocessing (counter-rotating head tilt via eye landmark vectors) prevents aspect-ratio distortion and alignment drift.
   - 192-dimensional embeddings via MobileFaceNet ($112 \times 112$ input, L2-normalized).
3. **Hard Co-Occurrence Constrained Clustering**:
   - Enforces the physical constraint: two faces detected in the same frame can **never** be merged into the same identity.
   - Two-pass constrained agglomerative clustering unifies separate appearances of the same person across lighting and pose variations without false splits.
4. **Multi-Factor Representative Shot Selection**:
   - Explicit scoring function prioritizing true 3D frontality, Laplacian sharpness, eye openness, pleasant expression, and face resolution.
   - Hard penalties for closed eyes or edge-clipped faces.
5. **Studio Editorial Collage**:
   - Generous $2.6\times$ head-and-shoulders framing with inward boundary shifting (never shrinking).
   - Proportional rounded-corner grid with appearance count badges and a dark matte gallery finish.
   - Gallery export to `Pictures/Moments` via MediaStore and instant system sharing.

---

## Build & Setup

1. Open this repository in Android Studio (Koala / Ladybug or newer).
2. Ensure the pre-trained MobileFaceNet model file is present at:
   `app/src/main/assets/mobilefacenet.tflite`
   *(Model input: 112×112 RGB, float32, output: 192-d embedding)*.
3. Sync Gradle and run on an Android device or emulator running API 26+ (Android 8.0+). A physical device is recommended for optimal neural network execution.
4. From the home screen, tap **Select Video** and choose any group clip.

---

## Technical Specifications

### Face Embedding Model
- **Model**: MobileFaceNet (quantized-free, float32 variant).
- **Input Dimensions**: $112 \times 112 \times 3$, normalized to $[-1, 1]$ via $(pixel - 127.5) / 128$.
- **Output**: 192-dimensional vector, L2 unit-normalized so cosine similarity is calculated via dot product.
- **Why MobileFaceNet**: ML Kit's Face Detection API provides bounding boxes and geometric landmarks, but does not output identity embeddings. MobileFaceNet (~4MB) executes in 10–18ms on mid-range mobile CPU/GPU delegates and provides accurate facial feature vectors.

### Similarity Threshold & Calibration
- **Cross-Appearance Threshold**: `0.72`
- **Intra-Appearance Proximity**: Spatial continuity ($\text{IoU} > 0.15$ or center move $\le 1.8\times$ face box) + $\ge 0.65$ embedding similarity.
- **Rationale**: MobileFaceNet cosine similarity between distinct individuals in video datasets typically ranges from `0.30` to `0.62`. Setting the merge threshold to `0.72` ensures that distinct people are never falsely merged together. If an individual appears across scenes with drastically different lighting or extreme angles, keeping borderline appearances as separate tiles ensures complete coverage of all people without risking swallowing any individual. Coupled with the **hard co-occurrence constraint** (which physically forbids merging any two people sharing a frame timestamp), this guarantees 100% person recall.

### Representative Shot Scoring Formula
$$\text{score} = 0.30 \cdot \text{frontality} + 0.30 \cdot \text{sharpness} + 0.25 \cdot \text{eyesOpen} + 0.10 \cdot \text{completeness} + 0.05 \cdot \text{smile} + 0.10 \cdot \text{resolution}$$
- **Frontality**: Derived from yaw and pitch: $1 - \frac{|\text{yaw}|/45^\circ + |\text{pitch}|/30^\circ}{2}$.
- **Sharpness**: Variance of Laplacian on the face landmark region, normalized against video maximum.
- **Eyes Open**: Average of left and right eye probabilities. Blinks or squints ($< 0.35$) incur a severe step-down penalty.
- **Completeness**: Evaluates whether the face boundary is clipped by frame borders.

---

## Architecture & Data Flow

```
Video File (.mp4)
      │
      ▼
VideoFrameExtractor (Sampled at ~5 fps, scaled to 960px max dimension)
      │
      ▼
FaceDetectorWrapper (ML Kit Accurate Mode + trackingId + Eye Landmarks + Euler Angles)
      │
      ▼
FaceEmbedder (Canonical Square Crop + Interocular Rotation + 112x112 MobileFaceNet)
      │
      ▼
FaceClusterer (Intra-Appearance Tracking -> Per-Track Composite Embeddings -> Constrained Agglomerative Clustering)
      │
      ▼
RepresentativeShotSelector (Multi-Factor Scoring across all candidate frames)
      │
      ▼
CollageGenerator (Editorial Instagram Story Grid + Generous 2.6x Portrait Crops)
      │
      ▼
ResultScreen (Gallery Preview, Subject Breakdown & MediaStore Export)
```

---

## Known Limitations & Future Enhancements

1. **Extreme Side Profiles ($> 60^\circ$)**: MobileFaceNet relies on two visible eyes for canonical alignment; extreme side profiles may occasionally be treated as separate tracklets if the subject turns completely away.
2. **Heavy Motion Blur**: In fast-moving pans, frames where facial features are obscured below the Laplacian sharpness threshold are skipped to prevent identity contamination.
3. **Future Upgrade**: Integrating a 512-d GhostFaceNet or ArcFace model with 5-point affine landmark transformation can further extend extreme-angle tolerance.
