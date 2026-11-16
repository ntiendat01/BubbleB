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

## M4 — trạng thái và kiểm thử bổ sung (2026-11-16)
**Cập nhật sau M3:** tests M4 đã chạy PASS trong tổng 238 JVM tests, build/lint PASS. Luồng M3 đã nối; thiết bị thật/provider thật vẫn chưa được chạy. Không coi JVM/Robolectric là bằng chứng AndroidKeyStore/overlay/chèn hoạt động trên OnePlus. Checklist thiết bị/reproduction nằm ở `DEVELOPMENT.md` §9–10.

| Test source | Các trường hợp phải chứng minh khi chạy |
|---|---|
| `ReplyJsonParserTest` | Đúng ba nhãn/text; sai schema/type/length/nesting → Copy only; bỏ control/bidi, giữ Unicode; output rỗng không có thao tác. |
| `InputSnapshotTest` | DTO định danh/thời gian, giới hạn draft, text không xác định, thiếu ID, nhiều ô focus. |
| `InsertionPolicyTest` | Ô trống đúng mục tiêu mới được ghi; đổi package/window/ID/cursor/focus; nháp/whitespace/password; thiếu dữ liệu; hết hạn/đồng hồ lùi. |
| `GuardedTextInserterTest` | Lấy lại trước ghi và đọc hậu điều kiện; khóa/đổi draft không ghi; false/exception không tuyên bố thành công/không retry; luôn giải phóng phiên. |
| `ReplySessionTest` | Tap chưa ghi; Hủy không ghi; xác nhận tiêu thụ một lần; lỗi chèn không tự Copy; fallback/draft Copy only; clipboard lỗi không lộ chi tiết. |
| `ReplyFlowTest` | Preview + provider readiness + Send; request ID cũ bị bỏ; đổi editor/window/app xóa context; consent/lock/exclusion chặn ghi; hủy khi policy read đang suspend không Copy muộn. |
| `AndroidReplyAdaptersTest` (Robolectric API 26/35) | Clipboard chỉ đổi khi gọi port, gắn sensitive flag; password input variations chặn dù cờ accessibility password sai. |
| `ReplyResultContentTest` (Robolectric Compose) | Ba lựa chọn, xác nhận Copy/chèn/Hủy; malformed JSON chỉ hiển thị text và Copy. |

Chạy đầy đủ `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint` khi có toolchain. Kiểm thử thật fixture/Zalo/Gmail/OnePlus, hành vi clipboard trong overlay không focus và tính ổn định uniqueId vẫn **DEVICE_VERIFICATION_PENDING**; nếu không hỗ trợ thì ghi rõ Copy/manual paste hoặc UNSUPPORTED, không tự mở rộng quyền.

## M3 — kết quả kiểm thử (2026-11-16)
- PASS: toàn bộ 238 JVM tests, không fail/error/skip; gồm các test cũ và M4.
- PASS: `ProviderUrlTest` (HTTPS/path/credentials/query/private IPv4/IPv6/mixed DNS, client không redirect/retry/cache/logger), `StoredProviderRepositoryTest` (saved ưu tiên debug, origin binding, rotation/revision, không key plaintext trong DataStore, xóa tắt fallback).
- PASS: `EncryptedSecretStoreTest` dùng AES-GCM JCE trên Robolectric API 26/35 cho ciphertext/fresh IV/AAD/invalidation/re-entry/transient error. Đây chưa phải kiểm chứng AndroidKeyStore thật.
- PASS: `OkHttpLlmGatewayTest` dùng TLS MockWebServer, model/path/auth đúng, HTTP/1.1 chunks cỡ 1 byte, UTF-8 emoji, multiline SSE, HTTP/2 SSE, optional usage, JSON fallback cùng response, 401/403/429/5xx/302/timeout/malformed/partial/oversize, không replay; cancel collector làm running calls về 0.
- PASS: `SseDecoderTest`, `ChatPayloadTest` (screen text tách user-data, không key trong body, optional null fields, estimate), `PanelAiFlowTest` (zero calls trước Send, revision đổi, hủy khi dismiss/editor/window/app, payload/language/cap, non-stream Reply chỉ hiện sau complete, retry phải quay review).
- PASS: `ProviderViewModelTest` (zero auto calls khi mở/lưu; synthetic test đúng model/cap, hủy/rời màn hình xóa key draft, lỗi enum), `AiPanelContentTest` (model/provider/target language, tap Send, Copy confirm).
- PASS: `assembleDebugAndroidTest`; **NOT_RUN** `KeystoreSecretStoreTest` instrumented (2 cases, alias/file test riêng), chưa có thiết bị/emulator chạy trong lượt này.
- NOT_RUN: request xác thực đến provider/model thật, network inspector trên điện thoại, actual AndroidKeyStore/clipboard/SET_TEXT/OxygenOS. Không có kết quả benchmark latency/24h thật để báo PASS.
