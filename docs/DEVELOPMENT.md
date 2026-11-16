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
3. **Không có API key nào trong repo hay trong cấu hình build.** Từ M3, người dùng nhập API key trong ứng dụng và key được mã hóa bằng Android Keystore. Không dùng `BuildConfig`/`local.properties` để chứa key (tránh nhúng key vào APK).

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

## 8. Quyền trong APK đã merge
Kiểm tra bằng `aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk`:
- `android.permission.INTERNET` (khai báo trực tiếp, dùng từ M3).
- `com.example.bubbleb.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: quyền chữ ký nội bộ do `androidx.core` tự thêm để hỗ trợ đăng ký receiver không export trên API < 33; không phải quyền nhạy cảm và không hiển thị cho người dùng.
- Không có `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES`, quyền micro/danh bạ/bộ nhớ.
