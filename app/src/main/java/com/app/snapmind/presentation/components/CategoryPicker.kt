package com.app.snapmind.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.snapmind.R
import com.app.snapmind.data.prefs.MaxCategoryNameLength
import com.app.snapmind.domain.classify.DetectedCategory
import com.app.snapmind.presentation.theme.LocalSnapMindPalette

/**
 * Manual category, in the item detail (spec.md 11.16).
 *
 * The classifier guesses; this is where the guess gets corrected, and where an item it could
 * not place gets filed by hand. Built-in categories first, the user's own after them, then
 * "neurčeno" and the way to add a new one.
 *
 * No counts anywhere, per §7.3 -- not "3 items in Zahrada", not a marker on categories in use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(
    /** The value stored on the item: a DetectedCategory name, a custom name, or null. */
    selected: String?,
    custom: List<String>,
    retired: List<String>,
    onPick: (String?) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSnapMindPalette.current
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    val selectedKey = selected?.trim().orEmpty()
    val builtIn = remember { DetectedCategory.entries.filter { it != DetectedCategory.UNKNOWN } }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.detail_category_label),
            style = MaterialTheme.typography.labelMedium,
            color = palette.onSurfaceFaded
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            builtIn.forEach { category ->
                CategoryChip(
                    label = builtInLabel(category),
                    selected = selectedKey == category.name,
                    // Tapping the selected one clears it -- the same rule as the filter on the
                    // card, so there is only one thing to learn.
                    onClick = { onPick(if (selectedKey == category.name) null else category.name) }
                )
            }

            custom.forEach { name ->
                CategoryChip(
                    label = name,
                    selected = selectedKey.equals(name, ignoreCase = true),
                    onClick = {
                        onPick(if (selectedKey.equals(name, ignoreCase = true)) null else name)
                    }
                )
            }

            // Explicitly "unclassified", not the absence of a choice: it is a place to file
            // something, and it is where the empty strip on the card comes from.
            CategoryChip(
                label = stringResource(R.string.category_none),
                selected = selectedKey.isEmpty() || selectedKey == DetectedCategory.UNKNOWN.name,
                onClick = { onPick(DetectedCategory.UNKNOWN.name) }
            )

            CategoryChip(
                label = stringResource(R.string.category_add),
                selected = false,
                dashed = true,
                onClick = { adding = !adding }
            )
        }

        AnimatedVisibility(visible = adding) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(MaxCategoryNameLength) },
                        placeholder = { Text(stringResource(R.string.category_add_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (draft.isNotBlank()) onCreate(draft)
                            draft = ""
                            adding = false
                        }
                    ) {
                        Text(stringResource(R.string.category_add_confirm))
                    }
                }

                // Retired names are offered back rather than forgotten: no retyping, and no
                // near-duplicate spelling of a category that already existed.
                if (retired.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.category_previously_used),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onSurfaceFaded,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        retired.forEach { name ->
                            CategoryChip(
                                label = name,
                                selected = false,
                                dashed = true,
                                onClick = {
                                    onCreate(name)
                                    draft = ""
                                    adding = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    dashed: Boolean = false
) {
    val palette = LocalSnapMindPalette.current

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            selected -> palette.background
            dashed -> palette.accent
            else -> palette.onSurfaceFaded
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.accent else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) palette.accent else palette.outline,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun builtInLabel(category: DetectedCategory): String = stringResource(
    when (category) {
        DetectedCategory.EVENT -> R.string.category_event
        DetectedCategory.RECIPE -> R.string.category_recipe
        DetectedCategory.PURCHASE -> R.string.category_purchase
        DetectedCategory.ARTICLE -> R.string.category_article
        DetectedCategory.CONTACT -> R.string.category_contact
        DetectedCategory.UNKNOWN -> R.string.category_none
    }
)
