# DECISIONS — architectural decision records

## ADR-001: Accessibility overlay instead of general overlay — accepted
A single AccessibilityService creates `TYPE_ACCESSIBILITY_OVERLAY`, avoiding `SYSTEM_ALERT_WINDOW` for MVP. Requires voluntary accessibility enable and transparent disclosure. OEM persistence is not guaranteed.

## ADR-002: No screenshot/vision in v0.2 — accepted
Use user-triggered accessible tree text only; unavailable apps show informative fallback. Never attempt to circumvent secured views. Vision/OCR deferred to v0.3 with separate privacy review.

## ADR-003: Preview/consent is mandatory before every AI request — accepted
In v0.1 only tap and action selection existed. v0.2 adds redacted preview and explicit `Gửi đến AI`. This increases friction but prevents accidental transmission of sensitive screen content.

## ADR-004: Safe text insertion is narrower than v0.1 — accepted
`ACTION_SET_TEXT` overwrites field, not text insertion at caret. Empty-field insertion may be supported, nonempty draft defaults Copy; user-approved Replace is a separate destructive action with verification. No automatic Send.

## ADR-005: Keep Compose overlay but isolate lifecycle details — accepted / needs proof
`ComposeView` in service uses dedicated lifecycle/view tree owner setup; M1 must validate on emulator and OnePlus. If too unstable, only the overlay surface may switch to a simple native `View`, documenting divergence; main app remains Compose.

## ADR-006: Support only configurable Chat Completions provider in MVP — accepted
No guarantee every DeepSeek/Qwen/GLM-compatible endpoint supports same SSE response/usage fields. Validate via synthetic test per provider and document fallback.

## ADR-007: Keystore-backed encrypted key storage — accepted
Plaintext DataStore for secrets prohibited. Local BYOK personal use; backend proxy considered if distributed publicly.

## ADR-008: minSdk 26 — support policy, not overlay API requirement
`TYPE_ACCESSIBILITY_OVERLAY` predates API 26. Retain 26 for simplified support; agent should not claim otherwise.

## ADR-009: App-specific persona + limited package visibility — accepted
Users can map persona to observed apps. No blanket `QUERY_ALL_PACKAGES` request in MVP; prefer package seen by service and targeted package queries where useful.

## ADR-010: 500-line cap — non-negotiable
Human-authored implementation/config/test files max 500 physical lines; split modules earlier; no exceptions without user's explicit change.

## Open device-validation questions
- Accessibility service overlay remains usable across OxygenOS 16 lock/unlock, memory pressure and background cleanup? Track actual behavior; do not assume.
- Do Zalo/Gmail expose text and writable input nodes reliably on this OS/build? Per-app observations only; fail gracefully otherwise.
- Can native overlay remain nonfocusable and still keep workable panel taps, IME placement, outside tap? Prove at M1.
- Which provider/model/endpoint exactly is available under the user's $10 balance? User enters values; no vendor price or SLA hardcoded.
