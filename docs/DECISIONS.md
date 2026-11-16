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

## ADR-011: Toolchain versions — accepted (M0)
Dùng bộ phiên bản stable mới nhất resolve được trên máy build ngày 2026-11-16 (AGP 9.5.1, Gradle 9.9.1, Kotlin 2.4.21, Compose BOM 2026.09.00...). `compileSdk = 37` vì Compose UI 1.12/core-ktx 1.19 yêu cầu; `targetSdk = 36` (Android 16) cho khớp thiết bị OxygenOS 16 dự kiến và tránh bật hành vi runtime Android 17 chưa kiểm thử. Chi tiết ở `docs/DEVELOPMENT.md`.

## ADR-012: Provider settings ở M0 chỉ là placeholder — accepted (M0)
TASKS M0 chỉ yêu cầu "provider settings route". Kho bí mật Keystore thuộc M3, nên M0 **không** có ô nhập API key, URL hay model để tránh lưu bí mật trước khi có mã hóa. Không dùng `BuildConfig`/`local.properties` cho API key (sẽ bị nhúng vào APK).

## ADR-013: Room `exportSchema = false` cho skeleton — accepted (M0)
Schema v1 chỉ có bảng `app_profile` tối thiểu (packageName, isExcluded, updatedAtMs). Khi M5 thêm persona sẽ bật export schema vào `app/schemas/` và viết migration v1→v2 có test.

## ADR-014: Ẩn bong bóng = gỡ hẳn view khỏi WindowManager — accepted (M1)
Khi bị ẩn (app bị loại trừ, khóa màn hình, tắt bong bóng, service ngắt) `OverlayController` gọi `removeViewImmediate`, dispose composition và hủy lifecycle owner, thay vì `View.GONE`. Không để lại cửa sổ vô hình có thể chặn chạm. Mỗi lần hiện lại tạo ComposeView + owner mới (đây là "attach/detach cycle" trong gate M1).

## ADR-015: Xác định app tiền cảnh từ package của root cửa sổ active — accepted (M1)
Trên `TYPE_WINDOW_STATE_CHANGED` service chỉ đọc `rootInActiveWindow.packageName` (không đọc text/node con) và dùng `event.packageName` làm dự phòng. Nếu root null và event đến từ chính app (overlay), bỏ qua để tránh tự ẩn. Package rỗng/không rõ → ẩn (fail closed). Không dùng `flagRetrieveInteractiveWindows`.

## ADR-016: Danh sách ẩn mặc định — accepted, best-effort (M1)
Luôn ẩn trên: chính app, SystemUI (màn hình khóa/thanh thông báo), hộp thoại cấp quyền/cài đặt gói, và một danh sách nhỏ app nhạy cảm nhận diện được (authenticator, trình quản lý mật khẩu, ví, một số ngân hàng/ví VN). Package ID phải được kiểm tra trên thiết bị thật; danh sách không thay thế cấu hình loại trừ của người dùng (Room, UI ở M5 — M1 đã nối luồng `excludedPackages` từ Room vào policy).

## ADR-017: Điều kiện hiện bong bóng — accepted (M1)
Bong bóng chỉ hiện khi: service thực sự connected **và** người dùng đã đồng ý thông báo phiên bản hiện tại **và** công tắc "Bật bong bóng" bật **và** không khóa màn hình **và** app tiền cảnh không bị loại trừ. Công tắc mặc định bật, nhưng vì cần cả đồng ý + bật Trợ năng thủ công nên không có bong bóng nào xuất hiện ngoài ý muốn. Rút lại đồng ý → bong bóng ẩn ngay.

## ADR-018: Chạm bong bóng ở M1 không đọc màn hình — superseded by ADR-022 (M2)
M1 là proof-of-concept: chạm chỉ hiện Toast thông báo; không capture, không mở panel, không gọi mạng (capture thuộc M2). `onInterrupt` không có việc gì để hủy ở M1; từ M2 sẽ hủy job capture/LLM tại đây.

## ADR-019: Kéo thả dùng toạ độ raw qua `View.OnTouchListener` — accepted (M1)
Delta của Compose pointer input tính theo toạ độ cục bộ của cửa sổ đang di chuyển nên bị nhiễu; vì vậy kéo thả dùng `MotionEvent.rawX/rawY` trong `BubbleDragTracker` (thuần Kotlin, có unit test). Compose chỉ vẽ bong bóng và cung cấp nhãn TalkBack `Mở BubbleB` + hành động click. Cửa sổ dùng `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN`, không dùng `FLAG_LAYOUT_NO_LIMITS`. Vị trí lưu dạng (cạnh, tỉ lệ dọc) để giữ đúng sau xoay màn hình.

