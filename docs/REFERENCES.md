# REFERENCES — Android verification links

Reviewed for design on 2026-11-16. Re-check official documentation during implementation; URLs may evolve.

- [AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) — lifecycle, active window and OS service behavior.
- [AccessibilityServiceInfo API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo) — content retrieval, capabilities and `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`.
- [AccessibilityNodeInfo API](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo) — action capability, active editable node and caveats.
- [AccessibilityNodeInfo.AccessibilityAction](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo.AccessibilityAction) — `ACTION_SET_TEXT`, `ACTION_SET_SELECTION` semantics.
- [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams) — `TYPE_ACCESSIBILITY_OVERLAY` and window flags.
- [Android Sharesheet / receiving simple data](https://developer.android.com/training/sharing/receive) — `ACTION_SEND`, text/plain.
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore) — secure cryptographic key management.
- [Google Play Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491) — possible later public release requirements.
- [DataStore guide](https://developer.android.com/topic/libraries/architecture/datastore) — non-secret settings persistence.

## Spec lineage
The original user-provided `Spec ContextBubble – Bong bóng AI đọc ngữ cảnh màn hình.md`, version 0.1 (2026-11-16), is included in this package under `docs/ORIGINAL_SPEC_v0.1.md` for traceability. Version 0.2 preserves core concept, tech stack and persona-per-app behavior while changing privacy gate, insertion safety and milestones.
