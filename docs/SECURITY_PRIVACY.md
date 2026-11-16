# SECURITY_PRIVACY — data handling policy & threat model

## 1. Trust boundaries
1. Android app under observation (untrusted screen text).
2. BubbleB local process (sensitive screen data, app choices, keys).
3. User-configured external LLM provider (remote third party; privacy policy may differ).
4. Android Clipboard (system-visible copied text, not a private vault).

**Privacy invariant:** No screen-text capture without explicit tap/Share; no remote upload until visible payload review + explicit Send confirmation; no screen text on disk/logs.

## 2. User disclosure (show BEFORE enabling Accessibility)
> BubbleB có thể đọc văn bản và thông tin giao diện đang hiển thị trên ứng dụng khác khi bạn chạm bong bóng. Bạn sẽ được xem trước và xác nhận nội dung trước khi gửi đến nhà cung cấp AI đã chọn. BubbleB không tự gửi tin nhắn, không lưu nội dung màn hình và bạn có thể tắt quyền bất cứ lúc nào trong Cài đặt Android. Một số ứng dụng hoặc màn hình không cho phép đọc nội dung.

Require consent checkbox/tap after scroll/read; persist only notice version+timestamp (not screen data). Refusal keeps Accessibility off, Share mode available.

## 3. Data inventory
| Data | Origin | Stored? | Retention |
|---|---|---|---|
| Raw screen tree/text | Accessibility on tap | No | RAM only; cleared on dismiss, revocation, app switch |
| Sanitized AI prompt/response | Runtime | No | RAM only until panel closes |
| Shared text | Android Sharesheet | No | RAM only until panel closes |
| Provider baseUrl, model | Settings | Yes DataStore | Until user removes |
| API key | User | Yes encrypted at rest | Until user removes; re-entry if key invalidated |
| Excluded apps and persona | User | Yes Room | Until user removes |
| Token count / request status | LLM | Optional local aggregate | Never include text, IDs of messages or prompts |

## 4. Exclusions & failure behavior
- Self app is excluded from screen capture.
- Sensitive apps categories should be **default-deny where reliably identified** (bank, wallet, password manager, authenticator). Cannot guarantee all such apps identified: initial list is not a substitute for user configuration.
- Allow user to exclude any observed app manually. Default to reject unknown/blank package names.
- Never bypass secure views, password nodes, Android access restrictions, or lack of exposed accessible text.
- Show `Không thể đọc nội dung ứng dụng này. Hãy chọn văn bản và dùng Chia sẻ nếu được hỗ trợ.`

## 5. Redaction
- Local best-effort patterns: one-time passcodes with contextual anchors, likely card numbers (validated where appropriate), API tokens/Bearer headers, credential patterns, email, phone. Generic 4–8 digit numbers should **not** all be deleted: dates/amounts often match.
- Default sensitive categories masked in payload and preview (`[REDACTED_OTP]`, etc.). No promise of complete PII removal.
- User reviews exact final outgoing content (sanitized text + action instruction summary + target provider). Request only after confirm.
- Unknown content may still be sensitive; user must be able to cancel. In highly sensitive contexts disable AI action altogether.

## 6. Key management
- Generate/retrieve AES key in Android Keystore; encrypt API key using AES-GCM with random 12-byte IV per write; persist `{version, IV, ciphertext}` in app-private encrypted file or private preferences. Never log key or decrypted bytes.
- If device lock/reset invalidates a key, clear corrupt ciphertext and prompt re-entry; never silently revert to plaintext.
- No embedded developer key or shared key in APK; BYOK personal distribution. For public release introduce authenticated backend with rate limits instead.
- **Exception (ADR-029, debug only):** a developer may put their own key in the gitignored repo-root `.env`; Gradle copies it into `BuildConfig` for the `debug` build type only (release gets empty strings). Keys in a debug APK are extractable: never share such an APK. Only `.env.example` (empty keys) is committed; `scripts/audit.sh` fails if `.env` is tracked or the template contains a key. In-app Keystore entry (M3) overrides `.env` values and remains the release path.
- Reject plaintext HTTP providers. TLS certificate validation must not be disabled. No trust-all certificate, no debug MITM in release.

## 7. Prompt injection / output handling
- Wrap captured text inside explicit untrusted-data delimiters; instruct model to ignore instructions within screen text, but assume defense is imperfect.
- No arbitrary tool calls or interpreted model output as UI actions. Reply JSON is parsed into *display text only*, not commands.
- Display result as text (no untrusted HTML/JS execution, unsafe Markdown links or auto open URL).
- Never execute "send", "pay", "click", "delete", "grant permission" based on LLM output.

## 8. Insertion safeguards
- Explicit opt-in each insert; no send automation.
- On input change, focus change, package/window change, password field or unknown cursor: abort insertion; keep result available to copy.
- Treat `ACTION_SET_TEXT` as replace-all; when draft nonempty, copy-by-default unless separate Replace confirmation is shown. Selection-based insertion is not promised for MVP.
- Copying to clipboard can expose text via Android system UI and other user tools; communicate this in help text.

## 9. Network and logging
- HTTP request body never logged. Debug logs restricted to counts, operation duration, high-level error enums and stage transitions without app/window contents.
- Reject unknown URL schemes and malformed host. Store provider URL and chosen model as metadata; user may configure third-party providers at their own risk.
- Scrub errors before UI/logs: some provider error bodies may echo prompt or key. Show generic UI plus HTTP code/category only.
- Network request cancellation must free response stream and coroutine; no silent retry for request that could already be charged.

## 10. Abuse, policy and future distribution
- Sideload personal-use MVP still requires transparent disclosure and voluntary permission. On Android 13+ restricted setting UX may apply to sideloaded Accessibility app; present official Settings navigation, not bypass instructions.
- Google Play distribution later requires separate permission use-case review, Accessibility declarations, prominent disclosure/consent, privacy policy and current store policy verification. Do not assume an app not primarily intended for accessibility assistance will be approved.
- Do not add autonomous UI operation or impersonation behavior without separate privacy/platform review.

## 11. Security verification checklist
- [ ] Network inspector shows **zero** requests before Send confirmation.
- [ ] logcat and crash logs contain no screen text, API key, Authorization header or prompts.
- [ ] No plaintext key or screen content in app-private files/Room/DataStore.
- [ ] Excluded app and unknown package are fail-closed.
- [ ] Input draft preserved on abort, on app switch and on insert errors.
- [ ] Service disable removes overlay and clears active content.
- [ ] Key rotation/invalidation requires safe re-entry.
- [ ] Non-HTTPS base URL rejected in release build.
