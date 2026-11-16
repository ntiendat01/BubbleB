package com.example.contextbubble.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.contextbubble.R
import com.example.contextbubble.domain.BubbleDefaults

/**
 * Collapsed bubble visual. Touch/drag is handled by the overlay adapter with raw coordinates;
 * this composable exposes the TalkBack label and click action only.
 */
@Composable
fun BubbleContent(onActivate: () -> Unit) {
    val label = stringResource(R.string.bubble_content_description)
    Surface(
        modifier = Modifier
            .size(BubbleDefaults.SIZE_DP.dp)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label = label) {
                    onActivate()
                    true
                }
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_bubble),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