## ADR-020: IME — best-effort — accepted, needs device proof (M1)
Không xin `flagRetrieveInteractiveWindows` nên không liệt kê được cửa sổ IME. Vùng an toàn trừ IME inset nếu `WindowMetrics` (API 30+) hoặc insets của chính view overlay báo về; nếu OEM không báo, bong bóng có thể đè lên bàn phím và người dùng kéo nó đi. Cần kiểm chứng trên OnePlus.

## ADR-021: Đổi tên sản phẩm thành BubbleB — accepted (2026-11-16)
Theo yêu cầu của chủ dự án, tên sản phẩm "ContextBubble" đổi thành **BubbleB** trong toàn bộ mã nguồn, tài nguyên, script và tài liệu (kể cả đặc tả gốc v0.1, có ghi chú lịch sử ở đầu file). Thay đổi kỹ thuật:
- `applicationId`/`namespace`/package Kotlin: `com.example.contextbubble` → `com.example.bubbleb`. Đây là **ứng dụng khác** đối với Android: bản APK cũ không được nâng cấp mà cài song song; cần gỡ bản cũ (dữ liệu cũ chỉ có đồng ý + vị trí bong bóng, không chuyển đổi).
- Lớp: `ContextBubbleApp` → `BubbleBApp`, `ContextBubbleDatabase` → `BubbleBDatabase`, `ContextBubbleTheme` → `BubbleBTheme`, theme XML `Theme.BubbleB`.
- File dữ liệu: Room `bubbleb.db`, DataStore `bubbleb_settings`; tiêu đề cửa sổ overlay `BubbleB`; log tag `BB/…`.
- Tên cũ chỉ còn xuất hiện trong ADR này và mục đổi tên của `CHANGELOG.md` để truy vết.

## ADR-022: Luồng chạm → capture → panel ở M2 — accepted (M2)
Chạm bong bóng (chỉ thao tác chạm rõ ràng của người dùng) gọi `PanelCoordinator.onBubbleTapped` → **một** snapshot `rootInActiveWindow` có giới hạn. Kiểm tra trước (`CapturePreflight`: khóa màn hình, package rỗng, app của chính BubbleB, danh sách hệ thống/nhạy cảm/người dùng loại trừ) chạy **trước khi đọc bất kỳ text nào**. Chạm lặp lại khi đang capture/đang mở panel bị bỏ qua; kết quả về muộn sau khi đã đóng panel bị bỏ (requestId). `ScreenContext` thô chỉ nằm trong bộ nhớ của coordinator, bị xoá khi đóng, đổi app, khóa màn hình, xoay màn hình, service ngắt hoặc chính sách ẩn bong bóng. Không lưu, không log nội dung.

## ADR-023: Gặp trường mật khẩu → chặn cả màn hình — accepted (M2)
Nếu traversal gặp node `isPassword`, toàn bộ capture trả `SENSITIVE_SCREEN` (thay vì chỉ bỏ node đó), vì màn hình đăng nhập thường chứa thông tin nhạy cảm khác. Đây là lựa chọn fail-closed; có thể nới ở milestone sau nếu người dùng yêu cầu.

## ADR-024: Nội dung nháp trong ô nhập không vào payload — accepted (M2)
Text đang gõ trong ô nhập (role INPUT) được giữ trong `InputSnapshot` chỉ để xác định khả năng "Trả lời" (và cho M4 chèn an toàn), nhưng **không** đưa vào preview/payload gửi AI. Hint text không bị coi là nháp. `contentDescription` chỉ dùng khi node không có text, và cũng qua redaction.

## ADR-025: Nút "Gửi đến AI" bị vô hiệu ở M2 — accepted (M2)
M2 chưa có LLM/provider (M3). Panel xem trước luôn hiện `Chưa cấu hình nhà cung cấp AI` và nút gửi bị disable; không có đường code mạng nào trong `src/main` (kiểm bằng `NoNetworkAuditTest`). Nút cũng bị disable khi preview gần như toàn bộ đã bị che (< 20 ký tự thật). `Hủy` quay về danh sách hành động không capture lại; nút X đóng và xoá snapshot. Do cửa sổ panel không focusable nên phím Back hệ thống không tới được panel — luôn có nút đóng rõ ràng. Chọn ngôn ngữ đích cho "Dịch" để M3.

