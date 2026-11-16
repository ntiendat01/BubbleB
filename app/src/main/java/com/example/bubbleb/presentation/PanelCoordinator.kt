package com.example.bubbleb.presentation

import com.example.bubbleb.di.OwnPackageName
import com.example.bubbleb.domain.capture.CapturePreflight
import com.example.bubbleb.domain.model.AiAction
import com.example.bubbleb.domain.model.CaptureFailure
import com.example.bubbleb.domain.model.CaptureResult
import com.example.bubbleb.domain.model.ScreenContext
import com.example.bubbleb.domain.repository.AppProfileRepository
import com.example.bubbleb.domain.repository.ScreenCaptureSource
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
 * tap); there is no network path at all in M2. The raw [ScreenContext] lives only in memory and is
 * purged on every dismiss, app switch, lock, rotation and service disconnect.
 */
@Singleton
class PanelCoordinator @Inject constructor(
    private val bubbleCoordinator: BubbleCoordinator,
    private val appProfileRepository: AppProfileRepository,
    @param:OwnPackageName private val ownPackage: String,
) {
    private val mutableState = MutableStateFlow<PanelState>(PanelState.Collapsed)
    val state: StateFlow<PanelState> = mutableState.asStateFlow()

    private var context: ScreenContext? = null
    private var captureTruncated = false
    private var requestId = 0L

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

    /** Close/Cancel/Back and every lifecycle trigger: purge snapshot and collapse. */
    fun dismiss() {
        requestId++
        context = null
        captureTruncated = false
        mutableState.value = PanelState.Collapsed
    }

    /** Foreground changed: any open panel for a different app is closed and purged. */
    fun onForegroundChanged(packageName: String?) {
        if (mutableState.value == PanelState.Collapsed) return
        if (mutableState.value == PanelState.Capturing) return // capture validates package itself
        val captured = context?.packageName
        if (captured == null || packageName != captured) dismiss()
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
        is PanelState.Unavailable -> "unavailable:${state.reason},cause=${state.cause}"
        is PanelState.Blocked -> "blocked:${state.reason},cause=${state.cause}"
    }
}
