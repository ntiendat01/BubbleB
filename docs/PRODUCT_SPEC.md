# PRODUCT_SPEC — BubbleB v0.2

**Version:** 0.2 · **Date:** 2026-11-16 · **Product:** Android floating AI utility · **Audience:** personal Android user · **Status:** implementation-ready specification, requires device validation

## 1. Problem / value

While using Android apps (web, chat, docs), user wants context-aware support without switching manually to a separate chatbot: extract accessible screen **text** on tap, choose an AI action, see result in nonintrusive overlay, optionally copy or safely insert a suggested reply. Personas per app are differentiator. This is not a general-purpose autonomous phone agent.

## 2. Principles
1. User controls **when** screen content is collected and **when** remote LLM gets a request.
2. Never silently send generated messages or overwrite existing text.
3. Keep text in memory only and prefer minimum necessary context; excluded apps fail closed.
4. Functional without OEM-specific permissions wherever possible; gracefully degrade.
5. API budget small; no background tokens, automatic querying or OCR/vision in v0.2.

## 3. Target platform
- Android native: Kotlin + Jetpack Compose, Material 3; minimum API 26 for support policy, compile/target stable available in build environment and required by platform/publishing rules if later published.
- Target device: OnePlus Ace 5, OxygenOS 16; verify physical hardware and OS/API via `adb shell getprop ro.build.version.sdk`.
- MVP distribution: sideload to personal device. Google Play release deliberately deferred.
- Languages: Vietnamese UI by default; English translation support as action; proper UTF-8/Unicode handling.

## 4. Scope

### P0 — MVP v0.2
- Onboarding: service purpose, data flow/remote transmission notice, explicit consent, status and Settings deep link; on/off toggle.
- Floating bubble: draggable, edge snap, restore last safe position; no touch blocking; window/layout reflow.
- Capture text from foreground supported accessibility tree *only after tap*. Bound traversal, skip passwords, self UI, excluded apps; transient structured context; no screenshot.
- Actions: `REPLY` (exactly three suggestions where conversation input/context is plausible), `SUMMARIZE`, `EXPLAIN`, `TRANSLATE` (Vietnamese/English target chosen from small list).
- Panel states: actions, captured content preview/redactions, confirm remote send, loading/stream, result, error, unavailable.
- Actions `Copy`, `Retry` (requires confirmation of new request), `Insert` only where safely supported, `Close`.
- User-specified OpenAI-style Chat Completions provider `baseUrl`, `model`, `apiKey`; connection test; usage estimate; no background auto-calls.
- Per-app profile: enabled/excluded, one selected persona, language, extra instruction; defaults only suggestions based on identified package, user can override.
- Manual share-to-BubbleB for `text/plain` and shared URLs as fallback (share flow opens app/sheet, no assumption overlay active).
- Blocked screen states; redaction and explicit payload preview. No disk history and no unapproved content submission.

### P1 — later v0.3, **not MVP**
- Smart Context Detection, custom AI actions/templates, multiple personas per app, quick switch persona, advanced analytics/token budget and provider-based routing.
- Better structured thread understanding or recognition from image; opt-in OCR/vision with separate authorization.

### Out of scope
- Auto-sending chats, AI autonomous clicks, voice activation, monitoring entire screen continuously, background message classification, full screenshot, OCR, cloud account/sync, Google Play publication, root privileges or accessibility workarounds for protected UIs.

## 5. Core user journeys

### J1 — First-run setup
1. User opens app and reviews explicit disclosure: Android Accessibility exposes text from apps; a selected portion can be transmitted to chosen provider **only after confirmation**.
2. Enable service from Android Settings; app checks actual connection; no fake success. If restricted settings gate applies, explain Android-supplied guidance without attempting bypass.
3. Configure provider (HTTPS base URL, model, API key), test connection with **non-screen test payload**.
4. User can enable/disable bubble and edit exclusions.
**AC:** If accessibility not enabled, bubble never claims ready. User can proceed with Android Share fallback independent of service when implemented.

### J2 — Read screen, AI summarize
1. User sees supported app; taps bubble. Service captures a **snapshot** before opening panel.
2. If app excluded/secure/no text, panel shows explicit reason and Share fallback where possible.
3. Panel shows target package/app, context preview, redaction indicator, estimated characters, chosen provider. User may cancel.
4. User selects Summarize; preview remains; taps **Send to AI**; only then start request. Show streaming result and Copy.
**AC:** no upload from tap alone; request cancelled when panel closes; no text persisted to disk.

### J3 — Reply and safe insert
1. User in chat app taps bubble; service captures visible content and input-target metadata (ephemeral).
2. User chooses Reply and reviews payload; AI returns 3 labeled options, optionally rendered after complete JSON parse.
3. Tap a response then `Copy` or `Insert` (Insert shown only if a writable target was detected and user confirms).
4. Before insertion, reacquire active window/focused input; compare package and current input vs captured original; do not re-use stale node.
5. If changed/unsupported/ambiguous, show `Không thể chèn an toàn` and offer Copy. App **never** taps Send.
**AC:** no existing draft lost. In case of uncertainty, copy/manual paste instead of destructive action.

