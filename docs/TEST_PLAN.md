# TEST_PLAN — BubbleB v0.2

## Test environment
- Android emulator/API corresponding to supported range (minSdk 26 + current emulator stable).
- Real OnePlus Ace 5 / OxygenOS 16 (validate API number), with sample non-sensitive content; optional second generic Android device.
- Test fixture Android screen: standard TextView/EditText, password field, long nested hierarchy, editable draft, fake chat UI. Never use real private messages/credentials.
- MockWebServer/fixture provider. Use a separate synthetic API test to avoid accidental charges.

## Functional test cases
| ID | Priority | Scenario | Expected |
|---|---|---|---|
| TC01 | P0 | Grant Accessibility first time | Consent recorded; service state true only when OS says enabled; bubble appears if toggle on |
| TC02 | P0 | Drag bubble middle and release | Snaps to nearer safe edge; restores within visible area |
| TC03 | P0 | Tap bubble on mock chat | One bounded capture; shows 4 actions when reply eligible |
| TC04 | P0 | Reply action with valid JSON | Exactly 3 labels/text; no automatic send |
| TC05 | P0 | Reply into empty editable field | Explicit confirmation, supported insertion; displayed text matches, no send |
| TC06 | P0 | Nonempty draft | Default Copy or explicit Replace confirmation only; no silent overwrite |
| TC07 | P0 | Password/sensitive screen | No sensitive content in captured payload, preview or logs; fail-closed if unsure |
| TC08 | P0 | Offline / bad key | Friendly error; no crash; retry needs confirmation |
| TC09 | P0 | Close panel during SSE | Coroutine/network call cancelled; content cleared |
| TC10 | P0 | App excluded | Bubble hidden/disabled; no capture/upload |
| TC11 | P1 | Lock device 30 min and unlock | State recovers gracefully; if service killed, show re-enable guidance |
| TC12 | P0 | Prompt injection string in fake screen | No instructions executed; only selected action rendered |
| TC13 | P0 | Rotate / IME open / display inset | Bubble/panel remains on-screen and touchable |
| TC14 | P0 | App switch while loading | Request cancelled; stale result never appears in new app |
| TC15 | P0 | Focused input disappears before Insert | Abort and offer Copy |
| TC16 | P0 | Draft modified after capture | Abort or explicit renewed confirmation; original draft preserved |
| TC17 | P0 | Unsupported canvas/secure/custom UI | ScreenUnavailable without bypass; Share guidance |
| TC18 | P0 | SSE split into arbitrary chunks | Parser reconstructs valid events; handles `[DONE]` |
| TC19 | P0 | Service revoked/rebound | WindowManager views removed; no old context/job; safe reattach on re-enable |
| TC20 | P0 | Screen locked during pending request | Clear/cancel based on lock policy, no stale insert |
| TC21 | P0 | Enter sensitive/excluded app while panel open | Dismiss and purge memory snapshot immediately |
| TC22 | P0 | Tap without selecting action | Zero network requests |
| TC23 | P0 | Choose action but cancel preview | Zero network requests |
| TC24 | P0 | Same action with 2 providers | Both tested or provider-specific incompatibility disclosed; no claim universal compatibility |
| TC25 | P0 | Invalid provider URL `http://` | Reject; no cleartext network |
| TC26 | P0 | Store key, restart app, inspect local files | Secret encrypted, no plaintext DataStore/log output |
| TC27 | P0 | Android Share `text/plain` with service off | Preview and action available without accessibility |
| TC28 | P0 | Share wrong MIME / > max chars | Graceful unsupported/truncation decision; no hidden attachment reads |
| TC29 | P0 | Hundreds of accessibility nodes | traversal caps respected, UI responsive |
| TC30 | P0 | App name/package unavailable | Fail closed and do not upload |
| TC31 | P0 | Malformed Reply JSON | Raw text copy only, no Insert eligibility |
| TC32 | P0 | 401/403/429/500 & injected error body | Sanitized user message; no sensitive body in logs |
| TC33 | P0 | Rapid tap 10 times | At most one capture/active request; no duplicate overlay |
| TC34 | P0 | API key Keystore invalidation | Safe re-entry prompt, no plaintext fallback |
| TC35 | P0 | System font scale and TalkBack | Critical actions available, controls correctly labeled |

## Automated tests (minimum)
- `ScreenReaderFilterTest`: skip password/invisible/self/unknown, bounds, dedup, cap, truncation.
- `RedactionTest`: card-like, OTP context, Bearer tokens, email/phone; false positives for normal dates/amounts.
- `PromptBuilderTest`: persona templating, untrusted delimiter isolation, action specifics.
- `ReplyParserTest`: exactly 3, invalid JSON, partial content, giant strings, malicious control characters.
- `ProviderUrlTest`: HTTPS only, slash normalization, reject embedded credentials.
- `SseParserTest`: multiple fragmented events, comments, `[DONE]`, malformed data, usage available/unavailable.
- `ControllerTest`: capture->preview->confirm->request, close cancellation, app switch, duplicate action prevention.
- `InsertPolicyTest`: empty input allowed when supported, draft changed -> abort, always never send.
- `ProfileDaoTest`: persona migration/defaults/exclusions persistence.
- `ShareIntakeTest`: MIME, null, max length and display consent.

## Manual real-device checklist
- [ ] Verify OnePlus model, OxygenOS version, API SDK and accessibility permission availability.
- [ ] Draggable bubble inside screen bounds with gesture nav, notification shade, keyboard and orientation.
- [ ] Zalo, Gmail, Chrome and a test fixture — mark each `SUPPORTED`, `PARTIAL`, `UNSUPPORTED` with screenshots **of synthetic data only**.
- [ ] Simulate permission revoke, background cleanup, overnight lock/unlock and app switch.
- [ ] Test Wi-Fi/mobile/hotspot and provider timeout; annotate latencies rather than assuming SLA.
- [ ] Run normal use for 24 h; collect crash count and memory/battery observations, no PII.

## Release gate
All P0 functional+security tests PASS, or a documented platform-unavailable behavior with fail-safe fallback and explicit acceptance. **Any data leak / silent overwrite / unauthorized remote request is release blocking.** Hardware-dependent cases are not PASS until physically run.
