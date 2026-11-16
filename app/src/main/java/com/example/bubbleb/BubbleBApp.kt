package com.example.bubbleb

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; Hilt graph root. Holds no screen data. */
@HiltAndroidApp
class BubbleBApp : Application()