### J4 — Share selected text
1. From any app exposing Android Sharesheet, user selects text and shares to BubbleB.
2. `ACTION_SEND`, MIME `text/plain`; received text is previewed and processed through same privacy and send confirmation workflow.
3. Unsupported MIME, huge payload, empty text → graceful error; do not import attachments or persist shared text.
**AC:** user can summarize selected text without Accessibility service enabled.

## 6. Screen reader detailed behavior
- Read `rootInActiveWindow` only on user tap; capture package from root where trustworthy, not event cache alone.
- Skip app's own package, password nodes, non-visible nodes and excluded apps. Always treat null tree, restricted UI and unknown package as **no capture**.
- Bounded traversal: e.g. max 500 nodes, depth 20, 300 ms processing budget (tune on device); no unbounded recursion. Node text max 500 Unicode code points, overall sanitized payload max 6,000 code points (Reply 3,000). These values configurable constants and subject to device testing.
- Store sequence index, role (`TEXT`,`INPUT`,`LABEL`,`UNKNOWN`), bounds and truncated text in ephemeral `ScreenElement`. Sort heuristically by vertical/horizontal bounds, preserve original index for fallback; **do not claim this reconstructs full conversation or sender identity**.
- Strip duplicate consecutive elements conservatively; preserve short meaningful lines (numbers, dates). `contentDescription` only if necessary; it may contain sensitive accessibility hints, treat as sensitive.
- Focus snapshot has password flag, current editable text if allowed, selection (when exposed), package and capture timestamp; NEVER persist node references.
- Explicitly exclude sensitive input types/passwords and OTP pattern; redact strings on **screen preview too**, not only HTTP payload. Redaction best-effort; unmasked data may remain → mandatory user review.
- No automatic scanning triggered by `onAccessibilityEvent`, which is only for foreground/excluded state handling.

## 7. Action behavior and outputs
- `SUMMARIZE`: concise 3–5 bullets; maintain numbers/uncertainty; no hallucinated missing context.
- `EXPLAIN`: clarify visible concepts, acknowledge insufficient context.
- `TRANSLATE`: choose Vietnamese/English; preserve format/entities; do not invent unseen text.
- `REPLY`: JSON `replies` length exactly 3, labels `Ngắn gọn`, `Thân thiện`, `Trang trọng`, with non-empty text. Validate JSON and display Copy-only raw text fallback **without insertion** when invalid.
- If context ambiguous, explain it is a partial screen snapshot and ask user to refine rather than assume what other person said.
- Treat screen content as untrusted. No model-generated system prompt or tool/action execution.

## 8. Panel and overlay requirements
- Bubble nominal 48 dp, accessible 48 dp touch target, draggable and edge-snapping within safe window insets.
- Simple, non-focusable accessibility overlay. Avoid `FLAG_LAYOUT_NO_LIMITS` unless strictly tested; account for navigation bar/cutout/IME.
- Panel ~90% width with height bounded by available area, scrollable text, visible close button, outside tap behavior tested.
- Panel and bubble may be separate `WindowManager` views, mutually exclusive; check focus handoff when keyboard open.
- On rotation, fold/window changes, screen lock, service disabled/destroyed or sensitive app entry, dismiss/cancel and clear text. Hiding bubble on lock is a policy, not a guarantee that lock state can always be inferred instantly.
- UI labels state no automatic send, show provider and approximate chars before upload.

## 9. Provider / costs
- Provider fields: nickname, full HTTPS base URL normalized without duplicated path, model id, encrypted API key, optional max output tokens.
- Compatibility target: OpenAI-style `/chat/completions`; actual model/provider behavior needs connection test. SSE parser recognizes `data:` lines, `[DONE]`, split chunks and optional usage; safe fallback when usage absent.
- Text-only in v0.2, no tools/function calling required. Distinct non-stream reply path and streaming summarize/explain/translate; fallback non-stream if provider doesn't support SSE (explicit message).
- Configurable per-request input char caps and output token cap. Show requests and token/cost estimates **without logging content**, pricing manually entered; no claim exact costs if API lacks price data.
- Network failures, 401/403, 429, 5xx, timeout, invalid JSON, partial stream, cancellation: mapped to friendly UI. No automatic replay of failed send that could duplicate charges without warning.

## 10. Acceptance criteria — MVP exit gate
- All P0 user journeys work on a test fixture app and Android Share (when service disabled) according to test plan.
- No app crash, no UI stuck after change/rotate/lock; overlay removed and jobs cancelled when service off.
- No background LLM calls; zero request sent until user confirms payload.
- No persistent screen data or API key plaintext; no sensitive data in logs.
- `ACTION_SET_TEXT` never causes a silent overwrite of drafts and sending is always manual.
- Manual real-device OnePlus Ace 5 test: **24 h normal usage**, record reproducible results; no guarantee service is immortal.
- Performance target (non-blocking): median <3s from **Send to AI** to first token under documented network/provider conditions. Report actual numbers, not fabricated pass.
- Every P0 testcase in `TEST_PLAN.md` passes or is explicitly justified as unsupported with accepted fallback; do not silently waive security P0.
