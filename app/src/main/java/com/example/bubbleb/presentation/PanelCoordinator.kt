package com.example.bubbleb.presentation

import com.example.bubbleb.di.OwnPackageName
import com.example.bubbleb.domain.capture.CapturePreflight
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.BubbleVisibility
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.model.ScreenContext
import com.example.bubbleb.domain.repository.AppProfileRepository
import com.example.bubbleb.domain.repository.ScreenCaptureSource
import com.example.bubbleb.domain.reply.ReplyParser
import com.example.bubbleb.domain.reply.ReplyPrompt
import com.example.bubbleb.domain.reply.ReplyClipboard
import com.example.bubbleb.domain.reply.TextInserter
import com.example.bubbleb.domain.usecase.PayloadPreviewBuilder
import com.example.bubbleb.domain.usecase.ScreenHeuristics
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Application-scoped panel controller. Capture happens only from [onBubbleTapped] (explicit user
 * tap); network transport still awaits M3. The raw [ScreenContext] lives only in memory and is
 * purged on every dismiss, app switch, lock, rotation and service disconnect.
 */
@Singleton
class PanelCoordinator @Inject constructor(
    private val bubbleCoordinator: BubbleCoordinator,
    private val appProfileRepository: AppProfileRepository,
    @param:OwnPackageName private val ownPackage: String,
    private val replyParser: ReplyParser,
) {
    private val mutableState = MutableStateFlow<PanelState>(PanelState.Collapsed)
    val state: StateFlow<PanelState> = mutableState.asStateFlow()

    private var context: ScreenContext? = null
    private var captureTruncated = false
    private var requestId = 0L
    private var replySession: ReplySession? = null

    /** Package the current snapshot belongs to, used to dismiss on app switch. */
    val capturedPackage: String? get() = context?.packageName

    /** One bounded snapshot per tap; taps while busy/open are ignored (no duplicate captures). */
    suspend fun onBubbleTapped(source: ScreenCaptureSource?) {
        if (mutableState.value != PanelState.Collapsed) return
        if (source == null || !bubbleCoordinator.serviceConnected.value) {
            mutableState.value = PanelState.Unavailable(UnavailableReason.PERMISSION_REQUIRED)
            return
        }
        val id = ++requestId
        mutableState.value = PanelState.Capturing
        val excluded = appProfileRepository.excludedPackages.first()
        val locked = bubbleCoordinator.isScreenLocked.value
        val result = try {
            source.capture { pkg -> CapturePreflight.check(pkg, ownPackage, excluded, locked) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Never fail silently: OEM accessibility stacks can throw from node APIs.
            if (id == requestId) mutableState.value = PanelState.Unavailable(UnavailableReason.CAPTURE_ERROR)
            return
        }
        if (id != requestId || mutableState.value != PanelState.Capturing) return // dismissed meanwhile
        mutableState.value = when (result) {
            is CaptureResult.Failure -> failureState(result.reason)
            is CaptureResult.Success -> {
                context = result.context
                captureTruncated = result.stats.truncatedText
                PanelState.Actions(
                    appLabel = result.context.appLabel,
                    visibleCodePoints = ScreenHeuristics.visibleCodePoints(result.context),
                    replyEligible = ScreenHeuristics.isReplyEligible(result.context),
                    screenType = ScreenHeuristics.classify(result.context),
                    truncated = result.stats.truncatedText,
                )
            }
        }
    }

    fun onActionSelected(action: AiAction) {
        val current = mutableState.value as? PanelState.Actions ?: return
        val ctx = context ?: return dismiss()
        if (action == AiAction.REPLY && !current.replyEligible) return
        mutableState.value = PanelState.PreviewConsent(
            preview = PayloadPreviewBuilder.build(ctx, action, captureTruncated),
            providerConfigured = false,
        )
    }

    /** Back from preview to the action list without re-capturing. */
    fun onPreviewBack() {
        if (mutableState.value !is PanelState.PreviewConsent) return
        val ctx = context ?: return dismiss()
        mutableState.value = PanelState.Actions(
            appLabel = ctx.appLabel,
            visibleCodePoints = ScreenHeuristics.visibleCodePoints(ctx),
            replyEligible = ScreenHeuristics.isReplyEligible(ctx),
            screenType = ScreenHeuristics.classify(ctx),
            truncated = captureTruncated,
        )
    }

    /**
     * M3 must call this from explicit Send, with provider readiness resolved locally. No network
     * client exists yet; M2's disabled Send button never calls this integration entry point.
     */
    suspend fun onReplySendConfirmed(providerReady: Boolean): ReplyRequest? {
        val preview = mutableState.value as? PanelState.PreviewConsent ?: return null
        val ctx = context ?: return null
        if (!providerReady || preview.action != AiAction.REPLY || preview.preview.mostlyRedacted ||
            preview.preview.codePoints == 0 || !bubbleCoordinator.serviceConnected.value || bubbleCoordinator.isScreenLocked.value) return null
        val allowed = isContextAllowed(ctx.packageName)
        if (mutableState.value !== preview || !allowed) return null
        val id = ++requestId
        replySession = ReplySession(ctx.focusedInput, replyParser)
        mutableState.value = PanelState.AwaitingReply(ctx.appLabel, id)
        return ReplyRequest(id, ReplyPrompt.instruction, preview.preview.text)
    }

    /** Only a complete response for the current request can become an actionable result. */
    fun onReplyResponse(id: Long, response: String, nowElapsedMs: Long) {
        val waiting = mutableState.value as? PanelState.AwaitingReply ?: return
        if (id != requestId || id != waiting.requestId) return
        if (!bubbleCoordinator.serviceConnected.value || bubbleCoordinator.isScreenLocked.value) return dismiss()
        val session = replySession ?: return
        session.accept(response, nowElapsedMs)
        mutableState.value = PanelState.Replies(waiting.appLabel, session.state)
    }

    fun onReplyAction(operation: ReplyOperation, index: Int) {
        val current = mutableState.value as? PanelState.Replies ?: return
        val session = replySession ?: return
        session.request(operation, index)
        mutableState.value = current.copy(reply = session.state)
    }

    fun onReplyActionCancel() {
        val current = mutableState.value as? PanelState.Replies ?: return
        val session = replySession ?: return
        session.cancel()
        mutableState.value = current.copy(reply = session.state)
    }

    suspend fun onReplyActionConfirm(inserter: TextInserter?, clipboard: ReplyClipboard?) {
        val current = mutableState.value as? PanelState.Replies ?: return
        val pending = current.reply.pending ?: return
        val session = replySession ?: return
        val contextAllowed = isContextAllowed(context?.packageName)
        // Reads above may suspend: dismiss, cancel, app switch or another confirmation wins.
        if (mutableState.value !== current || replySession !== session) return
        if (!contextAllowed) {
            dismiss()
            return
        }
        val allowed = pending.operation == ReplyOperation.INSERT
        session.confirm(inserter, clipboard, allowed)
        mutableState.value = current.copy(reply = session.state)
    }

    /** Close/Cancel/Back and every lifecycle trigger: purge snapshot and collapse. */
    fun dismiss() {
        requestId++
        context = null
        captureTruncated = false
        replySession = null
        mutableState.value = PanelState.Collapsed
    }

    /** Foreground changed: any open panel for a different app is closed and purged. */
    fun onForegroundChanged(packageName: String?) {
        if (mutableState.value == PanelState.Collapsed) return
        if (mutableState.value == PanelState.Capturing) return // capture validates package itself
        val captured = context?.packageName
        if (captured == null || packageName != captured) dismiss()
    }

    /** Metadata only: invalidate even if the user later restores the original draft/cursor. */
    fun onEditorChanged(packageName: String?) {
        if ((context != null || mutableState.value == PanelState.Capturing) && packageName != ownPackage) dismiss()
    }

    fun onWindowChanged(packageName: String?, windowId: Int) {
        if (packageName == ownPackage) return // Our non-focusable overlay is not an app switch.
        if (mutableState.value == PanelState.Capturing) return dismiss()
        val ctx = context ?: return
        if (packageName != ctx.packageName || windowId < 0 || windowId != ctx.focusedInput?.target?.windowId) dismiss()
    }

    private suspend fun isContextAllowed(packageName: String?): Boolean = try {
        val excluded = appProfileRepository.excludedPackages.first()
        val visible = bubbleCoordinator.bubbleVisibility.first() is BubbleVisibility.Visible
        visible && bubbleCoordinator.serviceConnected.value &&
            CapturePreflight.check(packageName, ownPackage, excluded, bubbleCoordinator.isScreenLocked.value) == null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false // Settings/database failure must never enable a write or expose provider errors.
    }

    /** Every failure maps to a visible panel message (never silence). */
    private fun failureState(reason: CaptureFailure): PanelState = when (reason) {
        CaptureFailure.EXCLUDED_APP -> PanelState.Blocked(BlockedReason.EXCLUDED_APP, reason)
        CaptureFailure.OWN_APP -> PanelState.Blocked(BlockedReason.OWN_APP, reason)
        CaptureFailure.SCREEN_LOCKED -> PanelState.Blocked(BlockedReason.SCREEN_LOCKED, reason)
        CaptureFailure.SENSITIVE_SCREEN -> PanelState.Blocked(BlockedReason.SENSITIVE_SCREEN, reason)
        CaptureFailure.NO_ROOT, CaptureFailure.UNKNOWN_PACKAGE, CaptureFailure.NO_TEXT ->
            PanelState.Unavailable(UnavailableReason.NO_ACCESSIBLE_CONTENT, reason)
    }

    /** Content-free description of the current state for `BB/Panel` stage logs. */
    fun stageOf(state: PanelState): String = when (state) {
        PanelState.Collapsed -> "collapsed"
        PanelState.Capturing -> "capturing"
        is PanelState.Actions -> "actions:reply=${state.replyEligible},type=${state.screenType}"
        is PanelState.PreviewConsent -> "preview:${state.action},redactions=${state.preview.redactionCounts.values.sum()}"
        is PanelState.AwaitingReply -> "awaiting_reply"
        is PanelState.Replies -> "reply_result"
        is PanelState.Unavailable -> "unavailable:${state.reason},cause=${state.cause}"
        is PanelState.Blocked -> "blocked:${state.reason},cause=${state.cause}"
    }
}