## ADR-026: Giới hạn capture và redaction — accepted (M2)
500 node, độ sâu 20, 300 ms (đồng hồ `elapsedRealtime`), 500 code point mỗi node, 6000 code point tổng; payload Reply tối đa 3000. Tính theo Unicode code point, không cắt đôi cặp surrogate; bỏ ký tự điều khiển/bidi override. Redaction cục bộ theo thứ tự token → credential → email → thẻ (Luhn 13–19 số) → OTP (cần từ khoá neo VI/EN) → SĐT (+84/0…); cố ý không che số chung chung (ngày, tiền, giờ, số phòng) để tránh che nhầm. Redaction là best-effort; người dùng vẫn phải xem lại.

## ADR-027: Panel là cửa sổ overlay riêng — accepted, needs device proof (M2)
Panel dùng cửa sổ `TYPE_ACCESSIBILITY_OVERLAY` thứ hai (`BubbleB Panel`, ~90% chiều rộng vùng an toàn, cao tối đa 70%, cuộn được, không focusable, không có ô nhập). Bong bóng và panel loại trừ nhau. Code WindowManager dùng chung qua `OverlayWindows`. Tên app lấy từ `PackageManager`; trên Android 11+ nếu không thấy package (package visibility) thì hiển thị package name — cần kiểm tra trên OnePlus.

## ADR-028: Sửa lỗi "chạm bong bóng không có gì" (0.2.1-m2) — accepted, needs device proof
Báo cáo từ máy thật: bong bóng hiện nhưng chạm không có phản hồi. Nguyên nhân đã tái hiện trên Robolectric: `Surface` của Material3 gắn `pointerInput` rỗng nên Compose nuốt mọi chạm; `OnTouchListener` đặt trên `ComposeView` không bao giờ nhận được sự kiện (cả chạm lẫn kéo bắt đầu trên bong bóng). Test M1 cũ chạm ngoài vùng 48 px nên không phát hiện. Sửa: cửa sổ bong bóng có gốc là `TouchInterceptLayout` (chặn chạm khỏi con Compose) và listener đặt trên gốc; tree owners đặt trên view gốc. Phòng ngừa thêm (giả thuyết, chưa xác nhận trên máy): bỏ qua sự kiện foreground mang package của chính BubbleB khi Activity của BubbleB không hiển thị (đó là cửa sổ overlay của mình); capture thử lại tối đa 3 lần/120 ms nếu root là BubbleB hoặc null; mọi lỗi (kể cả exception, không thêm được cửa sổ panel) đều hiện panel có lý do hoặc Toast; log stage `BB/Service`, `BB/Overlay`, `BB/Panel` (không nội dung).

## ADR-029: `.env` → BuildConfig chỉ cho bản DEBUG — accepted (2026-11-16, lựa chọn có chủ đích của chủ dự án)
Theo yêu cầu của chủ dự án, file `.env` ở gốc repo (gitignore; chỉ commit `.env.example` với key rỗng) được Gradle đọc lúc build và đưa vào `BuildConfig` **chỉ của build type `debug`**; `release` luôn nhận chuỗi rỗng. Thiếu `.env` không làm hỏng build; base URL không phải `https://` làm build lỗi. Tuyến mặc định là gateway OpenAI-compatible **CoreWeave Serverless Inference** (`LLM_BASE_URL`, `LLM_API_KEY` = key CoreWeave Forge) phục vụ cả model DeepSeek và Qwen (`DEEPSEEK_MODEL`, `QWEN_MODEL`, `DEFAULT_MODEL`); biến gọi thẳng DeepSeek/Qwen là tùy chọn (bị comment trong file mẫu). `ProviderDefaults.fromBuildConfig()` chỉ đọc giá trị, không gọi mạng, `toString()` che key.
Hệ quả và giới hạn (đã chấp nhận):
- Key trong APK debug **trích xuất được** (giải nén APK/đọc dex). Đây là ngoại lệ có giới hạn của SECURITY_PRIVACY §6 ("No embedded developer key"), chỉ cho máy dev/test cá nhân; không phân phối APK debug có key.
- Con đường cho bản phát hành vẫn là nhập key trong app, mã hóa bằng Android Keystore (M3). Khi M3 có cấu hình trong app, giá trị người dùng nhập **phải ghi đè** giá trị `.env`.
- Không log/hiển thị key; audit (`scripts/audit.sh`) chặn `.env.example` có key khác rỗng và chặn commit `.env` thật.

