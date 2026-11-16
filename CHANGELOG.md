# CHANGELOG — BubbleB v0.2

## M0 — Project scaffold & environment

**Modified files**

- Gradle: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), `app/proguard-rules.pro`, `.gitignore`.
- Manifest/resources: `AndroidManifest.xml` (chỉ `INTERNET` + khai báo AccessibilityService có `BIND_ACCESSIBILITY_SERVICE`), `res/xml/accessibility_service_config.xml`, `res/xml/network_security_config.xml` (cấm cleartext), `res/xml/data_extraction_rules.xml` (loại trừ backup), strings tiếng Việt, theme, launcher icon.
- Kotlin: `BubbleBApp` (Hilt), theme Material 3, `MainActivity` + Navigation 3 (Dashboard, Provider settings), `DashboardViewModel` (MVVM/StateFlow), DataStore `DataStoreSettingsRepository` (chỉ dữ liệu không bí mật: phiên bản + thời điểm đồng ý, cờ bật bong bóng), Room skeleton (`AppProfileEntity`, `AppProfileDao`, `BubbleBDatabase`), Hilt module, `BubbleAccessibilityService` dạng khai báo (không làm gì).
- Tests: `ManifestAuditTest`, `DataStoreSettingsRepositoryTest`, `AppProfileDaoTest`, `DashboardViewModelTest`, `DashboardContentTest`.
- Docs/scripts: `docs/DEVELOPMENT.md`, `CHANGELOG.md`, `scripts/audit.sh`, cập nhật `docs/TASKS.md`, `docs/DECISIONS.md`, `README.md`.

**Architecture decisions:** ADR-011 (phiên bản toolchain), ADR-012 (provider placeholder không có ô API key), ADR-013 (Room exportSchema tắt tới M5) trong `docs/DECISIONS.md`.

**Build:** PASS — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
**Unit tests:** PASS — `./gradlew :app:testDebugUnitTest` → 17 tests, 0 failures, 0 errors, 0 skipped.
**Lint:** PASS — `./gradlew :app:lint` → 0 errors, 1 warning (`OldTargetApi`, có chủ đích: targetSdk 36).
**Instrumented/device tests:** NOT\_RUN — emulator không chạy được trên máy build (KVM lỗi); chưa có thiết bị OnePlus. `DEVICE_VERIFICATION_PENDING`.
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
**Instrumented/device tests:** NOT\_RUN — emulator không chạy được (KVM lỗi trên máy build), chưa có OnePlus Ace 5. `DEVICE_VERIFICATION_PENDING`.
**Line-count audit:** PASS — file lớn nhất `OverlayController.kt` 200 dòng.
**Security/privacy checks:** service không đọc text, không gọi mạng, chỉ đọc package của root; log chỉ ghi stage (không package/nội dung); không thêm quyền; không `SYSTEM_ALERT_WINDOW`; không `flagRetrieveInteractiveWindows`; overlay bị gỡ khi service ngắt.
**Known limitations:** hành vi overlay/IME/khóa màn hình/OEM chưa kiểm chứng trên thiết bị; danh sách app nhạy cảm là best-effort; chạm bong bóng chỉ hiện Toast (capture ở M2); chưa có UI quản lý loại trừ (M5).
**Next milestone prerequisites:** chạy `scripts/m1_device_check.sh` + checklist thủ công trên emulator/OnePlus và ghi kết quả; xác nhận ADR-005 (ComposeView trong overlay) ổn định; người dùng yêu cầu bắt đầu M2.

## Rename — ContextBubble → BubbleB (ADR-021)

**Modified files:** toàn bộ package `com.example.contextbubble` → `com.example.bubbleb` (main + test, chuyển thư mục), `BubbleBApp.kt`, `BubbleBDatabase.kt`, `Theme.kt` (`BubbleBTheme`), `themes.xml`, `strings.xml`, `AndroidManifest.xml`, `accessibility_service_config.xml`, `app/build.gradle.kts`, `settings.gradle.kts` (`rootProject.name = "BubbleB"`), `.gitignore`, `scripts/*.sh`, `README.md`, `AGENTS.md`, `CHANGELOG.md`, mọi file trong `docs/`.
**Lưu ý cài đặt:** applicationId mới `com.example.bubbleb`; phải gỡ APK cũ `com.example.contextbubble`, nếu không sẽ có hai app song song.
**Build/test/lint/audit (sau đổi tên):** PASS — `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lint --no-build-cache` BUILD SUCCESSFUL; 71 tests, 0 failures; lint 0 errors, 1 warning (`OldTargetApi`, có chủ đích); `scripts/audit.sh` PASS. APK: package `com.example.bubbleb`, label `BubbleB`. Gate thiết bị M1 vẫn `DEVICE_VERIFICATION_PENDING`.

