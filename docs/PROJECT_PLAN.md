# Aura AI Gallery — Project Plan

**Plan baseline:** 19 November 2026  
**Source of truth:** `Aura_AI_Gallery_Spec_v2.1_Audit_Reconciliation.html`  
**Supporting product brief:** `Aura_AI_Gallery_Rebuild_Specification.html`  
**Implementation target:** Android, package/application ID `ai.sakana.aura.gallery`

## 1. Product direction

Aura AI Gallery is a phone-first Android gallery. The Android app is the product and the authoritative owner of the media catalogue. It must display real photos and videos from the device, preserve user actions, and make editing, duplicate detection and backup verifiably real.

The existing web/AI Studio implementation is retained as a read-only reference, not used as the Android foundation. It has a separate data model and web runtime; carrying it into the Android rebuild would preserve the wrong assumptions. The `legacy-reference/ai-studio-web` branch records that source snapshot.

There must be one source of truth for each device photo. Do not build a second Express-backed gallery or leave an unauthenticated endpoint serving a Gemini key. Any previously exposed Gemini credential must be rotated and must never be shipped in the app or repository.

## 2. Build strategy

The v2.1 reconciliation supersedes the earlier phase ordering. Real media ingestion is the first product milestone, not a late integration task.

- Start from a clean Android source tree on `android/rebuild-m1`; do not copy the old web data model, resource-name identifiers, simulated cloud loop, fake face grouping, or destructive database migration logic.
- Keep a single Gradle `:app` module through M1–M3. Extract modules only in M4, after the code has stable seams and tests.
- Build installable increments. Every milestone has an explicit acceptance gate and must pass its build/tests before the next milestone begins.
- Use tests to prove behavior. New logic follows red → green → refactor; UI changes also receive runtime/device-size verification.
- Keep generated APKs and test results as CI artifacts. The Git repository remains the source of truth for source code and project documentation.

## 3. Approved Android baseline

| Area | Decision |
|---|---|
| Application ID / namespace | `ai.sakana.aura.gallery` |
| Module layout through M3 | One `:app` module |
| Android SDK | compileSdk 36; targetSdk 36; minSdk 26 |
| Build toolchain | AGP 8.13.0, Gradle wrapper 8.13, JDK 17 |
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Dependency injection | Hilt 2.52 + KSP, isolated behind app-level wiring |
| Persistence | Room with exported schemas and explicit migrations |
| Image loading | Coil 3 with a configured `SingletonImageLoader`; add video thumbnail support when needed |
| Async/background work | Coroutines for foreground work; WorkManager only for durable background jobs (M3) |
| Logging | Timber |
| CI | GitHub Actions; use the committed wrapper, never generate it during CI |

Use stable dependency releases only; pin versions centrally in the version catalog. The selected Kotlin/Hilt/KSP combination is a deliberate conservative baseline from v2.1 and must be validated by the actual CI build. Do not silently upgrade one part of the Android Gradle toolchain independently.


### UI design-system baseline

The Android UI implementation follows the user-supplied Aura_AI_Gallery_UI_Design_System.html. Preserve the design's semantic colour rule (amber means user action; spectral means model inference), square image geometry, hairline contact-sheet grid, three bundled typefaces, tabular mono metadata and five screen destinations. When a feature is not built, show a truthful empty/planned state instead of the reference mockup's illustrative sample metrics.

### Verified implementation notes (19 November 2026)

- Coil is pinned at 3.0.4 with Kotlin 2.0.21. Coil 3.3.0 resolved to Kotlin 2.2 metadata and did not compile with the approved Kotlin 2.0.21 compiler.
- Hilt 2.52 continues to use KSP 2.0.21-1.0.28. Room 2.8.5 schema processing is routed through KAPT for now: Room's release KSP task failed with an AbstractMethodError in the Room schema serializer/classloader path under the pinned Kotlin compiler. KAPT successfully generates both Room implementation classes and the version 1 schema while Hilt stays on KSP.
- The Kotlin KAPT plugin warns that it falls back to language version 1.9 while generating Room stubs from Kotlin 2.0 sources. Current Room entity/DAO declarations use syntax supported by that stub path; re-evaluate this exception during the next Kotlin/KSP toolchain upgrade. Do not claim schema export is verified from KSP.
- The first integrated build passed :app:testDebugUnitTest, :app:check, :app:assembleDebug and :app:assembleRelease locally. The build host currently reports JDK 21; JDK 17 GitHub Actions validation and Android device/API-level runtime tests remain outstanding.

## 4. Architecture and data rules

Use a simple Clean Architecture/MVVM shape inside the single app module until M4:

- **Presentation:** Compose screens, ViewModels, immutable UI state and events.
- **Domain:** use cases, repository contracts and business rules.
- **Data:** MediaStore/Photo Picker sources, Room DAOs/database, repository implementations, file I/O and provider adapters.
- **Application wiring:** Hilt bindings, navigation, themes, permissions and worker configuration.

Keep interfaces small and behavior deep behind them. Features depend on domain contracts and models, never on concrete data implementations.

Data correctness rules:

