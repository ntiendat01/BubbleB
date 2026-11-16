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
- [ ] Implement service enable/disable status observer; self lifecycle cleanup. — IMPLEMENTED; unit PASS (`EnabledServicesParserTest`, `DashboardStateMapperTest`, `BubbleCoordinatorTest`); DEVICE_VERIFICATION_PENDING
- [ ] Implement `OverlayController` with WindowManager and `TYPE_ACCESSIBILITY_OVERLAY`. — IMPLEMENTED; Robolectric PASS (`OverlayControllerTest`); DEVICE_VERIFICATION_PENDING
- [ ] ComposeView + lifecycle tree owners; bubble dragging and edge snap, safe insets and positioning persistence. — IMPLEMENTED; unit/Robolectric PASS (`BubbleLayoutCalculatorTest`, `BubbleDragTrackerTest`, `OverlayControllerTest`, `DataStoreSettingsRepositoryTest`); DEVICE_VERIFICATION_PENDING (ADR-005 chưa được chứng minh trên máy thật)
- [ ] Detect foreground package/window enough to hide bubble in excluded/lock contexts without reading text automatically. — IMPLEMENTED; unit PASS (`BubbleVisibilityPolicyTest`, `BubbleCoordinatorTest`); DEVICE_VERIFICATION_PENDING
- [ ] Handle close/toggle, screen rotation, app switching, IME visibility, service revocation. — IMPLEMENTED (IME best-effort); logic unit PASS; DEVICE_VERIFICATION_PENDING
- [ ] Test on sample app/emulator then OnePlus; document OEM behavior. — NOT_RUN (emulator không khởi động được trên máy build do lỗi KVM; chưa có thiết bị OnePlus). Script sẵn: `scripts/m1_device_check.sh`
**Gate:** no crashes with 20 service toggles, 20 overlay attach/detach cycles; bubble positioned and removable; service disabled clears overlay. ADB/device gate can be PENDING if hardware unavailable.
**Gate status (M1):** JVM: 20 lần bật/tắt service (coordinator) PASS, 20 chu kỳ attach/detach overlay (Robolectric) PASS, không nhân đôi cửa sổ PASS, service ngắt → ẩn bong bóng PASS. Device/ADB gate: PENDING (`DEVICE_VERIFICATION_PENDING`).

## M2 — Screen snapshot + privacy guard (P0)
- [ ] Implement bounded Accessibility tree traversal and immutable structured DTOs. — IMPLEMENTED; unit PASS (`ScreenTreeReaderTest`); Android adapter (`AccessibilityScreenCapture`) DEVICE_VERIFICATION_PENDING
- [ ] Collect only on tap; filter password/invisible/own/excluded/unknown package and sensitive window states. — IMPLEMENTED; unit PASS (`CapturePreflightTest`, `ScreenTreeReaderTest`, `PanelCoordinatorTest`); DEVICE_VERIFICATION_PENDING
- [ ] Implement redaction rules with unit tests and redacted preview; no prompt logging. — IMPLEMENTED; unit PASS (`RedactionTest`, `PayloadPreviewBuilderTest`, `NoNetworkAuditTest` kiểm tra không log trong code capture/privacy)
- [ ] Add review/consent flow (even before LLM exists); ensure no network request on tap. — IMPLEMENTED; unit/Robolectric PASS (`PanelCoordinatorTest`, `PanelContentTest`, `PanelOverlayControllerTest`, `NoNetworkAuditTest`); nút `Gửi đến AI` luôn bị vô hiệu ở M2 (chưa có provider); DEVICE_VERIFICATION_PENDING
- [ ] Add mock screen-reader fixtures for chat, long doc, empty tree, missing focused input, protected view and complex hierarchy. — IMPLEMENTED (`ScreenFixtures`: chat, tài liệu dài, cây rỗng, không có ô nhập focus, view được bảo vệ, cây rộng >500 node, chuỗi sâu >20, trường mật khẩu, đồng hồ giả cho time budget); PASS trên JVM. Fixture **trên thiết bị** (app mẫu TextView/EditText) NOT_RUN
**Gate:** sensitive data not shown in preview where recognizably redactable; empty/excluded states fail closed; 500-node/depth/time caps enforced.
**Kết quả trên máy thật (người dùng tự báo, 2026-11-16):** bản `0.2.1-m2` (commit `b75d3db`) — chạm bong bóng mở panel và kéo thả bong bóng hoạt động trên điện thoại của người dùng. Bản `0.2.0-m2` trước đó: chạm và kéo không phản hồi ở mọi app (đã sửa, ADR-028). Model/OS máy chưa được người dùng xác nhận; chưa có log/ảnh chụp; các mục checklist thiết bị khác (xoay, bàn phím, khóa màn hình, redaction trên app thật, 20 chu kỳ bật/tắt) vẫn `DEVICE_VERIFICATION_PENDING`.
**Gate status (M2):** JVM PASS — email/SĐT VN/thẻ Luhn/OTP/token/credential bị che trong preview; không che nhầm ngày/số tiền/giờ/số phòng mẫu; cây rỗng/protected/null root → `Không đọc được…`; app loại trừ/app của chính BubbleB/khóa màn hình → chặn **trước khi đọc text**; mật khẩu → chặn cả màn hình; giới hạn 500 node / độ sâu 20 / 300 ms / 500 ký tự mỗi node / 6000 ký tự tổng (Reply 3000) đều có test. Device gate: PENDING (`DEVICE_VERIFICATION_PENDING`). **Gate thiết bị M1 cũng vẫn PENDING** — M2 chưa được chạy trên emulator/OnePlus.

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