## M2 — Screen snapshot + privacy guard

**Modified files**

- Mới (domain, thuần Kotlin): `domain/model/ScreenModels.kt` (AiAction, ScreenContext, ScreenElement, InputSnapshot, CaptureResult/Failure/Stats), `domain/text/TextLimits.kt`, `domain/capture/ScreenNode.kt`, `domain/capture/ScreenTreeReader.kt`, `domain/capture/CapturePreflight.kt`, `domain/privacy/Redactor.kt`, `domain/usecase/PayloadPreviewBuilder.kt` (+ `ScreenHeuristics`).
- Mới (presentation/Android): `presentation/PanelState.kt`, `presentation/PanelCoordinator.kt`, `service/AccessibilityScreenCapture.kt`, `service/AccessibilityScreenNode.kt` (giải phóng node sau mỗi lần capture), `overlay/OverlayWindows.kt` (dùng chung cho bong bóng và panel), `overlay/PanelOverlayController.kt`, `ui/overlay/PanelContent.kt`, `res/drawable/ic_close.xml`.
- Sửa: `BubbleAccessibilityService.kt` (chạm → capture → panel; đóng panel khi đổi app/khóa/xoay/ngắt service; bỏ Toast M1), `OverlayController.kt` (dùng `OverlayWindows`), `BubbleCoordinator.kt` (`isScreenLocked`), `AppConstants.kt` (`CaptureLimits`), `Repositories.kt` (`ScreenCaptureSource`), `strings.xml` (chuỗi panel/lỗi theo UI\_UX), `accessibility_service_config.xml` (chú thích), `app/build.gradle.kts` (versionCode 2, versionName `0.2.0-m2`).
- Tests mới: `ScreenFixtures`, `ScreenTreeReaderTest`, `CapturePreflightTest`, `RedactionTest`, `PayloadPreviewBuilderTest`, `PanelCoordinatorTest`, `PanelOverlayControllerTest` (Robolectric), `PanelContentTest` (Robolectric Compose), `NoNetworkAuditTest`.
- Docs: `docs/TASKS.md` (chú thích M2), `docs/DECISIONS.md` (ADR-022…027, ADR-018 superseded), `docs/DEVELOPMENT.md` (§7 checklist thiết bị M2).

**Build:** PASS — `./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lint --no-build-cache` → BUILD SUCCESSFUL.
**Unit tests:** PASS — 130 tests, 0 failures, 0 errors, 0 skipped (71 cũ + 59 mới).
**Lint:** PASS — 0 errors, 1 warning (`OldTargetApi`, có chủ đích).
**Instrumented/device tests:** NOT\_RUN — emulator không chạy được (KVM lỗi), chưa có OnePlus Ace 5. `DEVICE_VERIFICATION_PENDING` cho cả M1 và M2.
**Line-count audit:** PASS — file lớn nhất `BubbleAccessibilityService.kt` 187 dòng.
**Security/privacy checks:** chỉ đọc màn hình khi người dùng chạm; kiểm tra package/khóa màn hình trước khi đọc text; mật khẩu → chặn; ẩn/không cùng package → bỏ; không log nội dung (stage-only, có test); không có API mạng trong `src/main` (có test); nút `Gửi đến AI` luôn disable ở M2; không thêm quyền mới (APK: `INTERNET` + quyền chữ ký nội bộ androidx.core).
**Known limitations:** redaction là best-effort (regex, không đảm bảo che hết; có thể che dư số sau chữ "code"); tên app có thể hiện package name do package visibility trên Android 11+; app vẽ bằng canvas/WebView/FLAG\_SECURE có thể không lộ text → `Không đọc được nội dung màn hình này`; phím Back hệ thống không đóng được panel (không focusable) — dùng nút X; chưa có lựa chọn ngôn ngữ đích cho "Dịch"; chưa có UI quản lý loại trừ (M5).
**Next milestone prerequisites:** chạy checklist thiết bị M1 (`scripts/m1_device_check.sh`) và M2 (`docs/DEVELOPMENT.md` §7) trên emulator/OnePlus; người dùng yêu cầu bắt đầu M3 và cung cấp provider (base URL HTTPS/model) — API key do người dùng tự nhập trong app.

## M2.1 — Fix: chạm bong bóng không có phản hồi (0.2.1-m2)

