package com.example.bubbleb.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.domain.llm.*
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.usecase.PayloadPreview
import com.example.bubbleb.presentation.*
import com.example.bubbleb.ui.overlay.PanelCallbacks
import com.example.bubbleb.ui.overlay.PanelContent
import com.example.bubbleb.ui.theme.BubbleBTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AiPanelContentTest {
    @get:Rule val rule = createComposeRule()
    private val preview = PayloadPreview(AiAction.TRANSLATE, "Fixture", "fixture", "Synthetic reviewed text", 23,
        emptyMap(), false, false)
    private var state by mutableStateOf<PanelState>(PanelState.PreviewConsent(preview, true,
        ProviderStatus(ProviderConfig(), true, KeyIssue.NONE, ProviderSource.SAVED, 1)))
    private var sends = 0
    private var confirms = 0
    private val callbacks = object : PanelCallbacks {
        override fun onClose() = Unit
        override fun onAction(action: AiAction) = Unit
        override fun onPreviewCancel() = Unit
        override fun onReplyAction(operation: ReplyOperation, index: Int) = Unit
        override fun onReplyConfirm() = Unit
        override fun onReplyCancel() = Unit
        override fun onSend() { sends++ }
        override fun onTranslationSelected(language: TranslationLanguage) {
            state = (state as PanelState.PreviewConsent).copy(language = language)
        }
        override fun onOutputReview() = Unit
        override fun onOutputCopyRequest() { state = (state as PanelState.Output).copy(copyPending = true) }
        override fun onOutputCopyConfirm() { confirms++ }
        override fun onOutputCopyCancel() { state = (state as PanelState.Output).copy(copyPending = false) }
    }
    private fun show() = rule.setContent { BubbleBTheme { PanelContent(state, "", 600.dp, callbacks) } }

    @Test fun previewShowsProviderExactModelAndTranslationChoice_sendRequiresTap() {
        show()
        rule.onNodeWithText(ProviderPreset.MODEL).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Tiếng Anh").performScrollTo().performClick()
        assertEquals(TranslationLanguage.ENGLISH, (state as PanelState.PreviewConsent).language)
        assertEquals(0, sends)
        rule.onNodeWithText("Gửi đến AI").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, sends)
    }

    @Test fun completedOutputCopyRequiresWarningAndConfirmation() {
        state = PanelState.Output("Fixture", AiAction.EXPLAIN, "Synthetic result", loading = false)
        show()
        rule.onNodeWithText("Sao chép").performScrollTo().performClick()
        assertEquals(0, confirms)
        rule.onNodeWithText("Xác nhận sao chép").performScrollTo().performClick()
        assertEquals(1, confirms)
    }
}
