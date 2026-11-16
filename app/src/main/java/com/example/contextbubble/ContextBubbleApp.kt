package com.example.contextbubble

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; Hilt graph root. Holds no screen data. */
@HiltAndroidApp
class ContextBubbleApp : Application()