**Nguyên nhân (đã tái hiện trên Robolectric, chưa xác nhận trên máy):** `Surface` Material3 trong `BubbleContent` có `pointerInput` rỗng nên nuốt chạm; `OnTouchListener` gắn trên `ComposeView` không bao giờ được gọi → không có tap (và kéo bắt đầu trên bong bóng cũng không chạy). Test cũ chạm ngoài vùng 48 px nên không phát hiện; test mới `tapOnBubbleCenter_withComposeContent_invokesOnTap` FAIL trên commit 62c8b8a và PASS sau khi sửa.
**Modified files:** mới `overlay/TouchInterceptLayout.kt`; sửa `OverlayWindows.kt` (gốc cửa sổ có thể là layout chặn chạm; tree owners đặt trên gốc), `OverlayController.kt` (listener trên gốc, tap được post, log), `BubbleCoordinator.kt` + `MainActivity.kt` (bỏ qua package của chính BubbleB khi chỉ là overlay), `PanelState.kt`/`PanelCoordinator.kt` (lý do OWN\_APP/SCREEN\_LOCKED/CAPTURE\_ERROR, cause, `stageOf`), `AccessibilityScreenCapture.kt` (thử lại khi root là BubbleB/null, log), `PanelOverlayController.kt` (`show()` trả Boolean), `BubbleAccessibilityService.kt` (Toast khi không mở được panel, log `BB/Panel`), `PanelContent.kt`, `strings.xml`, `app/build.gradle.kts` (versionCode 3, `0.2.1-m2`). Tests: `BubbleTouchRealLayoutTest` (mới), `OverlayControllerTest`, `BubbleCoordinatorTest`, `PanelCoordinatorTest`. Docs: ADR-028, `DEVELOPMENT.md` §7 (lệnh logcat).
**Build/test/lint/audit:** PASS — 138 tests, 0 failures; lint 0 errors, 1 warning (`OldTargetApi`); audit PASS (file lớn nhất 213 dòng).
**Device:** NOT\_RUN trên máy build; cần người dùng cài lại và xác nhận. `DEVICE_VERIFICATION_PENDING`.

**Device (cập nhật 2026-11-16):** người dùng báo bản `0.2.1-m2` hoạt động trên điện thoại của họ (chạm mở panel, kéo thả được). Đây là báo cáo của người dùng, chưa có log; model máy chưa được xác nhận (không khẳng định là OnePlus Ace 5). Các mục checklist thiết bị khác vẫn `DEVICE_VERIFICATION_PENDING`.

## Chore — `.env.example` và BuildConfig provider defaults chỉ cho debug (ADR-029)

**Modified files:** mới `.env.example` (gateway CoreWeave `LLM_BASE_URL=https://api.inference.wandb.ai/v1`, `LLM_API_KEY=` rỗng, `DEEPSEEK_MODEL`, `QWEN_MODEL`, `DEFAULT_MODEL`; biến gọi thẳng DeepSeek/Qwen bị comment), `data/config/ProviderDefaults.kt`, `ProviderDefaultsTest`; sửa `.gitignore` (`.env`, `.env.*`, trừ `.env.example`), `app/build.gradle.kts` (đọc `.env` nếu có, `buildConfig = true`, field chỉ có giá trị ở debug, release rỗng, bắt buộc HTTPS), `scripts/audit.sh` (chặn key trong file mẫu và `.env` bị track), `docs/DECISIONS.md` (ADR-029), `docs/SECURITY_PRIVACY.md`, `docs/ARCHITECTURE.md` §8a, `docs/DEVELOPMENT.md`, `README.md`, `docs/TASKS.md`.
**Không thêm:** gọi mạng, UI M3, quyền mới.

## Config — Chọn DeepSeek-V4.1-Flash-1017 (2026-11-16)

**Files:** `.env.example`, `README.md`, `docs/DEVELOPMENT.md`; cập nhật `.env` cục bộ bị gitignore, giữ nguyên API key và các cấu hình khác.
**Thay đổi:** model DeepSeek mặc định là `deepseek-ai/DeepSeek-V4.1-Flash-1017`, `DEFAULT_MODEL=deepseek`, qua `LLM_BASE_URL=https://api.inference.wandb.ai/v1`. Dùng nguyên model ID do người dùng cung cấp, không đổi thành alias rút gọn.
**Kiểm tra cấu hình:** PASS — script kiểm tra template và `.env` cùng URL/model/selector yêu cầu; các dòng cấu hình cục bộ khác được giữ nguyên.
**Audit:** PASS — `bash scripts/audit.sh`, exit 0; file mã nguồn lớn nhất 213 dòng, không phát hiện secret hardcode hay endpoint cleartext theo quy tắc audit.
**Build/unit/lint:** NOT\_RUN — lệnh `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint` dừng trước Gradle vì `Unable to locate a Java Runtime`; cần JDK 17 để chạy lại.
**Giới hạn/bước tiếp:** chưa kiểm tra model bằng request xác thực; M2 chưa có luồng gọi API. Kết nối thực tế và xử lý kết quả thuộc M3.

