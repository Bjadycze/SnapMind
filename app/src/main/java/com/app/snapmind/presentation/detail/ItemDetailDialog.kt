package com.app.snapmind.presentation.detail

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.app.snapmind.R
import com.app.snapmind.domain.model.CapturedItem
import com.app.snapmind.presentation.components.MissingImagePlaceholder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Detail as a layer over the list, not a separate screen: the list stays visible around the
 * edges, so dismissing it feels like putting something back rather than navigating away.
 *
 * The OCR text is hidden behind a toggle. It is search material, not reading material, and
 * showing it by default turns every item into a wall of fragments -- the same mistake the
 * list card made before it was fixed.
 */
@Composable
fun ItemDetailDialog(
    items: List<CapturedItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onSaveNote: (Long, String) -> Unit,
    onDone: (Long) -> Unit,
    onDiscard: (Long) -> Unit,
    onRestore: (Long) -> Unit
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size }
    )
    val item = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]

    val context = LocalContext.current
    var note by remember(item.id) { mutableStateOf(item.userNote) }
    var showOcr by remember(item.id) { mutableStateOf(false) }

    // Swiping away is the same as closing: an unsaved note must not vanish with the page.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { }
    }

    Dialog(
        onDismissRequest = {
            // Saving on dismiss rather than behind a button: a half-typed note that vanishes
            // because the wrong thing was tapped is exactly the frustration this app avoids.
            if (note != item.userNote) onSaveNote(item.id, note)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        HorizontalPager(state = pagerState) { page ->
            val item = items[page]
            // Keyed on the uri, not just the id: a stale "failed" flag from a previous uri
            // must not carry over if imageUri is ever filled in later (e.g. link enrichment).
            var imageLoadFailed by remember(item.id, item.imageUri) { mutableStateOf(false) }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
            Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.extractedText.isNotBlank()) {
                        SpringyIconButton(
                            onClick = { showOcr = !showOcr },
                            icon = Icons.AutoMirrored.Filled.Subject,
                            description = stringResource(R.string.detail_show_text),
                            highlighted = showOcr
                        )
                    }
                    // Hidden once the image failed to load: it would open a broken intent or a
                    // gallery error, which is worse than not offering it at all.
                    if (item.imageUri != null && !imageLoadFailed) {
                        SpringyIconButton(
                            onClick = { openInGallery(context, item.imageUri) },
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            description = stringResource(R.string.detail_open_gallery)
                        )
                    }
                    // A settled item offers the way back instead of the way out: discarding
                    // is one tap, so mis-tapping it must be one tap to undo.
                    if (item.isResolved) {
                        SpringyIconButton(
                            onClick = { onRestore(item.id) },
                            icon = Icons.Default.Restore,
                            description = stringResource(R.string.detail_restore),
                            highlighted = true
                        )
                    } else {
                        // Both buttons remove the item from Aktivní; only the checkmark is a
                        // reward. Celebrating the trash too would train deletion instead of
                        // follow-through (spec.md 11.8).
                        SettleButton(onSettled = { onDone(item.id) })
                        SpringyIconButton(
                            onClick = { onDiscard(item.id) },
                            icon = Icons.Default.Delete,
                            description = stringResource(R.string.detail_trash)
                        )
                    }
                }

                item.imageUri?.let { uri ->
                    if (imageLoadFailed) {
                        // The row survives regardless (spec.md 7.2/11.11): the note and OCR
                        // text often outlive the picture. This just admits it's gone instead
                        // of leaving a permanently "loading" gap where the image was.
                        Column {
                            MissingImagePlaceholder(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                iconSize = 48.dp
                            )
                            Text(
                                text = stringResource(R.string.detail_image_missing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                onError = { imageLoadFailed = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.detail_note_label)) },
                    placeholder = { Text(stringResource(R.string.detail_note_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                AnimatedVisibility(
                    visible = showOcr && item.extractedText.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.detail_recognised_text),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = item.extractedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        if (note != item.userNote) onSaveNote(item.id, note)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.detail_close))
                }
            }
        }
        }
        }
    }
}

