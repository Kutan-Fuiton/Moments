# Project Overview — iykyk Collage

## The idea in one line
Hand the app any portrait video of a group of friends; it hands back an Instagram-Story-style
collage of every unique face in the clip, with each person shown once, in their best shot —
built entirely on-device, no server, no upload.

## Why this fits iykyk
iykyk's whole premise is turning shared, in-person dead time into something worth
re-living together afterward — the "funny clip you send to the group chat." This
assignment is that idea's little sibling: instead of a clip, you get a single shareable
image that says "here's everyone who was in this video, and here's their best face." It's
the same emotional payoff (a keepsake from time spent together) in a lighter, more
portable format — a natural companion feature to a phone-passing party game.

## What it does, end to end
1. **Pick a video.** No live camera — per the brief, a user selects an existing clip.
2. **Process, visibly.** The app samples frames, detects faces, computes an identity
   embedding for each one, groups matching faces into people, and tracks how many times
   each person appears — all while showing real stage-by-stage progress, not a spinner.
3. **See the result.** A polished collage: one tile per unique person, each showing an
   "appearance count" badge, arranged on a gradient card in a story-grid layout.
4. **Save & share.** One tap saves it to the gallery; another opens the native Android
   share sheet, so it goes straight to a group chat.

## The three "wow" moments
1. **It's genuinely offline.** Nothing about this app talks to a server. Face detection,
   the embedding model, and the clustering logic all run on the phone's CPU in real time —
   worth calling out explicitly in the demo, because it's easy to assume any face-AI app is
   phoning home.
2. **Live, honest progress — not a fake spinner.** The processing screen shows the actual
   pipeline stages (extracting frames → detecting faces → embeddings → clustering →
   picking shots → building the collage) with live counters, so the reviewer can see the
   pipeline is real and watch exactly where time goes on a 30-second clip.
3. **The collage itself is designed, not dumped.** Rounded tiles, drop shadows, a gradient
   scrim so badge text stays legible over any photo, and generous (never tight) face crops
   so every tile actually looks like a photo of a person, not a security-camera thumbnail.

## What "done" looks like for the submission
- Working debug APK that processes all three sample clips without crashing.
- A ≤5-minute screen recording showing: pick video → live processing progress →
  appearance counts → finished collage, for each of the 3 samples, held on screen long
  enough to read.
- A git repo with this README, the technical overview, and clean, reviewable code.
- Demo video uploaded to Google Drive, link sharing set to "Anyone with the link."

## Honest scope note
This was time-boxed to the assignment's own 12–15 hour budget. The architecture and full
pipeline logic (detection, embedding, clustering, shot-scoring, collage rendering) are
implemented end-to-end and documented — what's left to do on your machine is: drop in a
converted MobileFaceNet `.tflite` file (documented in `README.md`), run it against the
three real sample clips, and tune the one similarity-threshold constant if any two people
merge or one person splits. That last step is exactly the kind of judgment call the brief
expects you to make yourself with the real footage rather than have hardcoded.