## M4 preview — Reply và chèn bảo thủ (0.2.2-m4-preview, 2026-11-16)

**Trạng thái:** IMPLEMENTED phần độc lập; **chưa hoàn tất/không đánh dấu nghiệm thu M4**. Người dùng yêu cầu M4 trước khi M3 được triển khai; không mở rộng sang mạng hoặc provider settings (ADR-030).

**Files/kiến trúc:**
- Domain: `domain/reply/ReplyModels.kt` (labels/limits/prompt/contracts), `InsertionPolicy.kt`, `GuardedTextInserter.kt`; mở rộng `InputSnapshot`/`InputTarget`, `ScreenNode`, `ScreenTreeReader` để giữ định danh + monotonic timestamp và giới hạn draft.
- Data/DI: `data/reply/ReplyJsonParser.kt`; thêm kotlinx serialization JSON cùng phiên bản catalog hiện có, binding parser trong `AppModule.kt`.
- Presentation/UI: `ReplyRequest.kt`, `ReplySession.kt`, cập nhật `PanelState.kt`/`PanelCoordinator.kt`; mới `ReplyResultContent.kt`, `strings_reply.xml`, callbacks trong `PanelContent.kt`. Kết quả chỉ hiện dưới dạng Text; xác nhận Copy/chèn tách riêng, không tự Send.
- Android: `AccessibilityTextInserter.kt` (node session ngắn), `AndroidReplyClipboard.kt` (sensitive flag), `InputNodePrivacy.kt`; cập nhật `AccessibilityScreenNode.kt`, `BubbleAccessibilityService.kt`, config event metadata. Không thêm quyền hay flag mở rộng cây cửa sổ.
- Tests: thêm 8 file / 45 test declarations cho parser, snapshot, policy, fake editor/hậu điều kiện, session xác nhận, coordinator/stale request, Robolectric clipboard/password, Compose result. Cập nhật các tests cũ theo constructor/callback; **chưa chạy**.
- Docs: README phản ánh source hiện có; TASKS, ARCHITECTURE, DEVELOPMENT §9, TEST_PLAN; ADR-030…032 trong DECISIONS. `versionCode=4`, `versionName=0.2.2-m4-preview`.

**Hành vi:** JSON đúng ba nhãn và text giới hạn mới cho xét chèn. Sai schema/độ dài/nesting → Copy only, output rỗng không có thao tác. Chèn chỉ cho snapshot đầy đủ, ô trống, ID/window/package/cursor còn khớp, tuổi ≤120 giây, editable/visible/enabled/focused, không password và hỗ trợ SET_TEXT. Lấy lại mục tiêu trước ghi, kiểm tra hậu điều kiện; false/exception sau thử ghi → chưa xác minh, không retry/clipboard fallback. Nháp khác rỗng luôn Copy only. Đổi editor/window/app/khóa/revoke xóa phiên; request ID cũ bị bỏ.

**Build/unit/lint:** NOT_RUN — đã gọi `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint`, exit 1 trước Gradle: `Unable to locate a Java Runtime`. Cần JDK 17 và Android SDK theo DEVELOPMENT để chạy lại; không có bằng chứng biên dịch/tests PASS cho thay đổi này.
**Device/visual QA:** NOT_RUN / DEVICE_VERIFICATION_PENDING — chưa kiểm thử fixture Android/Zalo/Gmail/OnePlus; Robolectric cũng chưa chạy. Không suy kết quả từ các test declarations.
**Audit:** PASS — `bash scripts/audit.sh`, exit 0; 110 file code/config/script được kiểm tra, lớn nhất `BubbleAccessibilityService.kt` 237 dòng. Quy tắc audit không phát hiện secret hardcode/endpoint cleartext; XML mới/sửa parse được; `git diff --check` PASS.
**Hạn chế/rủi ro:** UI hiện tại vẫn dừng ở preview, nút Send vô hiệu vì chưa có M3. M4 mới có điểm nối request/result, không có HTTP cancellation thật. Android <33 hoặc app thiếu stable uniqueId/cursor → Copy only; clipboard trong overlay/OEM và SET_TEXT chưa được chứng minh. Android không hỗ trợ compare-and-set nguyên tử với editor từ app khác; ưu tiên Copy khi không chắc chắn. Metadata editor có thể làm panel đóng sớm (fail closed).
**Bước tiếp:** nối M3/provider/model đã chọn, chạy build/unit/lint, kiểm thử fixture và checklist thiết bị DEVELOPMENT §9 rồi mới xem xét nghiệm thu M4.
