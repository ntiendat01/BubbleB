package com.example.contextbubble.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Lifecycle / ViewModelStore / SavedStateRegistry owner for a ComposeView hosted in a
 * WindowManager overlay (no Activity). One instance per attached overlay view; must be moved to
 * RESUMED before `addView` and destroyed exactly once after `removeView`.
 */
class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    val isDestroyed: Boolean get() = registry.currentState == Lifecycle.State.DESTROYED

    /** INITIALIZED -> CREATED -> STARTED -> RESUMED. */
    fun createAndResume() {
        check(registry.currentState == Lifecycle.State.INITIALIZED) { "Owner already used" }
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    /** RESUMED -> ... -> DESTROYED; idempotent. Clears the ViewModelStore. */
    fun destroy() {
        if (isDestroyed) return
        val state = registry.currentState
        if (state == Lifecycle.State.INITIALIZED) {
            // Never created: LifecycleRegistry cannot go INITIALIZED -> DESTROYED via events.
            store.clear()
            return
        }
        if (state.isAtLeast(Lifecycle.State.RESUMED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        if (state.isAtLeast(Lifecycle.State.STARTED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
