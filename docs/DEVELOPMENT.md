# DEVELOPMENT — hướng dẫn build BubbleB (README kỹ thuật)

## 1. Phiên bản thực tế đã dùng để build (đã xác minh trên máy build Linux, 2026-11-16)

| Thành phần | Phiên bản |
|---|---|
| JDK | Eclipse Temurin 17.0.21+9 |
| Gradle (wrapper) | 9.9.1 |
| Android Gradle Plugin | 9.5.1 (Kotlin tích hợp sẵn của AGP 9, không áp dụng plugin `kotlin-android`) |
| Kotlin / Compose compiler plugin | 2.4.21 |
| KSP | 2.3.14 |
| Hilt | 2.60.1 · `androidx.hilt:hilt-lifecycle-viewmodel-compose` 1.4.0 |
| Compose BOM | 2026.09.00 (Material 3) |
| Navigation 3 | 1.2.1 |
| Lifecycle | 2.11.0 |
| Room | 2.8.5 |
| DataStore Preferences | 1.3.0 |
| Coroutines | 1.11.0 |
| Robolectric (unit test JVM) | 4.18 |
| `compileSdk` | 37 (platform `android-37.0`) — bắt buộc vì Compose UI 1.12 / core-ktx 1.19 yêu cầu compileSdk ≥ 37 |
| `targetSdk` | 36 (Android 16) — giữ ở 36 cho khớp OxygenOS 16 dự kiến, tránh hành vi runtime của Android 17 chưa kiểm thử; lint cảnh báo `OldTargetApi` là có chủ đích |
| `minSdk` | 26 (quyết định hỗ trợ sản phẩm, xem ADR-008) |
| Build-tools | 36.0.0 |

Danh sách phiên bản nằm ở `gradle/libs.versions.toml`.

## 2. Thiết lập môi trường
1. Cài JDK 17 và Android SDK (cmdline-tools, `platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools`).
2. Tạo `local.properties` ở gốc repo (file này **đã bị gitignore**, không commit):
   ```properties
   sdk.dir=/duong/dan/toi/android-sdk
   ```
3. **Không có API key nào được commit.** Bản phát hành: từ M3 người dùng nhập API key trong ứng dụng, mã hóa bằng Android Keystore.
4. **(Tùy chọn, chỉ bản debug — ADR-029)** cấu hình AI qua `.env`:
   ```bash
   cp .env.example .env    # điền LLM_API_KEY (key CoreWeave Forge) và chỉnh model nếu muốn
   ./gradlew :app:assembleDebug
   ```
   - `.env` bị gitignore; chỉ `.env.example` (key rỗng) được commit. `scripts/audit.sh` báo lỗi nếu `.env` bị track hoặc file mẫu có key.
   - Giá trị chỉ vào `BuildConfig` của bản **debug**; bản release luôn rỗng. Thiếu `.env` vẫn build được; base URL không phải HTTPS → build lỗi.
   - Cấu hình mặc định đã chọn: `LLM_BASE_URL=https://api.inference.wandb.ai/v1`, `DEEPSEEK_MODEL=deepseek-ai/DeepSeek-V4.1-Flash-1017`, `DEFAULT_MODEL=deepseek`. Nếu `.env` đã tồn tại, cập nhật các biến này trong file đó và build lại APK debug.
   - Key trong APK debug trích xuất được: không chia sẻ APK debug có key.
   - M2 chưa gọi mạng; giá trị chỉ được đọc sẵn (`ProviderDefaults`) cho M3.

## 3. Lệnh build / kiểm thử
```bash
./gradlew :app:assembleDebug        # APK debug: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # unit test JVM (JUnit4 + Robolectric + coroutines-test)
./gradlew :app:lint                 # Android lint (abortOnError = true)
scripts/audit.sh                    # kiểm tra giới hạn 500 dòng, secret hardcode, http:// cleartext
```
Test thiết bị (`connectedDebugAndroidTest`) cần emulator/thiết bị thật — xem mục 5.

