package com.example.bubbleb.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.bubbleb.ui.theme.BubbleBTheme
import com.example.bubbleb.presentation.BubbleCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Hosts onboarding/dashboard and settings. Share intake is added in M5. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var bubbleCoordinator: BubbleCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BubbleBTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }

    // Lets the service tell "our Activity is in front" apart from "our overlay window was touched".
    override fun onStart() {
        super.onStart()
        bubbleCoordinator.onOwnUiVisibilityChanged(true)
    }

    override fun onStop() {
        bubbleCoordinator.onOwnUiVisibilityChanged(false)
        super.onStop()
    }
}
