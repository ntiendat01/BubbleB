# TASKS — sequential implementation milestones

**Instruction to agent:** Do not skip gates, do not check off tasks without running/observing acceptance. Record `PASS`, `FAIL`, `NOT_RUN` and `DEVICE_VERIFICATION_PENDING`. Max 500 physical lines per implementation/config/test file, per `AGENTS.md`.

## M0 — Project scaffold & environment (P0)
- [x] Create Android Kotlin + Compose project, theme, Hilt, navigation, Room/DataStore skeleton and test dependencies. — PASS (build)
- [x] Set compileSdk/targetSdk to verified stable versions compatible with local AGP/Kotlin; minSdk 26. — PASS (compileSdk 37, targetSdk 36, xem `docs/DEVELOPMENT.md`)
- [x] Add correct `INTERNET` permission and Accessibility service declaration/config; no extra sensitive permissions. — PASS (`ManifestAuditTest`, `aapt2 dump permissions`)
- [x] Build placeholder Dashboard/Onboarding and provider settings route. — PASS (Robolectric `DashboardContentTest`)
- [x] Document development setup, Gradle versions and device API info. — PASS (`docs/DEVELOPMENT.md`; device API: DEVICE_VERIFICATION_PENDING)
- [x] Run assembleDebug, unit tests; check line limits and no secrets. — PASS (`scripts/audit.sh`)
**Gate:** app launches; Settings screen reachable; no unapproved permissions. Do not claim service working yet.
**Gate status (M0):** no unapproved permissions — PASS; Settings screen reachable — PASS (Robolectric/JVM); app launches on device/emulator — NOT_RUN / DEVICE_VERIFICATION_PENDING (emulator không chạy được trên máy build).

## M1 — Accessibility service + draggable bubble proof-of-concept (P0)
- [ ] Implement service enable/disable status observer; self lifecycle cleanup.
- [ ] Implement `OverlayController` with WindowManager and `TYPE_ACCESSIBILITY_OVERLAY`.
- [ ] ComposeView + lifecycle tree owners; bubble dragging and edge snap, safe insets and positioning persistence.
- [ ] Detect foreground package/window enough to hide bubble in excluded/lock contexts without reading text automatically.
- [ ] Handle close/toggle, screen rotation, app switching, IME visibility, service revocation.
- [ ] Test on sample app/emulator then OnePlus; document OEM behavior.
**Gate:** no crashes with 20 service toggles, 20 overlay attach/detach cycles; bubble positioned and removable; service disabled clears overlay. ADB/device gate can be PENDING if hardware unavailable.

## M2 — Screen snapshot + privacy guard (P0)
- [ ] Implement bounded Accessibility tree traversal and immutable structured DTOs.
- [ ] Collect only on tap; filter password/invisible/own/excluded/unknown package and sensitive window states.
- [ ] Implement redaction rules with unit tests and redacted preview; no prompt logging.
- [ ] Add review/consent flow (even before LLM exists); ensure no network request on tap.
- [ ] Add mock screen-reader fixtures for chat, long doc, empty tree, missing focused input, protected view and complex hierarchy.
**Gate:** sensitive data not shown in preview where recognizably redactable; empty/excluded states fail closed; 500-node/depth/time caps enforced.

## M3 — LLM integration + 3 read-only actions (P0)
- [ ] Implement HTTPS provider URL/credential handling with encrypted secret store, no plaintext key in DataStore.
- [ ] Connection test with synthetic text only and configured model.
- [ ] OkHttp Chat Completions request path + SSE streaming decoder; model compatibility handling.
- [ ] Implement Summarize/Explain/Translate prompts; preview + explicit Send confirmation.
- [ ] Cancellation, timeout and safe errors 401/403/429/5xx/partial event stream.
- [ ] MockWebServer tests including fragmented `data:` payload and `[DONE]`.
**Gate:** no network call before Send, no leaking prompt/key via logs, loading cancel stops request, unit tests green.

## M4 — Reply + safe insertion (P0)
- [ ] Define Reply JSON schema (three labeled texts); robust validation and text-only fallback.
- [ ] Implement `InputSnapshot` and `TextInserter` reacquiring input right before action.
- [ ] Default safe behavior: empty input + supported `ACTION_SET_TEXT`; nonempty drafts Copy only unless explicitly confirmed safe Replace.
- [ ] Abort on app/window/focus/draft change, password/unknown target; no automated Send action.
- [ ] Test mock input editor and real Zalo/Gmail where practical. Never assert universal compatibility.
**Gate:** existing drafts preserved, stale targets never modified, Copy always works.

## M5 — Persona, app profiles + Share fallback (P0)
- [ ] Room tables for AppProfile and Persona; defaults suggestions per app but let user customize.
- [ ] Excluded app options and package identification under Android package visibility constraints.
- [ ] Add `ACTION_SEND` / `text/plain` share intake; preview/consent same as accessibility flow.
- [ ] Share workflow works with accessibility disabled. Tests for missing/wrong MIME, huge text, cancellation.
**Gate:** per-app prompt style applies; excluded app never captures; Shared text never persisted.

## M6 — Hardening & MVP acceptance (P0)
- [ ] Screen lock, service restart/revoke, OEM process kill, orientation, IME, lifecycle and rapid taps.
- [ ] Battery and memory check on OnePlus; test 24 hours normal use (device evidence, not test promise).
- [ ] Audit Android manifest permissions, network behavior, key storage, text persistence, logs and error body sanitization.
- [ ] Execute test matrix in `TEST_PLAN.md`, run Gradle build/unit/lint; verify file line counts.
- [ ] Add in-app user help, limitations, clean README and release/debug reproducibility notes.
**Gate:** all P0 acceptance tests pass or formally blocked with rationale; no security P0 waiver; manual device evidence required to declare OnePlus verified.

## Deferred M7 — v0.3 (do NOT implement during v0.2)
- [ ] Smart Context Detection action ranking.
- [ ] Custom AI actions/prompt templates.
- [ ] Multiple personas per app, quick persona switching.
- [ ] Optional vision/OCR with new permission/privacy review.
- [ ] Cost dashboard or provider routing based on pricing.

## Required completion report template for every milestone
```text
Milestone: Mx
Modified files: ...
Implemented: ...
Build: PASS/FAIL/NOT_RUN + command + relevant output
Unit tests: PASS/FAIL/NOT_RUN + command
Instrumented/device tests: PASS/FAIL/NOT_RUN/PENDING + device/version
Line-count audit: PASS/FAIL + highest implementation file line count
Security/privacy checks: ...
Known limitations: ...
Next milestone prerequisites: ...
```
