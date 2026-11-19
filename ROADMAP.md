# Aura AI Gallery — Roadmap

**Baseline:** 19 November 2026  
**Plan:** [docs/PROJECT_PLAN.md](docs/PROJECT_PLAN.md)  
**Authoritative product/build spec:** Aura AI Gallery Spec v2.1 — Audit Reconciliation

## Status at baseline

- [x] Preserve the previous AI Studio/web repository state on `legacy-reference/ai-studio-web`.
- [x] Commit the approved implementation plan to `main`.
- [x] Recreate the clean Android foundation inside the Android rebuild branch; added a launcher Activity and Compose gallery entry point.
- [ ] Complete M0 and make the clean Android branch build from a fresh checkout.

**Important:** A clean Android foundation was previously built in an isolated ParallelSandbox workspace on 18 November 2026 (`assembleDebug`, `check` and `assembleRelease` succeeded there). That source was not pushed to GitHub. It is prior test evidence only; it does not count as M0 complete until the source is committed and the GitHub build is verified.

## Priority correction — primary intelligence feature

**Product rule:** Aura's primary purpose is to recognize related media and group it into People and AI Sets. Multi-cloud import/export/sync is optional and must not block local recognition or gallery use.

## M2 — On-device recognition and AI Sets

**Status: In progress — on-device scene suggestions, face-location detection and persistent user-confirmed AI Sets are implemented; model validation and person identity grouping remain incomplete.**

- [x] Bundle a pinned MediaPipe Image Embedder MobileNet V3 Small float32 model at build time; verify the downloaded model SHA-256 before packaging.
- [x] Run scene feature extraction entirely on-device; no image, video, embedding or face data is uploaded.
- [x] Sample video frames at 18%, 50% and 82% of duration and aggregate their normalized scene feature vectors.
- [x] Correct photo EXIF orientation before analysis and bound decoded image/frame dimensions.
- [x] Detect face locations on-device with bundled ML Kit Face Detection; persist normalized boxes and video frame timestamps separately from scene vectors.
- [x] Persist scene vectors, model version and source fingerprint in Room; add explicit v2→v3→v4 migrations.
- [x] Reuse cached embeddings only when both model version and source fingerprint match; include MediaStore modified time in the fingerprint.
- [x] Cluster compatible vectors with cosine similarity, LSH candidate search and deterministic union-find grouping; regression test a 300-item candidate bucket.
- [x] Connect People and AI Sets screens to real scan results and show honest progress/failure states.
- [x] Save a reviewed scene suggestion as a persistent, user-named AI Set.
- [x] Persist AI Set memberships independently of scene embeddings; add Room v4→v5 migration.
- [x] Support manual AI Set rename, merge, delete and removal of individual members (split/correction).
- [ ] Evaluate scene-similarity precision/recall against a labelled on-device set of recurring rooms, outfits, backgrounds and compositions; tune the threshold before treating suggestions as final AI Sets.
- [ ] Add user-confirmed AI Sets with persistent names and manual merge, split, rename and membership editing.
- [ ] Select and license a face-identity embedding model; ML Kit detection alone must never be represented as person recognition.
- [ ] Implement per-face embeddings, normalization, similarity clustering and manually editable person identities only after model licensing and validation.
- [ ] Add couple/person-combination grouping while keeping identities independent from scene groups.
- [ ] Validate recognition on a real 2,000+ item photo/video library, including cross-date and cross-video matches, memory use and scan interruption/retry.

**M2 exit criteria:** AI Set suggestions reliably group recurring visual setups across different dates while keeping person identity independent. People groups are based on a licensed and validated face embedding model—not face boxes or detector tracking IDs. Users can review and manually correct groups. All pixel processing and inference remains on-device.

## M0 — Android build foundation

**Status: In progress — local debug/release build gates pass; fresh GitHub Actions JDK 17 run and device installation are still pending.**

- [x] Create the clean Android implementation branch `android/rebuild-m1`; keep the legacy web source on its reference branch.
- [x] Set root project name to `AuraGallery` and application ID/namespace to `ai.sakana.aura.gallery`.
- [x] Add the complete Gradle 8.13 wrapper and pin AGP 8.13.0 / JDK 17 target / compileSdk 36 / targetSdk 36 / minSdk 26.
- [ ] Pin Kotlin 2.0.21; local debug/release compilation passes on the available JDK 21 host. Validate the complete JDK 17 toolchain combination in CI.
- [x] Add a stable dependency version catalog; use stable releases only. Coil is pinned to 3.0.4 for Kotlin 2.0.21 metadata compatibility.
- [x] Add the single `:app` module, `@HiltAndroidApp` Application, and working Hilt/KSP wiring. Room 2.8.5 uses KAPT for schema generation due the recorded KSP serializer exception.
- [x] Add all referenced manifest resources: theme, backup rules, data-extraction rules, adaptive launcher icons and ProGuard rules.
- [x] Configure Timber and baseline unit/instrumentation test dependencies.
- [x] Add GitHub Actions workflow for wrapper validation, `:app:check`, `:app:testDebugUnitTest`, `:app:assembleDebug` and APK artifact upload.
- [x] Build the clean Android checkout with the committed Gradle wrapper; verify the GitHub Actions run on JDK 17 after push.