## 4. Thông tin thiết bị đích
- OnePlus Ace 5 / OxygenOS 16: **DEVICE_VERIFICATION_PENDING**. Chưa có thiết bị trong môi trường build nên chưa xác nhận `Build.VERSION.SDK_INT`. Kiểm tra bằng:
  ```bash
  adb shell getprop ro.build.version.sdk
  adb shell getprop ro.build.version.release
  adb shell getprop ro.product.model
  ```
- Cài APK debug: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## 5. Giới hạn của môi trường build hiện tại
- Máy build Linux có `/dev/kvm` nhưng tạo vCPU KVM gây lỗi kernel (nested virtualization không hoạt động), nên **Android Emulator không khởi động được**. Vì vậy mọi test instrumented/thiết bị đều `NOT_RUN`.
- Các test Robolectric chạy trên JVM; chúng **không** tương đương test trên thiết bị thật (không mô phỏng token `TYPE_ACCESSIBILITY_OVERLAY`, hành vi OEM, IME, khóa màn hình thật).

## 6. Kiểm tra gate M1 trên emulator/thiết bị (khi có)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Mở app, bấm "Tôi hiểu và đồng ý" một lần, rồi:
scripts/m1_device_check.sh     # 20 lần bật/tắt service + 20 chu kỳ attach/detach + quét crash/ANR
```
Script chỉ là test harness (dùng `adb shell settings put secure ...`), không phải hướng dẫn cho người dùng vượt qua "restricted settings". Kiểm tra thủ công thêm: kéo bong bóng ra giữa rồi thả (phải hút về cạnh gần hơn), xoay màn hình, mở bàn phím, khóa/mở khóa, mở app bị loại trừ (ví dụ Google Authenticator), tắt Trợ năng trong Cài đặt (bong bóng phải biến mất). Ghi kết quả `SUPPORTED/PARTIAL/UNSUPPORTED` kèm ảnh chụp **dữ liệu giả**.

## 7. Kiểm tra M2 trên emulator/thiết bị (khi có) — chưa chạy
Chưa có script tự động cho M2 (cần chạm vào app thật). Checklist thủ công, **chỉ dùng dữ liệu giả**:
1. Mở một app chat/ghi chú có text giả (ví dụ tin nhắn chứa `0912 345 678`, `test@example.com`, `Mã xác thực: 123456`), chạm bong bóng → panel `BubbleB · {app}` hiện `Đã đọc N ký tự`.
2. Chọn `Tóm tắt` → `Kiểm tra nội dung gửi đến AI`: SĐT/email/OTP phải là `[REDACTED_…]`, có dòng `Đã che dữ liệu nhạy cảm`, nút `Gửi đến AI` bị vô hiệu.
3. Có ô nhập đang focus → `Trả lời` bật; không có → `Trả lời` mờ. Nội dung nháp không xuất hiện trong preview.
4. Màn hình đăng nhập có ô mật khẩu → thông báo chặn. App bị loại trừ (Google Authenticator…) → bong bóng ẩn / panel chặn.
5. Mở panel rồi chuyển app, khóa màn hình, xoay màn hình → panel phải đóng.
6. `adb logcat -s BB/Service BB/Overlay` chỉ có stage, không có text màn hình hay package name. Không có request mạng (ví dụ kiểm bằng Network Inspector/`adb shell dumpsys netstats`).
Ghi kết quả `SUPPORTED/PARTIAL/UNSUPPORTED` từng app (Zalo, Gmail, Chrome).

Chẩn đoán khi chạm bong bóng không phản hồi: `adb logcat -c && adb logcat -v time -s BB/Service:D BB/Overlay:D BB/Panel:D`. Chuỗi mong đợi: `bubble_tap` → `tap_received` → `state:capturing` → `capture_done:…` → `state:actions…` hoặc `state:blocked/unavailable…,cause=…`. Thiếu `bubble_tap` = chạm không tới listener; có `foreground_ignored:own_overlay` là bình thường; `panel_attach_failed` = không thêm được cửa sổ panel.

## 8. Quyền trong APK đã merge
Kiểm tra bằng `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk`:
- `android.permission.INTERNET` (khai báo trực tiếp, dùng từ M3).
- `com.example.bubbleb.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: quyền chữ ký nội bộ do `androidx.core` tự thêm để hỗ trợ đăng ký receiver không export trên API < 33; không phải quyền nhạy cảm và không hiển thị cho người dùng.
- Không có `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES`, quyền micro/danh bạ/bộ nhớ.

