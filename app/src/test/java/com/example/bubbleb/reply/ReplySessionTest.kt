package com.example.bubbleb.reply

import com.example.bubbleb.data.reply.ReplyJsonParser
import com.example.bubbleb.domain.reply.InsertionResult
import com.example.bubbleb.domain.reply.ReplyClipboard
import com.example.bubbleb.domain.reply.TextInserter
import com.example.bubbleb.presentation.ReplyFeedback
import com.example.bubbleb.presentation.ReplyOperation
import com.example.bubbleb.presentation.ReplySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplySessionTest {
    private val session = ReplySession(emptySnapshot(), ReplyJsonParser())
    private var writes = 0
    private var copies = 0
    private val inserter = TextInserter { _, _ -> writes++; InsertionResult.VERIFIED }
    private val clipboard = ReplyClipboard { copies++; true }

    private fun load() { session.accept(replyJson(), 1_100) }

    @Test fun requestOnlyShowsConfirmation_cancelNeverWritesOrCopies() {
        load()
        session.request(ReplyOperation.INSERT, 0)
        assertEquals(0, writes)
        assertEquals(0, copies)
        assertEquals(ReplyOperation.INSERT, session.state.pending!!.operation)
        session.cancel()
        session.confirm(inserter, clipboard, true)
        assertEquals(0, writes)
        assertEquals(0, copies)
    }

    @Test fun copyRequiresConfirmationAndNeverCallsInserter() {
        load()
        session.request(ReplyOperation.COPY, 1)
        assertEquals(0, copies)
        session.confirm(inserter, clipboard, true)
        session.confirm(inserter, clipboard, true)
        assertEquals(1, copies)
        assertEquals(0, writes)
        assertEquals(ReplyFeedback.COPIED, session.state.feedback)
    }

    @Test fun insertRequiresConfirmation_andCannotRepeatAfterAttempt() {
        load()
        session.request(ReplyOperation.INSERT, 2)
        session.confirm(inserter, clipboard, true)
        session.request(ReplyOperation.INSERT, 2)
        session.confirm(inserter, clipboard, true)
        assertEquals(1, writes)
        assertEquals(0, copies)
        assertFalse(session.state.canInsert)
        assertEquals(ReplyFeedback.INSERTED, session.state.feedback)
        assertNull(session.state.pending)
    }

    @Test fun failedOrUncertainInsertion_keepsCopyWithoutAutomaticClipboardFallback() {
        load()
        session.request(ReplyOperation.INSERT, 0)
        session.confirm(TextInserter { _, _ -> InsertionResult.UNVERIFIED }, clipboard, true)
        assertEquals(ReplyFeedback.INSERT_UNVERIFIED, session.state.feedback)
        assertEquals(0, copies)
        session.request(ReplyOperation.COPY, 0)
        session.confirm(inserter, clipboard, false)
        assertEquals(1, copies)
    }

    @Test fun excludedOrLockedContext_preventsWriteEvenAfterConfirmation() {
        load()
        session.request(ReplyOperation.INSERT, 0)
        session.confirm(inserter, clipboard, allowedToInsert = false)
        assertEquals(0, writes)
        assertEquals(0, copies)
        assertEquals(ReplyFeedback.INSERT_ABORTED, session.state.feedback)
    }

    @Test fun malformedOutputAndExistingDrafts_areCopyOnly() {
        session.accept("malformed", 1_100)
        session.request(ReplyOperation.INSERT, 0)
        assertNull(session.state.pending)
        session.request(ReplyOperation.COPY, 0)
        session.confirm(inserter, clipboard, true)
        assertEquals(1, copies)
        assertEquals(0, writes)
        val draftSession = ReplySession(emptySnapshot().copy(text = "preserve draft"), ReplyJsonParser())
        draftSession.accept(replyJson(), 1_100)
        assertFalse(draftSession.state.canInsert)
    }

    @Test fun outOfRangeIndexAndMissingConfirmation_areIgnored() {
        load()
        session.request(ReplyOperation.COPY, -1)
        session.request(ReplyOperation.INSERT, 3)
        session.confirm(inserter, clipboard, true)
        assertEquals(0, copies)
        assertEquals(0, writes)
        assertTrue(session.state.canInsert)
    }

    @Test fun clipboardErrorsAreSanitizedAndDoNotFallBackToInsertion() {
        load()
        session.request(ReplyOperation.COPY, 0)
        session.confirm(inserter, ReplyClipboard { error("synthetic private details") }, true)
        assertEquals(ReplyFeedback.COPY_FAILED, session.state.feedback)
        assertEquals(0, writes)
    }
}
