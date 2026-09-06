package com.app.snapmind.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Stands in for a thumbnail whose source image is gone -- most often a screenshot the user
 * deleted from the gallery after SnapMind already saved the row.
 *
 * The row itself is never deleted for this (spec.md 7.2/11.11): the note and OCR text are
 * often the part worth keeping, and they outlive the picture. This is just the card admitting
 * the image can't be shown, instead of showing a permanently "loading" blank.
 *
 * Muted, not alarmed: onSurfaceVariant at low alpha, no error colour, no exclamation mark.
 * Deleting your own screenshot is an ordinary, expected thing to do -- not a fault, and
 * nothing urgent.
 */
@Composable
fun MissingImagePlaceholder(modifier: Modifier = Modifier, iconSize: Dp = 28.dp) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ImageNotSupported,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize)
        )
    }
}
