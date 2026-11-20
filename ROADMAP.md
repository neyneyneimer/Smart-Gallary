# Aura AI Gallery — Roadmap

**Baseline:** 19 November 2026  
**Plan:** [docs/PROJECT_PLAN.md](docs/PROJECT_PLAN.md)  
**Authoritative product/build spec:** Aura AI Gallery Spec v2.1 — Audit Reconciliation

## Status at baseline

- [x] Preserve the previous AI Studio/web repository state on `legacy-reference/ai-studio-web`.
- [x] Commit the approved implementation plan to `main`.
- [ ] Recreate the verified Android foundation inside the Git repository.
- [ ] Complete M0 and make the clean Android branch build from a fresh checkout.

**Important:** A clean Android foundation was previously built in an isolated ParallelSandbox workspace on 18 November 2026 (`assembleDebug`, `check` and `assembleRelease` succeeded there). That source was not pushed to GitHub. It is prior test evidence only; it does not count as M0 complete until the source is committed and the GitHub build is verified.

## M0 — Android build foundation

**Status: Not complete**

- [ ] Create the clean Android implementation branch `android/rebuild-m1`; keep the legacy web source on its reference branch.
- [ ] Set root project name to `AuraGallery` and application ID/namespace to `ai.sakana.aura.gallery`.
- [ ] Add the complete Gradle 8.13 wrapper and pin AGP 8.13.0 / JDK 17 / compileSdk 36 / targetSdk 36 / minSdk 26.
- [ ] Pin Kotlin 2.0.21 and validate the complete toolchain combination in CI.
- [ ] Add a stable dependency version catalog; use stable releases only.
- [ ] Add the single `:app` module, `@HiltAndroidApp` Application, and working Hilt/KSP wiring.
- [ ] Add all referenced manifest resources: theme, backup rules, data-extraction rules, launcher icons and ProGuard rules.
- [ ] Configure Timber and baseline test dependencies.
- [ ] Add GitHub Actions workflow for wrapper validation, `:app:check`, `:app:assembleDebug` and APK artifact upload.
- [ ] Verify a fresh checkout builds without relying on globally installed Gradle or local-only files.

**M0 exit criteria:** GitHub Actions completes successfully from a fresh checkout and publishes an installable debug APK artifact. No missing resource, manifest, wrapper or dependency-injection errors.

## M1 — Real gallery on the phone

**Status: Not started**

- [ ] Implement MediaStore discovery for real device images; do not use drawable/sample media as gallery content.
- [ ] Store real `content://` URIs and stable MediaStore IDs.
- [ ] Add Room entities, DAO and database with one-shot reads, observation Flows and indices for date/hash/tag lookups.
- [ ] Export Room schemas and establish a non-destructive, versioned migration policy.
- [ ] Use persisted epoch-millisecond `Long` timestamps.
- [ ] Add repository interface/implementation and `ObserveMediaUseCase`; feature/UI code must not depend on concrete data classes.
- [ ] Build a Compose gallery grid with Coil 3 and a configured `SingletonImageLoader`.
- [ ] Add Photo Picker import using `PickMultipleVisualMedia` as the no-permission path.
- [ ] Add full-library permission handling for API 33 and API 34+, including `READ_MEDIA_VISUAL_USER_SELECTED` and partial-access states.
- [ ] Handle permission denial, revoked access, empty libraries and inaccessible URIs without crashing.
- [ ] Add favourite toggle and persist its state in Room.
- [ ] Add loading, empty, permission-required and recoverable-error UI states.
- [ ] Add focused tests for media mapping, repository behavior, permission state mapping and favourite persistence.
- [ ] Verify on API 33, 34 and 36, including partial photo selection.
- [ ] Run a 1,000+ item scrolling/memory test on a real device or representative emulator.

**M1 exit criteria:**
- Real device photos display; no placeholder/drawable-backed gallery records.
- 1,000+ items scroll without stutter or OOM.
- Favourites survive force-stop and relaunch.
- API 33, 34 and 36 permission paths work; partial-access users still see selected media.
- Photo Picker imports continue working without broad library permission.

## M2 — Real deduplication, editing and export

**Status: Not started**

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

**M2 exit criteria:**
- Two renamed copies of the same bytes are flagged as exact duplicates.
- A recompressed 80%-quality copy is identified as similar, not exact.
- Edited output is visible in the system gallery app.
- Revert/export does not overwrite the original; reverting restores original pixels.

## M3 — Real cloud backup

**Status: Not started**

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

## M4 — Advanced features and production hardening

**Status: Not started; intentionally deferred**

- [ ] Add on-device ML Kit face detection and clearly label it as detection only.
- [ ] Obtain and license a real FaceNet/MobileFaceNet TFLite embedding model asset.
- [ ] Implement face crop/normalization, embeddings, cosine similarity and clustering only after the model is present and validated.
- [ ] Add manual person labels and cluster-centroid updates; do not infer a person's identity.
- [ ] Add Room FTS4 search across title, face tags, attire, environment and lighting.
- [ ] Add storage optimizer insights for large files, similar photos, screenshots and blurred photos.
- [ ] Add safe bulk actions with undo.
- [ ] Extract stable code into modules only when an actual seam and measurable benefit exist.
- [ ] Expand unit, integration, UI and screenshot tests.
- [ ] Enable and validate R8/minification and release configuration.
- [ ] Add localization/internationalization and accessibility review.
- [ ] Run final dependency, security, performance, memory and install/update checks.

**M4 exit criteria:** Each shipped feature has a real implementation, user-visible failure states and tests. Face grouping is not marked complete without the licensed model asset and measured clustering behavior.

## Cross-milestone quality gates

- [ ] Every new behavior has a focused test; bug fixes begin with a failing reproduction test.
- [ ] Use repository wrappers and documented build/test commands.
- [ ] No fake IDs, mock media, simulated backup, stub AI output or untested "success" progress.
- [ ] No destructive Room migration fallback.
- [ ] No credentials, signing keys or private media in Git or logs.
- [ ] Keep `README.md`, this roadmap and the project plan aligned with actual repository state.
- [ ] Push completed changes and verify the remote branch/commit before reporting completion.

## Change log

| Date | Change |
|---|---|
| 2026-11-19 | Established v2.1 milestone order; created legacy reference branch; added project plan. |

## Status vocabulary

- **Not started** — no implementation exists in the active Android branch.
- **In progress** — implementation is underway but exit criteria are not met.
- **Blocked** — progress depends on an identified missing input or decision.
- **Done with evidence** — source is committed, automated checks pass, and milestone acceptance criteria have been verified.
