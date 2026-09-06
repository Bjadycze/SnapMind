package com.app.snapmind.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.app.snapmind.R
import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.domain.model.LinkPlatform
import com.app.snapmind.presentation.theme.LocalSnapMindPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Deliberately compact: one line of text plus a thumbnail.
 *
 * Showing 80 characters of raw OCR turned every screenshot into a ten-line block of
 * fragments, so two items filled the screen. That is the visual overwhelm this app exists to
 * avoid (spec.md 7.3), and the OCR text is for search, not for display.
 *
 * A settled item fades rather than disappearing: without a visible difference between "still
 * asking" and "dealt with", there is no reason to deal with anything.
 *
 * Shared between MainScreen and SearchScreen (spec.md 11.10) -- same card, same behaviour,
 * wherever a list of items is shown.
 */
@Composable
fun CapturedItemCard(
    item: CapturedItem,
    onOpen: () -> Unit,
    /**
     * Dragging the category strip leftwards settles the item (spec.md 11.14). Null where that
     * is not allowed: an already-settled item, or the Vyřízené filter. SearchScreen passes
     * nothing, so search results keep the strip but not the gesture.
     */
    onSettle: (() -> Unit)? = null,
    /** Tapping the strip filters the list by that category. Null = the strip ignores taps. */
    onFilterCategory: (() -> Unit)? = null,
    /** The list is already filtered by this item's category. */
    categorySelected: Boolean = false
) {
    val settled = item.isResolved
    val palette = LocalSnapMindPalette.current

    val container by animateColorAsState(
        targetValue = if (settled) palette.settled else palette.surfaceRaisedHigh,
        animationSpec = tween(durationMillis = 320),
        label = "cardContainer"
    )
    // No alpha: opacity multiplies with whatever sits behind it, so the same value reads
    // differently on every palette. settled/onSettled are pre-tuned per palette instead, each
    // holding a verified 4.0-4.8:1 contrast.
    val primaryTextColor by animateColorAsState(
        targetValue = if (settled) palette.onSettled else palette.onSurface,
        animationSpec = tween(durationMillis = 320),
        label = "cardPrimaryText"
    )
    val secondaryTextColor by animateColorAsState(
        targetValue = if (settled) palette.onSettled else palette.onSurfaceFaded,
        animationSpec = tween(durationMillis = 320),
        label = "cardSecondaryText"
    )

    // BoxWithConstraints, because the settle threshold is half the card's own width and the
    // strip has to know it. matchParentSize below is what lets the strip run the full card
    // height without the card first knowing how tall it is.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth

        PressableCard(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            containerColor = container
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    // Space reserved for the strip, so the note never slides under it.
                    .padding(end = CategoryTabWidth),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Without a thumbnail an un-noted item is unidentifiable a week later.
                item.imageUri?.let { uri ->
                    // Keyed on the uri, not just the id: if link enrichment ever fills imageUri in
                    // later, a stale "failed" flag from a previous uri must not carry over to it.
                    var imageFailed by remember(item.id, uri) { mutableStateOf(false) }

                    // The thumbnail stays fully saturated even when settled: it is the only thing
                    // that still identifies a done item at a glance.
                    if (imageFailed) {
                        // The screenshot was deleted from the gallery; the row survives regardless
                        // (spec.md 7.2/11.11) -- this only admits the picture is gone.
                        MissingImagePlaceholder(
                            modifier = Modifier.size(56.dp)
                        )
                    } else {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onError = { imageFailed = true },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.userNote.ifBlank { stringResource(R.string.item_no_note) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (settled) {
                                stringResource(R.string.item_settled, formatTimestamp(item.timestamp))
                            } else {
                                formatTimestamp(item.timestamp)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryTextColor
                        )

                        // A coloured dot plus a word, not a brand logo: logos are licensed assets
                        // and would need updating every time a platform rebrands.
                        LinkPlatform.from(item.extractedText)?.let { platform ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(platform.accent)
                            )
                            Text(
                                text = platform.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = platform.accent
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            CategoryEdgeTab(
                category = item.detectedCategory,
                settled = settled,
                cardWidth = cardWidth,
                onSettle = if (settled) null else onSettle,
                onFilter = onFilterCategory,
                selected = categorySelected
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("d.M. HH:mm", Locale.getDefault()).format(Date(millis))