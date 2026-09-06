# Strengthening the Core Pipeline: Detection → Identity → Representative Shot → Collage

Your architecture is already end-to-end, which is the hard part. The four weak links you named are exactly the four things the rubric weighs most heavily (detection+clustering+counting = 50%, shot/collage quality = 20%). Below is a concrete, buildable plan for each, in priority order, plus a realistic time-box given you're close to the deadline.

---

## 0. Quick diagnosis before you touch code

Before rewriting anything, instrument your current pipeline so you can *see* where it's failing:

- Dump every detected face crop (with frame timestamp + track ID) to internal storage during a debug run.
- Log: detection confidence, bounding box size, and (once you add them) embedding similarity scores between consecutive frames.
- Eyeball 2-3 minutes of Sample 1's dumped crops. You will almost always find the actual failure is one of:
  1. Detector missing faces at extreme angles / low light (detection recall problem)
  2. Same person's embeddings not matching across appearances (embedding/clustering problem)
  3. Different people merging into one cluster (threshold too loose)
  4. Good detections but ugly/blurry/closed-eye representative frame chosen (scoring problem)

Fixing #2 and #3 blind, without this visibility, wastes hours. Build this logging first — it's ~1 hour and will tell you exactly which stage to spend the remaining time on.

---

## 1. Face Detection (make it precise *and* recall-friendly)

ML Kit's face detector is fine, but most weak results come from *how* it's called, not the detector itself.

**Config to set explicitly:**
```kotlin
FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // not FAST
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // gives eyeOpen + smiling probs for free
    .setMinFaceSize(0.08f) // tune down if people are far from camera in your test clips
    .enableTracking() // ML Kit's built-in tracking ID — see Section 2
    .build()
```

**Frame sampling strategy** — don't run detection on every single decoded frame at full video FPS; it's wasteful and doesn't improve accuracy. Sample at ~6-10 fps (using `MediaMetadataRetriever` or `MediaCodec` decode + `getFrameAtTime`), which is dense enough to not miss a 1.4s appearance window (per the worked example, A+B share frame for 1.4s — at 6fps that's still 8+ samples).

**Filter garbage detections before they ever reach embedding/clustering:**
- Reject faces below a minimum pixel size (a 20×20px face crop will never embed reliably).
- Reject faces with detector confidence below ~0.5 if you have quality scores.
- Use ML Kit's head-pose Euler angles (`headEulerAngleY`, `headEulerAngleZ`) to flag extreme profile shots — you don't need to discard them (a real appearance may include a turn), but tag them as low-quality for later scoring.
- Handle the "blurred whip-pan counts for nobody" rule directly here: compute a fast blur score (variance of Laplacian, see Section 4) per frame; if it's below threshold, drop the frame entirely from consideration before detection even matters.

---

## 2. Within-Video Tracking (the step people skip, and it's why counting breaks)

This is the piece most solutions get wrong: **don't jump straight from per-frame detections to global embedding clustering.** Insert an intermediate *tracklet* stage that stitches frame-level detections into short-term tracks *within a single continuous appearance*, using spatial continuity — before you ever compare embeddings across time. This directly implements the "continuous visible segment" definition of an appearance.

**Approach:**
1. Use ML Kit's `enableTracking()` — it gives a stable `trackingId` for a face across consecutive frames as long as it doesn't disappear. This is your first-pass appearance segmentation, free.
2. A track **ends** (appearance ends) when:
   - The `trackingId` disappears for more than ~0.3-0.5s (tune this — too short and you fragment one appearance into many; too long and you merge distinct appearances across a cut).
   - OR the face becomes untrackable (extreme blur/occlusion — reuse your blur score).