**M0 exit criteria:** GitHub Actions completes successfully from a fresh checkout and publishes an installable debug APK artifact. No missing resource, manifest, wrapper or dependency-injection errors.

## M1 — Real gallery on the phone

**Status: In progress — first real-gallery vertical slice implemented; device and scale acceptance tests remain pending.**

- [x] Implement MediaStore discovery for real device images; do not use drawable/sample media as gallery content.
- [x] Store real `content://` URIs and stable MediaStore IDs.
- [x] Add Room entities, DAO and database with one-shot reads, observation Flows and M1 URI/date/favourite indices. Hash/tag indices arrive with their later feature data.
- [x] Export Room schema version 1 through KAPT and keep destructive migration fallback disabled; add explicit migrations with each schema change.
- [x] Use persisted epoch-millisecond `Long` timestamps.
- [x] Add repository interface/implementation and `ObserveMediaUseCase`; feature/UI code depends on domain models/contracts.
- [x] Build a Compose gallery grid with Coil 3 and a configured `SingletonImageLoader`.
- [x] Add Photo Picker import using `PickMultipleVisualMedia` as the no-permission path.
- [x] Add full-library permission handling for API 33 and API 34+, including `READ_MEDIA_VISUAL_USER_SELECTED` and partial-access UI.
- [ ] Handle permission denial, revoked access, empty libraries and inaccessible URIs without crashing.
- [x] Add favourite toggle and persist its state in Room.
- [x] Add loading, empty, permission-required, partial-access and recoverable-error UI states.
- [x] Apply the supplied five-screen UI design system: graphite/safelight palette, bundled licensed typefaces, square contact-sheet grid, hairline controls, localized metadata and the Library / People / Edit / Optimize / Settings destinations.
- [ ] Add focused tests for media mapping, repository refresh behavior and favourite persistence. Six permission-policy unit tests currently pass.
- [ ] Verify on API 33, 34 and 36, including partial photo selection.
- [ ] Run a 1,000+ item scrolling/memory test on a real device or representative emulator.

**M1 exit criteria:**
- Real device photos display; no placeholder/drawable-backed gallery records.
- 1,000+ items scroll without stutter or OOM.
- Favourites survive force-stop and relaunch.
- API 33, 34 and 36 permission paths work; partial-access users still see selected media.
- Photo Picker imports continue working without broad library permission.

## M3 — Real deduplication, editing and export

**Status: Not started — follows the primary recognition milestone.**

- [ ] Implement SHA-256 over file bytes for exact-duplicate identity.
- [ ] Implement 64-bit dHash over decoded image content.
- [ ] Implement banded LSH candidate generation, Hamming-distance thresholding (initial threshold 5/64) and union-find clustering.
- [ ] Keep exact duplicates separate from visually similar/near-duplicate groups.
- [ ] Add indices and tests for exact match, resized image, recompressed image, brightness shift and unrelated images.
- [ ] Add duplicate review UI; never delete or merge automatically without user action.
- [ ] Persist non-destructive edit values in Room: brightness, contrast, saturation and warmth.
- [ ] Implement the ColorMatrix pipeline in the specified order: saturation → contrast → warmth, with brightness as translation.
- [ ] Add live preview using the same matrix logic as export.
- [ ] Export edited images to MediaStore under `Pictures/AuraGallery`, using `IS_PENDING` while writing.
- [ ] Preserve original pixels and prove revert/export behavior through tests.

**M3 exit criteria:**
- Two renamed copies of the same bytes are flagged as exact duplicates.
- A recompressed 80%-quality copy is identified as similar, not exact.
- Edited output is visible in the system gallery app.
- Revert/export does not overwrite the original; reverting restores original pixels.

## M4 — Optional cloud import, export and backup

**Status: Optional / not started — this must not delay local recognition.**

