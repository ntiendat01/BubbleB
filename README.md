# BubbleB v0.2 — Android AI Context Utility

**Trạng thái:** Đang phát triển Android MVP · **Ngày:** 2026-11-16 · **Ngôn ngữ:** Vietnamese

**Tiến độ code hiện tại:** M2.1 đã triển khai; có phần độc lập của M4 (schema Reply, Copy/xác nhận chèn an toàn), chưa nghiệm thu. M3 chưa triển khai nên bản hiện tại chưa gọi AI, nút gửi vẫn vô hiệu. M4 còn chờ nối M3, build/tests và kiểm chứng thiết bị; xem `docs/TASKS.md`.

BubbleB là bong bóng tiện ích Android: người dùng **chủ động chạm** để trích xuất văn bản được hỗ trợ từ ứng dụng đang hiển thị, lựa chọn hành động AI (Reply / Summarize / Explain / Translate), xem kết quả trong panel và **tự xác nhận** trước khi sao chép/chèn nội dung. Có persona theo ứng dụng, đa nhà cung cấp LLM và phương án Android Share để xử lý đoạn văn bản được chọn.

## Đọc trước khi code

1. `AGENTS.md` — quy tắc bắt buộc, đặc biệt **không file mã nguồn nào vượt 500 dòng**.
2. `docs/PRODUCT_SPEC.md` — tính năng, tương tác, giới hạn và acceptance criteria.
3. `docs/ARCHITECTURE.md` — package, service, state, data flow, interfaces, persistence.
4. `docs/SECURITY_PRIVACY.md` — dữ liệu màn hình, consent, API key, prompt injection.
5. `docs/UI_UX.md` — cấu trúc màn hình, overlay, trạng thái, tiếng Việt.
6. `docs/TASKS.md` — roadmap từng milestone và definition of done.
7. `docs/TEST_PLAN.md` — test case thủ công/tự động và ma trận thiết bị.
8. `docs/DECISIONS.md` — các quyết định kỹ thuật và các điểm cần thử trên máy thật.
9. `docs/REFERENCES.md` — liên kết Android API/policy cần kiểm tra khi triển khai.

## Phạm vi bàn giao

Repository gồm source Android và bộ đặc tả dưới `docs/`; **MVP chưa hoàn chỉnh**. Không đưa API key hoặc dữ liệu thực vào commit hay gói bàn giao.

**Thiết bị đích:** OnePlus Ace 5 / OxygenOS 16 (cần xác nhận `Build.VERSION.SDK_INT` trên thiết bị). `minSdk=26` là quyết định sản phẩm, **không phải** API tối thiểu của accessibility overlay. `compileSdk`, `targetSdk` và phiên bản dependency phải là bản stable tương thích có thể xác minh trong môi trường build; agent phải ghi phiên bản thực tế vào README kỹ thuật khi code.

## Build (README kỹ thuật)

Phiên bản toolchain thực tế, cách thiết lập SDK và lệnh build/test nằm ở `docs/DEVELOPMENT.md`. Lịch sử từng milestone ở `CHANGELOG.md`.

**Cấu hình AI cho bản debug (tùy chọn):** `cp .env.example .env` rồi điền `LLM_API_KEY` (key CoreWeave Forge cho gateway `https://api.inference.wandb.ai/v1`, gọi được model DeepSeek và Qwen). Không commit `.env`. Giá trị chỉ vào bản debug; key trong APK debug có thể bị trích xuất (ADR-029). Bản release dùng key nhập trong app (M3).

Model mặc định đã chọn: `deepseek-ai/DeepSeek-V4.1-Flash-1017` tại gateway trên (`DEEPSEEK_MODEL`, `DEFAULT_MODEL=deepseek`). Nếu đã có `.env`, cập nhật model trong file đó; thay đổi `.env.example` không tự cập nhật `.env`. M2 hiện chưa gọi API; phần gọi AI thuộc M3.

## Giao task cho agent

> Đọc `AGENTS.md`, `docs/PRODUCT_SPEC.md`, `docs/ARCHITECTURE.md`, `docs/SECURITY_PRIVACY.md` và `docs/TASKS.md`. Triển khai milestone người dùng yêu cầu theo tiến độ thực tế; ghi rõ phụ thuộc/gate còn thiếu, không tự mở rộng scope. Mỗi milestone báo file thay đổi, lệnh build/test, kết quả, hạn chế và bằng chứng kiểm thử. Không file code nào >500 dòng. Không hardcode API key. Không thực hiện thao tác tự động gửi tin nhắn. Nếu chưa xác minh được trên máy OnePlus, đánh dấu `DEVICE_VERIFICATION_PENDING`, không tuyên bố đã hoạt động.

## Cách sử dụng bản đặc tả

Project Android ở **gốc repository**, tài liệu dưới `docs/`. Tất cả các mục **P0** là điều kiện bắt buộc trước khi coi MVP hoàn thành. Các mục deferred không nên tự triển khai.
