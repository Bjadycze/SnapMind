package com.app.snapmind.presentation.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.app.snapmind.R
import com.app.snapmind.domain.classify.DetectedCategory
import com.app.snapmind.presentation.theme.LocalSnapMindPalette
import kotlinx.coroutines.launch

/** Resting width of the tab. The card reserves exactly this much on its right edge. */
val CategoryTabWidth: Dp = 30.dp

/**
 * Everything tunable about the strip, in one block -- same rule as FilterTabs (spec.md 11.4).
 *
 * The strip may not introduce a colour of its own: Palette.kt is the only source of colour in
 * the app (spec.md 11.13). So instead of picking a hex, the fill is the card's own colour
 * nudged towards `outline`, which is a mid-tone in every palette and both modes. That is why
 * this survives a palette swap where a fixed role does not: `surfaceRaisedHigh` *is* the card,
 * and in the light palettes `surfaceRaised` is a hair off white, so both vanish into it.
 *
 * To tune:
 *  - strip too faint  -> raise TabTint (0.30f is clearly visible, 0.5f is loud)
 *  - strip too heavy  -> lower TabTint towards 0.10f
 *  - divider too weak -> raise DividerAlpha towards 1f, or DividerWidth to 1.5.dp
 *  - want the strip to sit *behind* the card instead of on it -> replace the fill expression
 *    in `restingStrip` with `palette.panel` (the colour of the list behind the card)
 */
private const val TabTint = 0.45f       // jak moc se výplň liší od karty
private const val DividerAlpha = 0.9f   // síla dělicí čáry
private val DividerWidth: Dp = 1.5.dp     // tloušťka čáry
private const val SelectedTint = 0.55f  // výplň štítku, když je podle něj filtrováno

/**
 * Filter key for everything the classifier could not place: a null category, UNKNOWN, or a
 * value stored by a future version this build does not recognise. They are one group, because
 * to the user they are one pile -- the items still waiting for a category.
 *
 * Not a real `DetectedCategory` value, so it can never collide with one.
 */
const val UnclassifiedFilterKey = "__unclassified__"

/** The value a tap on this item's strip filters by. Never null: everything belongs somewhere. */
fun categoryFilterKey(category: String?): String {
    val parsed = category?.let { runCatching { DetectedCategory.valueOf(it) }.getOrNull() }
    return parsed?.takeIf { it != DetectedCategory.UNKNOWN }?.name ?: UnclassifiedFilterKey
}

/** True when the item belongs in the currently filtered group. A null filter matches everything. */
fun matchesCategoryFilter(category: String?, filter: String?): Boolean =
    filter == null || categoryFilterKey(category) == filter

/**
 * The category from Task 7, drawn as a vertical strip on the card's right edge, and the
 * gesture that settles the item by dragging that strip to the left.
 *
 * Why the strip and not a chip in the metadata row: it never competes with the note text,
 * it grows with the card, and it carries no colour of its own beyond `accentMuted`, so it
 * survives all six palettes without a new value in Palette.kt (spec.md 11.13).
 *
 * The gesture is its own reward, which is the one thing that makes it legal under spec.md
 * 11.8: the colour, the word and the vibration all happen *while the finger is down*. There
 * is no animation after release -- there could not be one, because in the Aktivní filter the
 * card leaves the list the instant it settles, and the animation would play on a composable
 * already being disposed. That failure is recorded in 11.8 and this avoids it by construction.
 *
 * Nothing here counts anything (spec.md 7.3): the strip shows what an item *is*, never how
 * many of them there are.
 */