// ---- SettleButton tunables --------------------------------------------
// RareOdds        – 1 in N settles draws from the rare tier. Raise to make it rarer.
// BumpNormal      – scale peak shared by BOUNCE and the rebound in POP.
// BumpRare        – scale peak shared by SPIN and the first bump in DOUBLE_POP.
// PopDip          – how far POP compresses before it rebounds.
// TiltDegrees     – how far TILT leans, in either direction, before returning.
// NudgeDistanceDp – how far NUDGE shifts sideways before returning.
// CommonDurationMs – per-leg cap for common-tier variants, total kept under ~350ms.
// RareDurationMs   – per-leg cap for rare-tier variants, total kept under ~600ms.
private const val RareOdds = 6
private const val BumpNormal = 1.18f
private const val BumpRare = 1.35f
private const val PopDip = 0.88f
private const val TiltDegrees = 12f
private const val NudgeDistanceDp = 14f
private const val CommonDurationMs = 170
private const val RareDurationMs = 280
// -------------------------------------------------------------------------

private enum class SettleTier { COMMON, RARE }

/**
 * One settle animation. Tier decides the odds (`RareOdds`); nothing else does.
 *
 * Within a tier every variant is drawn with equal probability by [random] and none is scarcer,
 * ranked, or sequenced ahead of another. That is deliberate: a set of variants a user could
 * notice themselves completing is a streak wearing a different hat, and spec.md 7.3 already
 * bans that. If this ever needs weights, that is a new product decision, not a tuning knob --
 * don't add one here.
 */
private enum class SettleAnimation(val tier: SettleTier) {
    BOUNCE(SettleTier.COMMON),
    POP(SettleTier.COMMON),
    TILT(SettleTier.COMMON),
    NUDGE(SettleTier.COMMON),
    FLIP(SettleTier.RARE),
    SPIN(SettleTier.RARE),
    DOUBLE_POP(SettleTier.RARE);

    companion object {
        fun random(rare: Boolean): SettleAnimation {
            val tier = if (rare) SettleTier.RARE else SettleTier.COMMON
            return entries.filter { it.tier == tier }.random()
        }
    }
}

/**
 * The fajfka. Only this button rewards -- spec.md 7.3 bans patterns that celebrate the mere
 * absence of a problem, and rewarding the trash too would be exactly that: it would train
 * deletion instead of actually dealing with things (spec.md 11.8).
 *
 * The haptic fires immediately, then whichever [SettleAnimation] was drawn plays out fully
 * before onSettled runs, so the item is never seen to vanish mid-animation.
 *
 * A single fixed bounce stops registering once it is familiar -- the known failure mode of any
 * fixed reward (spec.md 11.9). Picking a new animation per settle, from a wider bag on the rare
 * draw, is what carries the reward past that point: novelty, not intensity.
 */
@Composable
private fun SettleButton(onSettled: () -> Unit) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val rotationZAnim = remember { Animatable(0f) }
    val rotationYAnim = remember { Animatable(0f) }
    val translationXAnim = remember { Animatable(0f) }

    FilledTonalIconButton(
        onClick = {
            scope.launch {
                val rare = Random.nextInt(RareOdds) == 0
                settleVibration(context, rare)

                when (SettleAnimation.random(rare)) {
                    SettleAnimation.BOUNCE -> playBounce(scaleAnim)
                    SettleAnimation.POP -> playPop(scaleAnim)
                    SettleAnimation.TILT -> playTilt(rotationZAnim)
                    SettleAnimation.NUDGE -> playNudge(translationXAnim, with(localDensity) { NudgeDistanceDp.dp.toPx() })
                    SettleAnimation.FLIP -> playFlip(rotationYAnim)
                    SettleAnimation.SPIN -> playSpin(scaleAnim, rotationZAnim)
                    SettleAnimation.DOUBLE_POP -> playDoublePop(scaleAnim)
                }

                onSettled()
            }
        },
        // One graphicsLayer, not separate Modifier.scale + Modifier.rotate: stacking transform
        // modifiers applies them in a fixed order and they fight each other the moment more
        // than one is animating at once (SPIN drives scale and rotationZ together).
        modifier = Modifier.graphicsLayer {
            scaleX = scaleAnim.value
            scaleY = scaleAnim.value
            rotationZ = rotationZAnim.value
            rotationY = rotationYAnim.value
            translationX = translationXAnim.value
            // Unqualified `density` here is GraphicsLayerScope's own scale factor (it extends
            // Density), not the outer LocalDensity.current used for the nudge amplitude above.
            cameraDistance = 12f * density
        },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(R.string.detail_discard)
        )
    }
}

