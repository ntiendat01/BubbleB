package com.example.bubbleb.presentation

import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.ScreenType
import com.example.bubbleb.domain.usecase.PayloadPreview

/** Why the panel shows a "cannot read" state (UI_UX.md error table). */
enum class UnavailableReason { NO_ACCESSIBLE_CONTENT, PERMISSION_REQUIRED, CAPTURE_ERROR }

/** Why the panel refuses to read/upload for privacy reasons. */
enum class BlockedReason { EXCLUDED_APP, SENSITIVE_SCREEN, OWN_APP, SCREEN_LOCKED }

/**
 * Ephemeral panel states, including M4 results awaiting M3 transport integration. Holds only
 * display-ready output/preview; the raw ScreenContext stays private in [PanelCoordinator].
 */
sealed interface PanelState {
    data object Collapsed : PanelState
    data object Capturing : PanelState

    data class Actions(
        val appLabel: String,
        val visibleCodePoints: Int,
        val replyEligible: Boolean,
        val screenType: ScreenType,
        val truncated: Boolean,
    ) : PanelState

    data class PreviewConsent(
        val preview: PayloadPreview,
        /** No provider can be configured before M3, so sending is always disabled in M2. */
        val providerConfigured: Boolean,
    ) : PanelState {
        val action: AiAction get() = preview.action
        val canSend: Boolean get() = providerConfigured && !preview.mostlyRedacted && preview.codePoints > 0
    }

    data class AwaitingReply(val appLabel: String, val requestId: Long) : PanelState
    data class Replies(val appLabel: String, val reply: ReplyUiState) : PanelState

    /** [cause] is the exact capture failure, kept for stage logging (enum only, no content). */
    data class Unavailable(val reason: UnavailableReason, val cause: CaptureFailure? = null) : PanelState
    data class Blocked(val reason: BlockedReason, val cause: CaptureFailure? = null) : PanelState
}
