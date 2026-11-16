# CHANGELOG — ContextBubble v0.2

## M0 — Project scaffold & environment

**Modified files**
- Gradle: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), `app/proguard-rules.pro`, `.gitignore`.
- Manifest/resources: `AndroidManifest.xml` (chỉ `INTERNET` + khai báo AccessibilityService có `BIND_ACCESSIBILITY_SERVICE`), `res/xml/accessibility_service_config.xml`, `res/xml/network_security_config.xml` (cấm cleartext), `res/xml/data_extraction_rules.xml` (loại trừ backup), strings tiếng Việt, theme, launcher icon.
- Kotlin: `ContextBubbleApp` (Hilt), theme Material 3, `MainActivity` + Navigation 3 (Dashboard, Provider settings), `DashboardViewModel` (MVVM/StateFlow), DataStore `DataStoreSettingsRepository` (chỉ dữ liệu không bí mật: phiên bản + thời điểm đồng ý, cờ bật bong bóng), Room skeleton (`AppProfileEntity`, `AppProfileDao`, `ContextBubbleDatabase`), Hilt module, `BubbleAccessibilityService` dạng khai báo (không làm gì).
- Tests: `ManifestAuditTest`, `DataStoreSettingsRepositoryTest`, `AppProfileDaoTest`, `DashboardViewModelTest`, `DashboardContentTest`.
- Docs/scripts: `docs/DEVELOPMENT.md`, `CHANGELOG.md`, `scripts/audit.sh`, cập nhật `docs/TASKS.md`, `docs/DECISIONS.md`, `README.md`.

**Architecture decisions:** ADR-011 (phiên bản toolchain), ADR-012 (provider placeholder không có ô API key), ADR-013 (Room exportSchema tắt tới M5) trong `docs/DECISIONS.md`.

**Build:** PASS — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
**Unit tests:** PASS — `./gradlew :app:testDebugUnitTest` → 17 tests, 0 failures, 0 errors, 0 skipped.
**Lint:** PASS — `./gradlew :app:lint` → 0 errors, 1 warning (`OldTargetApi`, có chủ đích: targetSdk 36).
**Instrumented/device tests:** NOT_RUN — emulator không chạy được trên máy build (KVM lỗi); chưa có thiết bị OnePlus. `DEVICE_VERIFICATION_PENDING`.
**Line-count audit:** PASS — `scripts/audit.sh`, file lớn nhất `DashboardScreen.kt` 122 dòng.
**Security/privacy checks:** chỉ quyền `INTERNET` (+ quyền chữ ký nội bộ của androidx.core); không cleartext; không backup; không có API key/secret trong repo; không ghi log nội dung.
**Known limitations:** chưa chứng minh "app launches" trên thiết bị/emulator (chỉ Robolectric render Dashboard); service chỉ là khai báo.
**Next milestone prerequisites:** M1 — status observer, overlay, bong bóng kéo thả.

## M1 — Accessibility service + draggable bubble proof-of-concept

**Modified files**
- Mới: `domain/model/BubbleModels.kt`, `domain/policy/BubbleVisibilityPolicy.kt`, `domain/policy/EnabledServicesParser.kt`, `domain/layout/BubbleLayoutCalculator.kt`, `presentation/BubbleCoordinator.kt`, `overlay/OverlayController.kt`, `overlay/OverlayLifecycleOwner.kt`, `overlay/BubbleDragTracker.kt`, `overlay/SafeAreaProvider.kt`, `ui/overlay/BubbleContent.kt`, `service/SettingsSecureAccessibilityStatus.kt`, `service/ScreenLockMonitor.kt`, `res/drawable/ic_bubble.xml`, `scripts/m1_device_check.sh`.
- Sửa: `BubbleAccessibilityService.kt` (adapter thật: kết nối, sự kiện cửa sổ, khóa màn hình, cleanup), `AppConstants.kt` (BubbleDefaults, ExclusionDefaults), `UserSettings.kt` + `Repositories.kt` + `DataStoreSettingsRepository.kt` (lưu vị trí bong bóng; `AccessibilityStatusSource`), `AppModule.kt`, `DashboardState.kt`/`DashboardViewModel.kt`/`DashboardScreen.kt` (trạng thái thật + công tắc bong bóng + "Kiểm tra lại"), `strings.xml`, `app/build.gradle.kts` (versionName).
- Tests mới: `BubbleLayoutCalculatorTest`, `BubbleDragTrackerTest`, `BubbleVisibilityPolicyTest`, `EnabledServicesParserTest`, `BubbleCoordinatorTest`, `DashboardStateMapperTest`, `OverlayControllerTest` (Robolectric); cập nhật test dashboard/DataStore.
- Docs: `docs/TASKS.md`, `docs/DECISIONS.md` (ADR-014…020), `docs/DEVELOPMENT.md`.

**Build:** PASS — `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lint --no-build-cache` → BUILD SUCCESSFUL.
**Unit tests:** PASS — 71 tests, 0 failures, 0 errors, 0 skipped (gồm 20 chu kỳ bật/tắt service ở coordinator, 20 chu kỳ attach/detach overlay trên Robolectric).
**Lint:** PASS — 0 errors, 1 warning (`OldTargetApi`, có chủ đích).
**Instrumented/device tests:** NOT_RUN — emulator không chạy được (KVM lỗi trên máy build), chưa có OnePlus Ace 5. `DEVICE_VERIFICATION_PENDING`.
**Line-count audit:** PASS — file lớn nhất `OverlayController.kt` 200 dòng.
**Security/privacy checks:** service không đọc text, không gọi mạng, chỉ đọc package của root; log chỉ ghi stage (không package/nội dung); không thêm quyền; không `SYSTEM_ALERT_WINDOW`; không `flagRetrieveInteractiveWindows`; overlay bị gỡ khi service ngắt.
**Known limitations:** hành vi overlay/IME/khóa màn hình/OEM chưa kiểm chứng trên thiết bị; danh sách app nhạy cảm là best-effort; chạm bong bóng chỉ hiện Toast (capture ở M2); chưa có UI quản lý loại trừ (M5).
**Next milestone prerequisites:** chạy `scripts/m1_device_check.sh` + checklist thủ công trên emulator/OnePlus và ghi kết quả; xác nhận ADR-005 (ComposeView trong overlay) ổn định; người dùng yêu cầu bắt đầu M2.