// Each variant is a suspend function that leaves every Animatable it touches back at rest
// before returning -- SettleButton awaits it and only then calls onSettled().

private suspend fun playBounce(scale: Animatable<Float, AnimationVector1D>) {
    scale.animateTo(
        BumpNormal,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
    )
    scale.animateTo(
        1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
    )
}

private suspend fun playPop(scale: Animatable<Float, AnimationVector1D>) {
    scale.animateTo(PopDip, tween(CommonDurationMs / 2))
    scale.animateTo(
        1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
    )
}

private suspend fun playTilt(rotationZ: Animatable<Float, AnimationVector1D>) {
    val direction = if (Random.nextBoolean()) 1f else -1f
    rotationZ.animateTo(direction * TiltDegrees, tween(CommonDurationMs))
    rotationZ.animateTo(
        0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
    )
}

private suspend fun playNudge(translationX: Animatable<Float, AnimationVector1D>, amplitudePx: Float) {
    val direction = if (Random.nextBoolean()) 1f else -1f
    translationX.animateTo(direction * amplitudePx, tween(CommonDurationMs))
    translationX.animateTo(
        0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
    )
}

private suspend fun playFlip(rotationY: Animatable<Float, AnimationVector1D>) {
    rotationY.animateTo(360f, tween(RareDurationMs))
    // 360° reads identically to 0°, so snapping back is invisible -- it just keeps the value
    // from climbing forever across repeated flips.
    rotationY.snapTo(0f)
}

private suspend fun playSpin(
    scale: Animatable<Float, AnimationVector1D>,
    rotationZ: Animatable<Float, AnimationVector1D>
) {
    coroutineScope {
        launch { rotationZ.animateTo(360f, tween(RareDurationMs)) }
        launch {
            scale.animateTo(BumpRare, tween(RareDurationMs / 2))
            scale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
            )
        }
    }
    rotationZ.snapTo(0f)
}

private suspend fun playDoublePop(scale: Animatable<Float, AnimationVector1D>) {
    // The rare case differs in kind, not degree: a second bounce reads as a different event,
    // where a bigger single scale peak on a 40dp button is a few pixels and reads as nothing.
    scale.animateTo(
        BumpRare,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
    )
    scale.animateTo(0.94f, spring(stiffness = Spring.StiffnessHigh))
    scale.animateTo(
        1.16f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
    )
    scale.animateTo(
        1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
    )
}

/**
 * Answers a tap with a small squash. Per-action feedback only -- nothing counts how often it
 * happens (spec.md 7.3).
 */
@Composable
private fun SpringyIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    highlighted: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 900f),
        label = "iconPressScale"
    )

    FilledTonalIconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.scale(scale),
        colors = if (highlighted) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        }
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

private fun openInGallery(context: android.content.Context, uri: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uri), "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }
}
/**
 * Vibrates directly rather than through `View.performHapticFeedback`.
 *
 * MagicOS silently ignores `HapticFeedbackConstants.CONFIRM` — the call succeeds and nothing
 * happens, which is the same failure mode as the overlay window in spec.md 11.1.
 *
 * The rare variant is a different pattern, not a stronger one: intensity is what a phone in a
 * pocket is worst at conveying, rhythm is what it is best at.
 */
private fun settleVibration(context: Context, rare: Boolean) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager ?: return
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    }

    if (!vibrator.hasVibrator()) return

    val effect = if (rare) {
        // Two taps and a longer close: reads as "something extra" without needing to be loud.
        VibrationEffect.createWaveform(
            longArrayOf(0, 26, 70, 26, 70, 55),
            intArrayOf(0, 170, 0, 190, 0, 255),
            -1
        )
    } else {
        VibrationEffect.createOneShot(26, 150)
    }

    runCatching { vibrator.vibrate(effect) }
}