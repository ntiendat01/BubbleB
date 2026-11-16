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

## ADR-018: Chạm bong bóng ở M1 không đọc màn hình — accepted (M1)
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

## Open device-validation questions
- Accessibility service overlay remains usable across OxygenOS 16 lock/unlock, memory pressure and background cleanup? Track actual behavior; do not assume.
- Do Zalo/Gmail expose text and writable input nodes reliably on this OS/build? Per-app observations only; fail gracefully otherwise.
- Can native overlay remain nonfocusable and still keep workable panel taps, IME placement, outside tap? Prove at M1.
- Which provider/model/endpoint exactly is available under the user's $10 balance? User enters values; no vendor price or SLA hardcoded.