3. Within a live track, accumulate multiple embeddings and quality scores (you'll pick the best frame from this track for both embedding centroid and representative shot — see Section 4).
4. Only **after** tracks are finalized do you compute one embedding (or averaged embedding) per track and run cross-track clustering (Section 3). This means clustering is comparing "trackA vs trackB", not "frame17 vs frame4218" — far fewer, far more reliable comparisons, and it naturally gives you the appearance count per person (number of tracks assigned to a cluster).

This structure — **detect → track (intra-appearance) → embed per track → cluster tracks (inter-appearance identity)** — is what turns your three separately-weak stages into a coherent, self-correcting pipeline, and it's exactly what the rubric's "appearance-count accuracy" is testing.

---

## 3. Face Embeddings + Cross-Appearance Clustering

**Model choice:** ML Kit does not provide embeddings, so you need a separate on-device model. For minSdk 26 + TFLite, standard choices:
- **MobileFaceNet** (128-d embedding, ~5MB, fast on-device) — most commonly used for this exact task, well documented, good accuracy/speed tradeoff. Recommended.
- FaceNet (512-d) is heavier and usually overkill for a mobile demo.

Document whichever you pick in the README along with input size (usually 112×112 aligned face) and normalization used — this is explicitly asked for.

**Face alignment matters more than model choice.** Before feeding a crop to the embedding model, align it using ML Kit's eye/landmark coordinates (rotate so eyes are horizontal, crop to a consistent margin around the face). Unaligned crops are the single biggest cause of "same person, different embedding" errors. This is cheap to add and usually gives the biggest accuracy jump of anything in this list.

**Per-track embedding:** Instead of embedding every frame, embed the 2-3 best-quality frames in a track (by the sharpness+frontality score from Section 4) and average (L2-normalize each, average, re-normalize). This is far more robust to one blurry/off-angle frame poisoning the whole track's identity.

**Clustering across tracks:**
- Compute cosine similarity between track embeddings.
- Use **incremental/greedy clustering**, not a fixed-k algorithm (you don't know how many people are in the video ahead of time): for each new track, compare its embedding to existing cluster centroids; assign to the best match if similarity > threshold, else start a new cluster. Update the centroid (running average) on each assignment.
- This is simpler to implement and debug than HDBSCAN/agglomerative on-device, and works well at this scale (a handful of people, dozens of tracks per video).

**Threshold selection (you must document this number and how you got it):**
- Don't guess a threshold and hope. Dump the pairwise cosine similarity for: (a) same-person track pairs you can visually confirm, and (b) different-person track pairs. Plot/inspect the distributions — there's usually a visible gap. Pick the threshold in that gap.
- Typical MobileFaceNet cosine similarity thresholds land around 0.5–0.7 depending on alignment quality, but *do not* just copy a number — verify it on your actual sample videos and state in the README how you arrived at it (this is explicitly graded).
- Expect to need distinct handling for the "A and B share the frame" case — two tracks active in overlapping time windows must never be merged into the same identity regardless of embedding similarity (add this as a hard constraint in your clustering: two tracks with overlapping time ranges can never be the same cluster).

---

## 4. Representative Shot Selection Engine

Build this as an explicit scoring function per candidate frame within a person's cluster, then just take the argmax. Don't hand-wave it — a small, well-documented weighted score is exactly what's being asked for.

```
score(frame) = w1*frontality + w2*sharpness + w3*eyesOpen + w4*smile + w5*faceCompleteness
```

- **Frontality**: derive from ML Kit's `headEulerAngleY` (yaw) and `headEulerAngleX` (pitch). `score = 1 - (|yaw|/maxYaw + |pitch|/maxPitch)/2`, clamped to [0,1].
- **Sharpness**: variance of Laplacian on the cropped face region (standard, fast, OpenCV or a small manual convolution — you don't need full OpenCV, a 3×3 Laplacian kernel pass over grayscale is enough). Normalize against the max seen in that track.
- **Eyes open**: ML Kit gives `leftEyeOpenProbability` / `rightEyeOpenProbability` directly when classification mode is enabled — average them.
- **Smile**: ML Kit gives `smilingProbability` directly. Don't over-weight this — "pleasant" ≠ "must be smiling"; keep it a minor positive tiebreaker, not a hard filter, or you'll bias against neutral-but-great shots.
- **Face completeness / not clipped**: check that the detected bounding box, expanded by your crop margin (see Section 5), doesn't hit the frame edge. Penalize heavily (near-zero) if the face is cut off — the brief explicitly calls this out.

Weight suggestion to start from (then eyeball-tune against your samples): frontality 0.3, sharpness 0.3, eyesOpen 0.25, faceCompleteness 0.1 (as a gate, not just a weight — hard-reject clipped candidates if any unclipped candidate exists), smile 0.05.

Pick the top-scoring frame **per cluster across all its tracks**, not just within one track — a person's best-ever frame might be in their 3rd appearance, not their 1st.

---

## 5. Collage Creation Engine

Two separate problems: (a) getting a good source image per person, (b) laying multiple people out attractively.

**(a) Generous cropping, not tight bounding-box crop** (explicitly required):
- Take the detected face bounding box, then expand it symmetrically — a good starting rule is expand to roughly **2.5–3× the box width/height**, centered on the face, clamped to frame bounds. This gives head+shoulders framing instead of a postage-stamp face, and looks far better in a collage tile.
- If the expanded crop would go out of frame (person near the video edge), shift the crop box inward rather than shrinking it, to preserve resolution.

**(b) Layout — Instagram Story-style grid:**
- Simple, robust, and looks polished: a **rounded-corner grid** (2×2 for 4 people, 3×2 for 5-6, etc.), each cell holding one person's representative shot, `centerCrop`-scaled to fill the cell, with consistent corner radius and a thin white/soft gutter between tiles (this is the actual visual signature of the IG Story collage template you're referencing).
- For odd counts (e.g., 5 people), don't force a perfect grid — use one larger "hero" tile (top person, or just the first cluster) plus smaller tiles around it; this is a common and easy-to-implement asymmetric layout that reads as intentional rather than a leftover cell.
- Add a subtle bottom gradient bar per tile if you want to show appearance count as a small badge/number overlay per person — nice touch for the "show each person's appearance count" requirement, and doubles as visible proof-of-work in your demo video.
- Render via a `Canvas`/`Bitmap` composition (works cleanly whether you're on Compose or Views) — draw each cropped+scaled bitmap into its cell rect with `Paint` + rounded-rect clipping path, rather than trying to force this through nested Compose layouts, which is more failure-prone for pixel-precise export.
- Export at a reasonably high fixed resolution (e.g. 1080×1080 or 1080×1920 for a Story-shaped output) so it saves/shares well.

**Save & share:**
- Save via `MediaStore` (scoped storage compliant, minSdk 26 means you should support both legacy and scoped paths, but targeting modern behavior via `MediaStore.Images` insert is the clean path).
- Share via `Intent.ACTION_SEND` with a `FileProvider` URI to the saved collage — standard share sheet, no extra library needed.

---

## 6. Suggested time allocation (assuming you're tight on hours)

Given the pipeline is functionally complete and these four things are what's weak, rough priority if time is short:

1. **Face alignment before embedding** (Section 3) — highest accuracy return for lowest effort.
2. **Track-based intra-appearance grouping + hard time-overlap constraint in clustering** (Sections 2 & 3) — this is very likely your actual counting-accuracy bug.
3. **Representative shot scoring function** (Section 4) — mechanical, fast to implement, directly graded.
4. **Generous crop + grid collage** (Section 5) — mostly visual polish, but explicitly graded and very visible in your demo video.
5. Only after the above: threshold tuning by eyeballing similarity distributions on your 3 sample videos (Section 3), since your thresholds will shift once alignment + tracking are fixed.

## 8. Making Processing Feel "Live" to the User (and it doubles as your debug view)

Turning the hidden backend pass into a visible, real-time-feeling feed is a genuinely good idea here — it directly satisfies "show clear processing progress," and as a bonus, it *is* your debug tool from Section 0, so you stop maintaining two separate things.

**How to structure it without breaking the "off main thread" requirement:**
- Keep the actual decode → detect → embed → cluster pipeline on a background dispatcher (`Dispatchers.Default` for CPU-bound ML Kit/TFLite work, `Dispatchers.IO` for file/video reads) exactly as required.
- Emit a lightweight `ProcessingEvent` (sealed class: `FrameSampled`, `FaceDetected(box, trackId, quality)`, `TrackClosed(trackId, appearanceCount)`, `ClusterFormed`, `Done`) from that background work via a `Flow`/`SharedFlow`.
- Collect this flow on the main thread purely to update UI — draw the current sampled frame in an `ImageView`/Compose `Canvas`, overlay bounding boxes on detected faces, show a running counter of tracks/clusters found so far. This is cheap UI work, not the actual processing, so it doesn't violate the responsiveness requirement even though it *looks* real-time.
- Throttle emission if frames are sampled faster than the UI can meaningfully show (e.g. UI updates capped at ~15fps display rate even if you internally sample faster) so the feed looks smooth rather than flickering.
- Since you're already decoding at ~6-10fps for detection (Section 1), you likely don't need a separate "real-time preview" decode pass — just reuse the same sampled frames for both processing and display. Don't decode the video twice.
- Visually: draw each detected face's bounding box in a distinct color per `trackingId` (ML Kit gives you this for free), so the user visually sees "oh, that's the same person being tracked" as the video scrubs through — this is a strong, cheap "wow, it's actually doing something" moment for your demo video.

This is low-risk to add late because it's purely additive UI wrapped around the existing pipeline — you're not changing the detection/clustering logic, just surfacing its intermediate state.

## 9. Multi-Person Frames: Crop and Score Each Face Independently

When two clearly visible people share a frame (like the A+B example at 10.1–11.5s), don't treat the frame as one shot-selection candidate — split it immediately after detection:

- ML Kit's detector already returns one `Face` object per detected face in a frame, each with its own bounding box, landmarks, and classification probabilities. Loop over all detected faces in that frame and produce **one independent crop per face**, each going into its own track/cluster pipeline as described in Sections 2–4.
- Apply the generous-crop rule (Section 5a) around each face's own box, not a single crop covering both people. If the two expanded crop regions overlap, that's fine — you're producing two separate images, each centered on its own subject.
- Each face's quality scoring (frontality, sharpness, eyes-open, smile) must be computed **per face**, not per frame, since in a shared frame one person can be a much better shot than the other — e.g., A might be looking dead at camera while B is mid-blink.
- Track continuity in a multi-person frame: match faces to existing tracks by combining IoU (spatial overlap with the previous frame's box for that `trackingId`) with the ML Kit tracking ID itself — usually the tracking ID alone is enough, but if two people cross paths and IDs get confused, embedding similarity is your fallback disambiguator.

## 10. Hard Blur Gate on the Representative Shot Engine

Right now, sharpness is one of several *weighted* factors (Section 4), which means a blurry frame could still theoretically "win" if it scores high enough on the others. Tighten this into a hard gate rather than just a weight, so a blurred frame can never be chosen as anyone's representative shot or even considered a valid appearance frame:

1. **Pre-filter at the frame level (cheapest, do this first):** compute variance-of-Laplacian for every sampled frame *before* detection even runs (Section 1). If it's below a "globally blurry" threshold — e.g., a fast whip-pan frame — discard the frame entirely; it produces no detections, no track updates, nothing. This is the "blurred whip-pan passes count for nobody" rule from the brief, enforced structurally rather than hoped-for.
2. **Per-face gate at candidate-scoring time:** even in an otherwise sharp frame, compute variance-of-Laplacian *cropped to just the face region* (motion blur on a moving head can be sharp everywhere else). If a face crop's sharpness falls below a minimum absolute threshold, exclude it from the representative-shot candidate pool for that person entirely — don't just downweight it, remove it, so it literally cannot be selected even if every other candidate is worse on other axes.
3. **Never let "best of a bad set" produce a bad result:** if *every* frame in a person's track fails the sharpness gate (rare, but possible for a fast walk-through appearance), fall back to the least-blurry available frame but flag it internally (e.g., a `isFallbackShot` flag) — useful to know about if a reviewer questions why one person's collage tile looks softer than the rest.
4. **Calibrate the thresholds against your actual sample videos**, same way as the clustering threshold in Section 3: dump variance-of-Laplacian values for frames you've manually judged sharp vs. blurred, and pick the cutoff from the gap between those distributions rather than an arbitrary constant — this number will differ from a generic default depending on your video's resolution and compression.

With this gate in place, "eyes open + smiling + frontal" no longer has any chance of overriding "clearly blurred" — sharpness moves from being one vote among several to a precondition for being in the running at all.

## 11. README checklist (explicitly required, easy to forget under time pressure)
- [ ] Build/setup steps
- [ ] Which embedding model you used, its size/input format
- [ ] The similarity threshold you chose, and *how* you chose it (distribution check, not a guess)
- [ ] Any known limitations (e.g., "clustering can merge two people wearing similar accessories under bad lighting" — judges respect honest limitations over silent gaps)