## 9. M4 — triển khai độc lập, chưa nối M3 / chưa kiểm thử trên máy

M4 hiện có parser/schema Reply, state kết quả và xác nhận Copy/chèn; M3 chưa có nên **không có đường người dùng nhận kết quả AI trong APK hiện tại**. Không thử bấm nút Send đang vô hiệu để nghiệm thu M4. Chưa có request xác thực đến provider, chưa chứng minh tương thích DeepSeek/Zalo/Gmail/OnePlus.

Điểm nối cho M3:
1. Sau khi người dùng chọn Reply, xem provider/nội dung/chỉ dẫn rồi xác nhận Send, resolve provider và gọi `onReplySendConfirmed(providerReady)`. Null = không tạo request, không gọi mạng.
2. Tạo request Chat Completions bằng instruction xác định và screen text được bọc như dữ liệu không tin cậy riêng. Không đưa draft/target metadata vào prompt. Parser không phụ thuộc provider có hỗ trợ JSON Schema hay không.
3. Chỉ chuyển assistant content hoàn chỉnh (không phải HTTP error body hoặc delta chưa hoàn tất) cùng request ID vào `onReplyResponse(id, content, SystemClock.elapsedRealtime())`. M3 phải hủy HTTP khi phiên bị đóng, không chỉ bỏ response cũ.
4. Service đã nối callbacks xác nhận Copy/chèn; không mở Activity lấy focus khi đang chèn. Trước API 33 hoặc khi không có stable uniqueId/con trỏ: Copy only.

Lệnh sau khi có JDK 17 và SDK theo mục 2:
```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint
bash scripts/audit.sh
```
Trên máy hiện tại: Gradle **NOT_RUN** (`Unable to locate a Java Runtime`); XML well-formedness/audit **PASS**. Các tests mới chưa chạy: parser, snapshot metadata, insertion policy, fake editor với hậu điều kiện, phiên xác nhận, request ID, Compose và Robolectric clipboard/password. Không thay kết quả NOT_RUN thành PASS dựa trên đọc code.

Checklist thiết bị **sau khi nối M3**, dùng dữ liệu giả và ghi model/OS/API:
- Nhận đúng ba nhãn; JSON sai chỉ có Copy, không có Insert; response rỗng không có thao tác ghi.
- Clipboard có dữ liệu giả trước đó: tap Copy chưa đổi clipboard; Hủy giữ nguyên; Xác nhận mới thay. Không hứa clipboard là kho riêng tư.
- Ô nhập trống có stable uniqueId + selection 0/0 + hỗ trợ SET_TEXT: tap Insert chưa ghi; Hủy không ghi; Xác nhận lấy lại ô nhập, chèn đúng một lần và không tự gửi.
- Có nháp (kể cả khoảng trắng), không có ID/cursor, password, đổi focus/window/app, gõ rồi xóa lại, hết 120 giây, khóa/tắt service/rút consent/thêm exclusion: không ghi vào mục tiêu cũ. Khi editor phát metadata thay đổi, panel có thể đóng ngay.
- Action trả false, OEM ném lỗi hoặc hậu điều kiện không chứng minh được: không tự thử lại/paste/Copy, hiện trạng thái chưa xác minh nếu panel còn mở; người dùng tự kiểm tra editor.
- Đóng panel hoặc ngắt service khi đang chờ: xóa phiên, bỏ response muộn; kiểm tra HTTP cancellation thật ở M3. Chạy trên fixture rồi Zalo/Gmail nơi hỗ trợ; ghi SUPPORTED/PARTIAL/UNSUPPORTED, không suy từ Robolectric ra máy thật.
