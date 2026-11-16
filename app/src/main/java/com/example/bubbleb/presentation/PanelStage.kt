package com.example.bubbleb.presentation

/** Logging allowlist: enums/booleans/counts only; never serialize a state carrying user text. */
internal object PanelStage {
    fun describe(state: PanelState): String = when (state) {
        PanelState.Collapsed -> "collapsed"
        PanelState.Capturing -> "capturing"
        is PanelState.Actions -> "actions:reply=${state.replyEligible},type=${state.screenType}"
        is PanelState.PreviewConsent -> "preview:${state.action},redactions=${state.preview.redactionCounts.values.sum()}"
        is PanelState.AwaitingReply -> "awaiting_reply"
        is PanelState.Replies -> "reply_result"
        is PanelState.Output -> "output:loading=${state.loading},error=${state.error}"
        is PanelState.Unavailable -> "unavailable:${state.reason},cause=${state.cause}"
        is PanelState.Blocked -> "blocked:${state.reason},cause=${state.cause}"
    }
}