1. Store real `content://` URIs and stable MediaStore IDs; never derive identity from drawable/resource names or filenames.
2. Use epoch-millisecond `Long` values for persisted timestamps and convert at presentation boundaries.
3. Use `AuraResult` for application results; do not introduce a type named `Result` that collides with Kotlin or WorkManager.
4. Provide one-shot DAO queries for suspend reads, plus indices for hash/date/tag lookups. Do not turn cold observation Flows into one-shot reads with `.first()`.
5. Export Room schemas and commit them. Add versioned migrations; never use `fallbackToDestructiveMigration` for production data.
6. Keep original media immutable. Edits are persisted adjustment values and rendered/exported as derived media.
7. Exact duplicate identity is SHA-256 over file bytes. Similarity uses a perceptual hash, Hamming distance and candidate bucketing (banded LSH); never group by exact perceptual-hash string equality.
8. ML Kit Face Detection only detects faces. Person grouping requires an actual licensed embedding model asset, normalization and similarity clustering. Until that model is supplied and validated, describe the capability as face detection, not identity recognition.

## 5. Media access and privacy

Prefer the Android Photo Picker for user-selected imports because it avoids broad library permission prompts. For a full-library gallery scan, correctly handle the platform permission model:

- API 26–32: legacy external-storage read permission where applicable.
- API 33: `READ_MEDIA_IMAGES` (and video permission only if the app scans videos).
- API 34+: `READ_MEDIA_IMAGES` plus `READ_MEDIA_VISUAL_USER_SELECTED`; partial access must remain a valid state.

Never assume that a permission grant means the app can see the entire library. No private media, tokens, API keys, OAuth refresh tokens or signing credentials may be written to logs or committed.

## 6. Milestones and release gates

The detailed task checklist and status live in [ROADMAP.md](../ROADMAP.md).

| Milestone | Scope | Exit gate |
|---|---|---|
| M0 — Android build foundation | Clean project, wrapper, manifest/resources, Hilt application, CI debug APK | Fresh CI build succeeds; APK artifact installs |
| M1 — Real gallery | MediaStore + Photo Picker, real URIs, Coil grid, ViewModel/repository, persistent favourites, permission/partial-access handling | 1,000+ items scroll without stutter/OOM; favourites survive force-stop; API 33/34/36 and partial-access flows work |
| M2 — Dedupe, editing and export | SHA-256, dHash + banded candidate search, clustering, non-destructive ColorMatrix edits and MediaStore export | Exact and recompressed copies are distinguished; edited output appears in system gallery; revert restores original pixels |
| M3 — Cloud backup | OAuth, real provider upload, per-item state machine, WorkManager constraints/retry/resume | Interrupted sync resumes without duplicate upload; offline queue drains on Wi-Fi; downloaded Drive file matches source bytes |
| M4 — Advanced features and hardening | Licensed face embeddings, FTS4 search, storage optimizer, module extraction, broader tests, R8 and i18n | Each feature has a real implementation and tests; modules are extracted only when stable |

No milestone is complete because a screen exists or a progress indicator moves. Completion requires its stated acceptance evidence.

## 7. CI, APK signing and delivery

- Commit the complete Gradle 8.13 wrapper and use it for every build.
- Add a GitHub Actions workflow for push and manual builds; run compile, unit tests and debug APK assembly, then upload the APK artifact.
- Do not use `gradle wrapper ... || true` in CI. A missing or invalid wrapper must fail visibly.
- Preserve install-over-install compatibility with a stable signing key, but keep the keystore and passwords out of Git. Store them as protected GitHub Actions secrets and fail clearly when signing is required but unavailable. Never use hard-coded fallback passwords.
- Keep versionCode monotonic for installable builds. Do not claim an APK is installable until artifact metadata and installation are verified.

## 8. Risks and explicit non-goals

| Risk | Mitigation |
|---|---|
| Android 14+ partial photo access | Photo Picker first; test full, partial and denied states |
| Large libraries exhaust memory or cause jank | Paging/lazy loading, bounded bitmap work, thumbnail sizing and a 1,000+ item acceptance run |
| Similarity hash misses recompressed copies | LSH candidate search plus Hamming-distance tests |
| Face detection mistaken for person recognition | No identity claims until the embedding model is supplied, licensed and validated |
| Cloud sync duplicates or loses state | Stable item IDs, idempotent upload strategy, Room state machine and WorkManager retry |
| Signing material leaks | Repository secrets only; no credentials in source or artifacts |

Out of scope for the first three milestones: a web gallery, a second backend-owned media catalogue, automatic identity naming without a real embedding model, and premature multi-module extraction.

## 9. Working agreement

1. Keep this plan and [ROADMAP.md](../ROADMAP.md) current in the same branch as implementation changes.
2. Before each milestone, inspect the current code and tests; do not assume old prototype behavior is correct.
3. Add regression tests with each behavior change and run the repository's documented checks.
4. Push completed changes to the relevant Git branch and verify the resulting GitHub commit before reporting them as pushed.
5. Report milestone status as **not started**, **in progress**, **blocked**, or **done with evidence**. Do not mark unverified work complete.

---
This plan implements the corrected priority and engineering constraints from Spec v2.1. Where the older rebuild brief conflicts with v2.1 (notably build order, early multi-module extraction, permissions and face recognition), v2.1 governs.
