package com.example.bubbleb.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.ScreenType
import com.example.bubbleb.domain.privacy.Redactor
import com.example.bubbleb.domain.usecase.PayloadPreview
import com.example.bubbleb.presentation.BlockedReason
import com.example.bubbleb.presentation.PanelState
import com.example.bubbleb.presentation.ReplyOperation
import com.example.bubbleb.presentation.UnavailableReason
import com.example.bubbleb.ui.overlay.PanelCallbacks
import com.example.bubbleb.ui.overlay.PanelContent
import com.example.bubbleb.ui.theme.BubbleBTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric Compose test of the overlay panel (JVM, not a device test). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PanelContentTest {
    @get:Rule val rule = createComposeRule()

    private val actions = mutableListOf<AiAction>()
    private var closes = 0
    private var cancels = 0
    private val callbacks = object : PanelCallbacks {
        override fun onClose() { closes++ }
        override fun onAction(action: AiAction) { actions += action }
        override fun onPreviewCancel() { cancels++ }
        override fun onReplyAction(operation: ReplyOperation, index: Int) = Unit
        override fun onSend() = Unit
        override fun onTranslationSelected(language: com.example.bubbleb.domain.llm.TranslationLanguage) = Unit
        override fun onOutputReview() = Unit
        override fun onOutputCopyRequest() = Unit
        override fun onOutputCopyConfirm() = Unit
        override fun onOutputCopyCancel() = Unit
        override fun onReplyConfirm() = Unit
        override fun onReplyCancel() = Unit
    }

    private fun show(state: PanelState) = rule.setContent {
        BubbleBTheme { PanelContent(state, appLabelFallback = "", maxHeight = 600.dp, callbacks = callbacks) }
    }

    @Test fun actions_replyDisabledWhenNotEligible_othersClickable() {
        show(PanelState.Actions("Zalo", 120, replyEligible = false, screenType = ScreenType.UNKNOWN, truncated = false))
        rule.onNodeWithText("BubbleB · Zalo").assertIsDisplayed()
        rule.onNodeWithText("Đã đọc 120 ký tự").assertIsDisplayed()
        rule.onNodeWithText("Trả lời").assertIsNotEnabled()
        rule.onNodeWithText("Tóm tắt").performClick()
        rule.onNodeWithText("Dịch").performScrollTo().performClick()
        assertEquals(listOf(AiAction.SUMMARIZE, AiAction.TRANSLATE), actions)
        rule.onNodeWithContentDescription("Đóng bảng BubbleB").performClick()
        assertEquals(1, closes)
    }

    @Test fun review_showsRedactedText_sendDisabled_cancelWorks() {
        val preview = PayloadPreview(
            action = AiAction.SUMMARIZE, appLabel = "Zalo", packageName = "p",
            text = "Gọi [REDACTED_PHONE] nhé", codePoints = 24,
            redactionCounts = mapOf(Redactor.Category.PHONE to 1), truncated = false, mostlyRedacted = false,
        )
        show(PanelState.PreviewConsent(preview, providerConfigured = false))
        rule.onNodeWithText("Kiểm tra nội dung gửi đến AI").assertIsDisplayed()
        rule.onNodeWithText("Gọi [REDACTED_PHONE] nhé").assertIsDisplayed()
        rule.onNodeWithText("Đã che dữ liệu nhạy cảm").assertIsDisplayed()
        rule.onNodeWithText("24 ký tự").assertIsDisplayed()
        rule.onNodeWithText("Gửi đến AI").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Hủy").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, cancels)
    }

    @Test fun errorStates() {
        show(PanelState.Blocked(BlockedReason.EXCLUDED_APP))
        rule.onNodeWithText("Ứng dụng này được loại trừ để bảo vệ quyền riêng tư").assertIsDisplayed()
    }

    @Test fun unavailableShowsShareHint() {
        show(PanelState.Unavailable(UnavailableReason.NO_ACCESSIBLE_CONTENT))
        rule.onNodeWithText("Không đọc được nội dung màn hình này").assertIsDisplayed()
    }
}