## ADR-030: Triển khai phần độc lập của M4 trước M3 — accepted (2026-11-16)
Người dùng yêu cầu làm M4 khi M3 chưa triển khai. Giữ phạm vi M4: schema/parser Reply, dữ liệu xác thực ô nhập, kết quả/Copy/xác nhận chèn và Android adapter. Không bổ sung mạng, lưu key hay provider UI thuộc M3. `PanelCoordinator.onReplySendConfirmed(providerReady)` chỉ tạo hợp đồng request sau preview và xác nhận người dùng; M3 phải nối nó vào nút Send, hiển thị provider và tóm tắt chỉ dẫn trước gửi, gửi screen text như dữ liệu không tin cậy trong thông điệp riêng, rồi chuyển **response hoàn chỉnh** kèm request ID về `onReplyResponse`. Response cũ bị bỏ sau dismiss/đổi context. M3 còn phải thực sự hủy HTTP khi đóng panel; M4 hiện chỉ vô hiệu request ID vì chưa có HTTP.
Nút Send trong bản hiện tại vẫn vô hiệu. Không thêm kết quả giả vào UI sản phẩm. M4 chưa thể nghiệm thu toàn luồng hoặc đánh dấu hoàn thành khi thiếu M3/JDK/kiểm thử thiết bị.

## ADR-031: Chèn vào ô trống có định danh ổn định; trường hợp khác Copy only — accepted / needs device proof
`InputSnapshot` chứa text giới hạn 500 code points, cờ đầy đủ, package/window/`uniqueId`, selection và thời điểm monotonic; không giữ node. Snapshot không đầy đủ/thiếu ID/con trỏ không xác định/quá 120 giây/nháp khác rỗng đều không được chèn. Dùng `AccessibilityNodeInfo.uniqueId` (API 33+) để tránh đoán mục tiêu bằng vị trí hoặc resource ID trùng lặp; Android cũ và app không cung cấp ID ổn định dùng Copy only. Không xin thêm quyền hoặc flag mở rộng cây cửa sổ. API reference: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#getUniqueId()
`GuardedTextInserter` kiểm tra chính sách trên DTO, lấy lại ô focus trước ghi và kiểm tra hậu điều kiện sau một lần `ACTION_SET_TEXT`. Android adapter chỉ lấy node trong một phiên đồng bộ và giải phóng ngay. Chỉ hỗ trợ ô rỗng thực sự + selection 0/0 + editable/visible/enabled/focused + không password + action được hỗ trợ. Không có Replace/Paste/Send và không tự động dùng clipboard khi lỗi. Android không có thao tác compare-and-set nguyên tử giữa kiểm tra text và `ACTION_SET_TEXT`; phải kiểm thử trên fixture/thiết bị và dùng Copy khi không chắc chắn. Không tuyên bố bảo đảm trên mọi editor/OEM.

## ADR-032: Vô hiệu snapshot bằng metadata; xác nhận riêng cho Copy — accepted / needs device proof
Bổ sung `typeViewFocused`, `typeViewTextChanged`, `typeViewTextSelectionChanged` chỉ để xóa phiên đang mở khi editor thay đổi (kể cả gõ rồi xóa về text cũ). Ngoài phiên người dùng đã chạm, không đọc metadata editor; không đọc event text/source/node hierarchy trong các callback này. Sự kiện window khác/không xác định cũng xóa context. Đây là phần kiểm tra phiên người dùng chủ động, không phải tự động thu thập nội dung. Một số app có thể phát event dư nên panel đóng sớm; ưu tiên fail closed và ghi kết quả trên máy thật.
Copy yêu cầu tap và xác nhận thay clipboard; không đọc clipboard cũ, không tự paste, không sao chép khi chèn lỗi. Clip được gắn `android.content.extra.IS_SENSITIVE` theo Android: https://developer.android.com/develop/ui/views/touch-and-input/copy-paste . Thông báo giao diện nêu clipboard có thể được truy cập qua Android/công cụ người dùng, không hứa clipboard là kho riêng tư.

## ADR-033: Key mã hóa riêng, gắn với endpoint; metadata và cấu hình debug — accepted (M3)
`StoredProviderRepository` chỉ lưu `ProviderConfig` không bí mật trong DataStore; `EncryptedFileSecretStore` ghi envelope version/12-byte IV/ciphertext trong `noBackupFilesDir` bằng AtomicFile. AES-256-GCM dùng AndroidKeyStore và IV ngẫu nhiên cho mỗi lần ghi; normalized base URL làm AAD. Đổi endpoint phải nhập lại key, tránh mang key provider cũ sang host mới. Nếu ghi secret thành công nhưng ghi metadata lỗi, AAD không khớp sẽ fail closed và yêu cầu nhập lại thay vì dùng nhầm key. Key mất hiệu lực/ciphertext hỏng bị xóa; lỗi tạm thời giữ ciphertext và báo không sẵn sàng. Không hạ cấp sang plaintext.
Cấu hình SAVED luôn ưu tiên debug defaults; key SAVED lỗi không tự dùng key `.env`. Xóa cấu hình đặt cờ tắt debug fallback trước khi xóa key. Key draft trong UI chỉ ở RAM, không SavedStateHandle/rememberSaveable; rời màn hình xóa draft và hủy connection test. Tham khảo Keystore: https://developer.android.com/privacy-and-security/keystore . Tests JVM dùng cipher JCE thật cho envelope/AAD; instrumented test dùng alias/file ngẫu nhiên riêng, không đụng key của người dùng, hiện chưa chạy trên thiết bị.