@Composable
fun CategoryEdgeTab(
    category: String?,
    settled: Boolean,
    cardWidth: Dp,
    /** null = this item cannot be settled by dragging (already settled, or the Vyřízené filter). */
    onSettle: (() -> Unit)?,
    /** Tapping filters the list by this category. Null keeps the strip inert to taps. */
    onFilter: (() -> Unit)? = null,
    /** The list is currently filtered by this category, so the strip shows itself as the reason. */
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalSnapMindPalette.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val label = categoryLabel(category)
    val drag = remember { Animatable(0f) }

    val restPx = with(density) { CategoryTabWidth.toPx() }
    val cardWidthPx = with(density) { cardWidth.toPx() }

    // Half the card, not 70%: past half you have to shift your grip, and a gesture that needs
    // a second hand is slower than opening the detail and tapping the fajfka.
    val confirmPx = (cardWidthPx * CONFIRM_FRACTION - restPx).coerceAtLeast(restPx)
    // A fast flick counts earlier -- the intent is already unmistakable at that speed.
    val flingPx = (cardWidthPx * FLING_FRACTION - restPx).coerceAtLeast(0f)

    val width = restPx + drag.value
    val armed = drag.value >= confirmPx

    // The category fades out over the first tab-width of travel, "Hotovo?" fades in over the
    // rest, and full brightness lands exactly on the confirm threshold -- so the colour *is*
    // the indicator, and no separate progress bar is needed (that would be a counter, 7.3).
    val fadeOut = ((width - restPx) / restPx).coerceIn(0f, 1f)
    val fadeInSpan = (confirmPx - restPx).coerceAtLeast(1f)
    val fadeIn = ((width - restPx * 2f) / fadeInSpan).coerceIn(0f, 1f)

    // The card's own colour, nudged towards `outline` -- see the tuning block at the top of the
    // file for why this rather than a palette role, and which knob to turn.
    val cardColor = if (settled) palette.settled else palette.surfaceRaisedHigh
    val restingStrip = when {
        // An empty slot stays empty -- unless it is the group being filtered, where it has to
        // show itself, because the accent is the only way back out of the filter.
        label == null && !selected -> cardColor
        // While the list is filtered by this category, its strips carry the accent -- that is
        // the only sign the filter is on, and it sits on the thing you tapped rather than
        // somewhere else on the screen. Not a badge and not a count (spec.md 7.3).
        selected -> lerp(cardColor, palette.accent, SelectedTint)
        else -> lerp(cardColor, palette.outline, TabTint)
    }
    val stripColor = lerp(restingStrip, palette.accent, fadeIn)
    val restingText = when {
        settled -> palette.onSettled
        selected -> palette.background   // on an accent fill, the ground colour is what reads
        else -> palette.accent
    }

    // Two quick pulses at the threshold, one firm one on release. Rhythm is what a phone in a
    // pocket conveys; intensity is what it conveys worst (spec.md 11.8), so the two events are
    // told apart by their pattern, not by being louder.
    LaunchedEffect(armed) {
        if (armed) vibrateTick(context)
    }

    val draggable = onSettle != null

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(with(density) { width.toDp() })
            .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
            .background(stripColor)
            // The divider is what actually separates strip from card; the fill only tints it.
            // Solid for a known category, dashed for UNKNOWN -- an empty slot, not the word
            // "unknown". Nothing is wrong with an item the classifier could not place, and one
            // day this is where the category gets written in by hand.
            .drawBehind {
                if (fadeIn >= 1f) return@drawBehind
                val stroke = DividerWidth.toPx()
                drawLine(
                    color = palette.outline.copy(alpha = DividerAlpha * (1f - fadeIn)),
                    start = Offset(stroke / 2f, 4.dp.toPx()),
                    end = Offset(stroke / 2f, size.height - 4.dp.toPx()),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                    pathEffect = if (label == null) {
                        PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                    } else {
                        null
                    }
                )
            }
            .then(
                if (onFilter != null) Modifier.clickable(onClick = onFilter) else Modifier
            )
            .then(
                if (draggable) {
                    Modifier.draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                // Leftwards is negative, and the strip only ever grows.
                                drag.snapTo((drag.value - delta).coerceIn(0f, cardWidthPx - restPx))
                            }
                        },
                        onDragStopped = { velocity ->
                            val flung = velocity <= -FLING_VELOCITY && drag.value >= flingPx
                            if (drag.value >= confirmPx || flung) {
                                vibrateConfirm(context)
                                drag.animateTo(cardWidthPx - restPx, tween(SWEEP_MS))
                                onSettle?.invoke()
                                drag.snapTo(0f)
                            } else {
                                // Silent on the way back, exactly like the koš: a settle that
                                // did not happen must not feel like an error either.
                                drag.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (label != null && fadeOut < 1f) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = restingText,
                maxLines = 1,
                modifier = Modifier
                    .rotatedVertically()
                    .graphicsLayer { alpha = 1f - fadeOut }
            )
        }

        if (fadeIn > 0f) {
            Text(
                // The question mark answers itself: below the threshold it asks, above it it
                // states. That is the whole affordance -- no badge, no bar.
                text = stringResource(
                    if (armed) R.string.card_settle_confirm else R.string.card_settle_question
                ),
                style = MaterialTheme.typography.labelLarge,
                color = lerp(Color.Transparent, palette.background, fadeIn),
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = fadeIn }
            )
        }
    }
}

/** Czech label for a stored `DetectedCategory` name. UNKNOWN and null deliberately draw nothing. */
@Composable
private fun categoryLabel(category: String?): String? {
    val parsed = category?.let { runCatching { DetectedCategory.valueOf(it) }.getOrNull() }
    val res = when (parsed) {
        DetectedCategory.EVENT -> R.string.category_event
        DetectedCategory.RECIPE -> R.string.category_recipe
        DetectedCategory.PURCHASE -> R.string.category_purchase
        DetectedCategory.ARTICLE -> R.string.category_article
        DetectedCategory.CONTACT -> R.string.category_contact
        DetectedCategory.UNKNOWN, null -> return null
    }
    return stringResource(res)
}

/**
 * Rotation alone does not change a composable's measured size, so a rotated Text would still
 * be laid out as a wide, short box and the card would grow sideways. Measuring against
 * swapped constraints first is what keeps the strip narrow.
 */
private fun Modifier.rotatedVertically(): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                maxWidth = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.maxWidth,
                maxHeight = constraints.maxWidth
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2)
            )
        }
    }
    .graphicsLayer { rotationZ = 90f }

/**
 * `View.performHapticFeedback` is silently ignored on MagicOS (spec.md 11.8), so the vibrator
 * is driven directly. Rhythm carries through a pocket, intensity does not.
 */
private fun vibrator(context: Context): Vibrator? {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    return vibrator?.takeIf { it.hasVibrator() }
}

/** Threshold crossed: two short pulses. A single soft tick was not felt on the test device. */
private fun vibrateTick(context: Context) {
    vibrator(context)?.vibrate(
        VibrationEffect.createWaveform(
            longArrayOf(0, 14, 45, 14),
            intArrayOf(0, 255, 0, 255),
            -1
        )
    )
}

/** Settled: one firm pulse, a different shape from the double tick above. */
private fun vibrateConfirm(context: Context) {
    vibrator(context)?.vibrate(VibrationEffect.createOneShot(CONFIRM_MS, 255))
}

private const val CONFIRM_FRACTION = 0.5f
private const val FLING_FRACTION = 0.3f
private const val FLING_VELOCITY = 1000f
private const val SWEEP_MS = 140
private const val CONFIRM_MS = 40L