- [ ] Define provider-neutral cloud repository interface and item-level backup states.
- [ ] Implement real OAuth and upload for Google Drive API v3.
- [ ] Implement real Dropbox provider integration.
- [ ] Store credentials securely; never place access/refresh tokens in preferences, logs or source.
- [ ] Implement Hilt WorkManager integration and remove the default WorkManager initializer where required.
- [ ] Implement constraints (network/Wi-Fi and charging policy), retry/backoff and unique work.
- [ ] Make uploads resumable/idempotent so app restarts do not create duplicates.
- [ ] Persist per-item state and last successful remote identifier in Room.
- [ ] Add failure, cancellation, quota and authentication-expiry recovery states.
- [ ] Test with real provider accounts and byte-for-byte comparison of uploaded files.

**M3 exit criteria:**
- Killing the app mid-sync and reopening resumes without re-uploading completed items.
- Airplane mode queues work; eligible work drains when the required network is available.
- A file downloaded from Drive is byte-for-byte identical to the phone source.
- Provider errors are visible and recoverable; no simulated delay-loop is presented as backup.

## M5 — Advanced features and production hardening

**Status: Not started; intentionally deferred**

- [ ] Add Room FTS4 search across title, manual person labels, attire, environment and lighting.
- [ ] Add storage optimizer insights for large files, similar photos, screenshots and blurred photos.
- [ ] Add safe bulk actions with undo.
- [ ] Extract stable code into modules only when an actual seam and measurable benefit exist.
- [ ] Expand unit, integration, UI and screenshot tests.
- [ ] Enable and validate R8/minification and release configuration.
- [ ] Add localization/internationalization and accessibility review.
- [ ] Run final dependency, security, performance, memory and install/update checks.

**M5 exit criteria:** Each shipped feature has a real implementation, user-visible failure states and tests. Recognition completion still requires the M2 model, device and user-correction acceptance gates.

## Cross-milestone quality gates

- [ ] Every new behavior has a focused test; bug fixes begin with a failing reproduction test.
- [ ] Use repository wrappers and documented build/test commands.
- [ ] No fake IDs, mock media, simulated backup, stub AI output or untested "success" progress.
- [ ] No destructive Room migration fallback.
- [ ] No credentials, signing keys or private media in Git or logs.
- [ ] Keep `README.md`, this roadmap and the project plan aligned with actual repository state.
- [ ] Push completed changes and verify the remote branch/commit before reporting completion.

## Implementation notes and current blockers

- Room 2.8.5 KSP release processing hits a Kotlin compiler classloader serializer AbstractMethodError with the pinned Kotlin 2.0.21 toolchain. Room processing now uses KAPT, while Hilt remains on KSP. KAPT reports a Kotlin language 1.9 fallback for stubs; keep this visible and revisit on the next toolchain upgrade. Room schema export through KAPT has been verified.
- Coil 3.3.0 requires Kotlin 2.2 metadata and failed against Kotlin 2.0.21. Coil 3.0.4 is pinned to retain the v2.1 Kotlin baseline.
- The available sandbox host runs JDK 21, not the JDK 17 CI target. No Android emulator or attached device is available in this build environment. The app builds locally, but M0/M1 runtime acceptance is not complete until GitHub Actions runs under JDK 17 and API 33/34/36 device tests are performed.

## Change log

| Date | Change |
|---|---|
| 2026-11-19 | M2 recognition foundation: checksum-pinned local MobileNet V3 Image Embedder, sampled video-frame scene vectors, separate ML Kit face-location detections, Room v4 persistence/migrations, similarity suggestions in People/AI Sets screens, and deterministic LSH clustering tests. Scene suggestions and face detection are not the same as validated scene sets or person identity. |
| 2026-11-19 | M2 user-managed AI Sets: Room v5 saved sets and membership records; save reviewed suggestions, rename, merge, delete, and remove individual media members. Domain regression tests cover name validation, duplicate membership, merge ordering and split behavior. Scene-precision evaluation and face identity embeddings remain open. |
| 2026-11-19 | Established v2.1 milestone order; created legacy reference branch; added project plan. |
| 2026-11-19 | Applied the uploaded Aura UI design system to Compose, bundled Instrument Serif/Manrope/JetBrains Mono with OFL licences, added all five destinations and a non-destructive tone preview. Unimplemented M2–M4 data is clearly labelled rather than shown as mock metrics. |
| 2026-11-19 | Created android/rebuild-m1; added launcher app, MediaStore/Photo Picker repository, Room favourites, permission-aware Compose grid, schema v1, six permission tests and Android CI workflow. Local debug/release/check/unit-test build passed on JDK 21. Device/API and JDK 17 CI gates remain pending. |

## Status vocabulary

- **Not started** — no implementation exists in the active Android branch.
- **In progress** — implementation is underway but exit criteria are not met.
- **Blocked** — progress depends on an identified missing input or decision.
- **Done with evidence** — source is committed, automated checks pass, and milestone acceptance criteria have been verified.
