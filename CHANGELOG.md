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
