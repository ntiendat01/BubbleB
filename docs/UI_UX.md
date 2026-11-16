# UI_UX — BubbleB v0.2

## Design direction
- Vietnamese-first; clean, cool neutral surfaces with single blue accent, Material 3; dark/light theme.
- Overlay should feel like a utility, not a chat application: compact, single-tap actions, limited footprint.
- Text sizes: body >=14sp where feasible, controls 48dp touch targets, dynamic font scale and TalkBack labels.
- All UI elements must support narrow screen, landscape, display cutout, gesture nav, keyboard/IME and system font scaling.

## 1. MainActivity navigation

### Screen A: Onboarding / Dashboard
- App logo + current state `Chưa cấp quyền / Đã kết nối / Đã tắt bong bóng`.
- Brief privacy disclosure before permission flow; explicit `Tôi hiểu...` consent.
- Button `Mở cài đặt Trợ năng` (never auto-grant) and `Kiểm tra lại`.
- `Bật bong bóng` toggle active only when service ready; settings available regardless.
- Buttons: `Nhà cung cấp AI`, `Ứng dụng & persona`, `Quyền riêng tư`, `Hướng dẫn chia sẻ văn bản`.
- If Accessibility not available, Share action remains usable.

### Screen B: LLM Provider
- Inputs: display name, HTTPS base URL, model ID, API key obscured with reveal toggle, optional max output tokens.
- Buttons: `Kiểm tra kết nối`, `Lưu`, `Xóa cấu hình`.
- Connection test uses test phrase created by app and must never attach screen content. Validation messages for invalid URL/401/429/timeout.
- Show `Nội dung bạn xác nhận sẽ được gửi tới nhà cung cấp này` and configurable API privacy note.

### Screen C: Applications / Personas
- Observed packages list + search/entry; per app enable toggle, persona selector, output language, short custom instruction.
- Defaults: Zalo `Thân mật`, Gmail `Lịch sự`, Slack `Ngắn gọn`, others `Trung tính`. Check real installed app package IDs at runtime instead of assuming hardcoded IDs universally.
- App exclusion toggles clearly separate from persona style. Security exclusion has priority and cannot be undone in unsupported protected contexts.

### Screen D: Privacy
- App exclusions, preview/confirmation setting (mandatory for this MVP), payload truncation caps and disclosure.
- `Xóa mọi cấu hình` with confirmation, removes keys, preferences, profiles; ephemeral data cleared immediately.

## 2. Overlay UI

### Bubble collapsed
- Small round bubble (48dp), visible branded icon and subtle edge shadow; drag and edge snap.
- Tap opens actions **after capture**. Long press may open hide/show options in later version, not a P0 dependency.
- Do not overlay sensitive/excluded apps or Android lock UI. Position within usable viewport, including IME.

### Action panel
- Header `BubbleB · {app label}`, close X.
- Top status: `Đã đọc {N} ký tự` or unavailable notice.
- Four action buttons: `Trả lời`, `Tóm tắt`, `Giải thích`, `Dịch`; Reply disabled if no reliable input/conversation context.
- `Chia sẻ đoạn văn bản` hint where reader cannot access text.
- No free-form text input in panel for MVP due nonfocusable window.

### Review / consent panel (MUST)
- Title `Kiểm tra nội dung gửi đến AI`.
- Subheading `Nhà cung cấp: {name} · Model: {model}`.
- Scrollable *redacted* payload text preview with count `{N} ký tự`, show `Đã che dữ liệu nhạy cảm` if applicable.
- `Hủy` and high-contrast `Gửi đến AI` buttons. Back/close cancels, no transmission.
- May show short action prompt summary; do not reveal raw provider API key.

### Loading/streaming
- Title `Đang xử lý...`, streamed text in scrolling area for streaming actions.
- `Hủy` stops coroutine/network immediately. Skeleton/spinner; no promise response under fixed time.

### Result card
- For Reply: exactly 3 labeled suggestion cards. Each has `Sao chép`; `Chèn` appears only when eligible. Confirm potentially destructive replacement separately.
- For other actions: streamed/final text with `Sao chép`, `Thử lại`, `Đóng`.
- `Thử lại` returns to preview/confirmation; never calls provider without confirmation.
- Status footer: `Đã gửi {N} ký tự đến {provider}` after request actually sent; distinguish characters from tokens.

### Error & empty states
| Condition | User message | Action |
|---|---|---|
| No accessibility permission | `Chưa bật quyền Trợ năng` | `Mở cài đặt` |
| No accessible content | `Không đọc được nội dung màn hình này` | `Dùng Chia sẻ` |
| Excluded app | `Ứng dụng này được loại trừ để bảo vệ quyền riêng tư` | `Đóng` |
| No provider | `Chưa cấu hình nhà cung cấp AI` | `Mở cài đặt` |
| 401/403 | `API key hoặc quyền truy cập không hợp lệ` | `Cài đặt` |
| 429 | `API đang giới hạn yêu cầu` | `Đóng / Thử lại sau` |
| Network | `Không thể kết nối nhà cung cấp` | `Thử lại` (with confirm) |
| Insert stale target | `Ô nhập đã thay đổi; không chèn để tránh mất nội dung` | `Sao chép` |

## 3. Interaction requirements
- Tap bubble triggers one bounded snapshot, not a background stream.
- When user switches app while panel open, immediately clear context and close panel. Do not insert into newly focused app.
- Copy preserves the original generated text and shows system clipboard feedback.
- Use `Back`/outside tap according to overlay implementation constraints; always provide explicit close.
- Accessibility labels for bubble (`Mở BubbleB`), action buttons and status notifications.

## 4. UI implementation note
ComposeView within service must be wired to lifecycle/ViewModelStore/SavedStateRegistry tree owners **before attached to WindowManager**, with composition disposed on release. Rendering should observe state from an application-scoped controller rather than assuming an Activity ViewModel is available.