## ADR-034: Endpoint HTTPS công khai và một POST không replay — accepted (M3)
Base URL nhận dạng `/v1` hoặc full `/chat/completions`; append suffix đúng một lần. Cấm HTTP, credentials/query/fragment, địa chỉ nội bộ/localhost. `PublicProviderDns` kiểm tra mọi IP trả về (IPv4/IPv6, loopback/link-local/site-local/ULA/carrier/private), không chỉ chuỗi hostname. Địa chỉ local/private không được hỗ trợ trong MVP; đây là trade-off ưu tiên tránh upload nhầm vào host nội bộ.
OkHttp 5.5.1, mặc định TLS/hostname validation, không body logger/cache, `followRedirects=false`, `followSslRedirects=false`, `retryOnConnectionFailure=false`; call timeout 60s/read timeout 20s. Không tự replay khi 401/403/429/5xx/timeout/stream không hỗ trợ. Người dùng phải quay lại review và xác nhận lần gửi mới, vì lần trước có thể tính phí. Phiên bản stable/security fix được đối chiếu: https://raw.githubusercontent.com/square/okhttp/master/CHANGELOG.md . API gateway: https://docs.coreweave.com/products/inference/serverless/api-reference/chat-completions . Model ID do người dùng chỉ định giữ nguyên, chưa gọi provider thật để kiểm chứng.

## ADR-035: CallbackFlow, hủy Call và đóng response trên cùng reader thread — accepted (M3)
HTTP được enqueue/đọc trên I/O thread; collector cancellation gọi `Call.cancel()`. Response luôn nằm trong `use` trên callback reader. Không đồng thời `response.close()` từ thread hủy vì có thể đua với blocking read của Okio và gây `Unbalanced enter/exit` (đã phát hiện bằng TLS test, sửa và chạy lại PASS). Hủy socket/read làm callback thoát và đóng body trên chính reader thread; test chờ dispatcher không còn running call. Job thuộc scope service, không GlobalScope, bị hủy khi dismiss/đổi editor/window/app/lock/revoke.

## ADR-036: Consent gắn revision provider; SSE và fallback có kiểm soát — accepted (M3)
Preview chứa provider/model, action summary, payload đã che và cap, language Việt/Anh, token/cost ước lượng (giá tự nhập, không phải hóa đơn). Ngay trước HTTP, runner resolve key và so config/revision với preview; thay đổi cấu hình yêu cầu review lại. Prompt cố định ở system message; screen text được JSON-escape thành dữ liệu user message riêng, không đưa draft/target metadata vào prompt, không tools/function calling. Chỉ render text; tool calls thực bị từ chối, trường tool null/rỗng được chấp nhận để tương thích.
SSE đọc theo logical line (không coi mỗi network read là JSON), hỗ trợ UTF-8/chunk boundaries, multiline `data:`, optional usage và `[DONE]`; cap event 32 KiB/tổng stream 512 KiB/output 12000 code points/body JSON 256 KiB/depth 16. Thiếu DONE hoặc lỗi trả trạng thái kết quả một phần, không chèn. Nếu server trả JSON thay SSE, dùng chính response đó và báo rõ, không gửi lần thứ hai. Có toggle non-stream; Reply luôn yêu cầu non-stream và chỉ parse/cho xét chèn sau complete. Usage chỉ là aggregate trong RAM; không prompt/response/request ID hay history trên disk.

## Open device-validation questions
- Accessibility service overlay remains usable across OxygenOS 16 lock/unlock, memory pressure and background cleanup? Track actual behavior; do not assume.
- Do Zalo/Gmail expose text and writable input nodes reliably on this OS/build? Per-app observations only; fail gracefully otherwise.
- Can native overlay remain nonfocusable and still keep workable panel taps, IME placement, outside tap? Prove at M1.
- Which provider/model/endpoint exactly is available under the user's $10 balance? User enters values; no vendor price or SLA hardcoded.
