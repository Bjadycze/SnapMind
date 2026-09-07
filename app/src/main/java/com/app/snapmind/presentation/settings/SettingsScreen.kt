package com.app.snapmind.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.snapmind.R
import com.app.snapmind.presentation.theme.LocalSnapMindPalette
import com.app.snapmind.presentation.theme.PaletteChoice
import com.app.snapmind.presentation.theme.ThemeMode
import com.app.snapmind.presentation.theme.paletteFor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back)
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        ModeSetting(
            mode = state.themeMode,
            onChange = viewModel::setThemeMode
        )

        PaletteSetting(
            selected = state.palette,
            mode = state.themeMode,
            onChange = viewModel::setPalette
        )

        HourSetting(
            title = stringResource(R.string.settings_reminder_title),
            explanation = stringResource(R.string.settings_reminder_body),
            hour = state.reminderHour,
            onChange = viewModel::setReminderHour
        )

        HourSetting(
            title = stringResource(R.string.settings_quiet_start_title),
            explanation = stringResource(R.string.settings_quiet_body),
            hour = state.quietStartHour,
            onChange = { viewModel.setQuietHours(it, state.quietEndHour) }
        )

        HourSetting(
            title = stringResource(R.string.settings_quiet_end_title),
            explanation = null,
            hour = state.quietEndHour,
            onChange = { viewModel.setQuietHours(state.quietStartHour, it) }
        )

        ProSetting(
            isPro = isPro,
            onSubscribe = { context.findActivity()?.let(viewModel::purchase) }
        )

        // Only shown once the user has made one: an empty section would be a setting for
        // something that does not exist yet.
        if (categories.isNotEmpty()) {
            CustomCategoriesSetting(
                categories = categories,
                onRemove = viewModel::retireCategory
            )
        }
    }
}

/**
 * SnapMind Pro (spec.md 11.2, Task 8). Capture, reminders and search are free permanently, so
 * this section states what Pro is for and never nags: no banner, no badge, no reminder that
 * you have not bought it.
 */
@Composable
private fun ProSetting(isPro: Boolean, onSubscribe: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.settings_pro_title),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(
                if (isPro) R.string.settings_pro_active else R.string.settings_pro_body
            ),
            style = MaterialTheme.typography.bodySmall,
            color = LocalSnapMindPalette.current.onSurfaceFaded
        )
        if (!isPro) {
            OutlinedButton(onClick = onSubscribe) {
                Text(stringResource(R.string.settings_pro_subscribe))
            }
        }
    }
}

/** Compose gives a ContextWrapper here, not the Activity Play needs. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The user's own categories (spec.md 11.16). Removing one only stops it being offered -- the
 * name is kept and comes back as a suggestion the next time a category is added, and items
 * already carrying it keep showing it (spec.md 7.2).
 */
@Composable
private fun CustomCategoriesSetting(
    categories: List<CustomCategory>,
    onRemove: (String) -> Unit
) {
    val palette = LocalSnapMindPalette.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_categories_title),
            style = MaterialTheme.typography.titleSmall
        )

        categories.forEach { category ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (category.inUse) {
                        Text(
                            text = stringResource(R.string.settings_category_in_use),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceFaded
                        )
                    }
                }

                IconButton(
                    onClick = { onRemove(category.name) },
                    enabled = !category.inUse
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.settings_category_remove)
                    )
                }
            }
        }
    }
}

@Composable
private fun HourSetting(
    title: String,
    explanation: String?,
    hour: Int,
    onChange: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            explanation?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { onChange((hour + 23) % 24) }) { Text("−") }
                Text(
                    text = "%02d:00".format(hour),
                    style = MaterialTheme.typography.titleLarge
                )
                OutlinedButton(onClick = { onChange((hour + 1) % 24) }) { Text("+") }
            }
        }
    }
}

@Composable
private fun ModeSetting(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_mode_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_mode_light),
        ThemeMode.DARK to stringResource(R.string.settings_mode_dark)
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_mode_title),
                style = MaterialTheme.typography.titleMedium
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = { onChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteSetting(selected: PaletteChoice, mode: ThemeMode, onChange: (PaletteChoice) -> Unit) {
    // Preview swatches follow the mode actually in effect, not a fixed dark/light guess --
    // otherwise a Light-mode user would pick a colour by its (invisible) dark-mode accent.
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_palette_title),
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PaletteChoice.entries.forEach { choice ->
                    val isSelected = choice == selected
                    val swatchColor = paletteFor(choice, dark).accent
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onChange(choice) }
                    )
                }
            }
            Text(
                text = selected.labelCs,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
