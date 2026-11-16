package com.example.bubbleb.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bubbleb.data.reply.ReplyJsonParser
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.reply.ReplyLimits
import com.example.bubbleb.domain.reply.ReplyOutput
import com.example.bubbleb.presentation.PanelState
import com.example.bubbleb.presentation.PendingReplyAction
import com.example.bubbleb.presentation.ReplyOperation
import com.example.bubbleb.presentation.ReplyUiState
import com.example.bubbleb.reply.replyJson
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
class ReplyResultContentTest {
    @get:Rule val rule = createComposeRule()
    private var ui by mutableStateOf(ReplyUiState(ReplyJsonParser().parse(replyJson()), true))
    private var confirmations = 0
    private val callbacks = object : PanelCallbacks {
        override fun onClose() = Unit
        override fun onAction(action: AiAction) = Unit
        override fun onPreviewCancel() = Unit
        override fun onReplyAction(operation: ReplyOperation, index: Int) {
            ui = ui.copy(pending = PendingReplyAction(operation, index))
        }
        override fun onReplyConfirm() { confirmations++; ui = ui.copy(pending = null) }
        override fun onReplyCancel() { ui = ui.copy(pending = null) }
    }

    private fun show() = rule.setContent {
        BubbleBTheme { PanelContent(PanelState.Replies("Fixture", ui), "", 600.dp, callbacks) }
    }

    @Test fun validResultHasThreeOptionsAndExplicitInsertConfirmation() {
        show()
        ReplyLimits.LABELS.forEach { rule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        rule.onAllNodesWithText("Sao chép").assertCountEquals(3)
        rule.onNodeWithTag("reply_insert_0").performScrollTo().performClick()
        assertEquals(0, confirmations)
        rule.onNodeWithText("Xác nhận chèn").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Hủy").performScrollTo().performClick()
        assertEquals(0, confirmations)
        rule.onNodeWithTag("reply_copy_0").performScrollTo().performClick()
        rule.onNodeWithText("Xác nhận sao chép").performScrollTo().performClick()
        assertEquals(1, confirmations)
    }

    @Test fun invalidJsonHasCopyButNoInsert() {
        ui = ReplyUiState(ReplyOutput.CopyOnly("Synthetic plain text"), canInsert = false)
        show()
        rule.onNodeWithText("Synthetic plain text").assertIsDisplayed()
        rule.onNodeWithTag("reply_copy_0").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("reply_insert_0").assertDoesNotExist()
        rule.onNodeWithTag("reply_copy_0").performClick()
        rule.onNodeWithText("Xác nhận sao chép").performScrollTo().assertIsDisplayed()
        assertEquals(0, confirmations)
    }
}
