package com.app.snapmind.presentation.main.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import com.app.snapmind.presentation.theme.LocalSnapMindPalette
//import androidx.compose.animation.core.tween


/** Which items MainScreen shows. "Vyřízené" == CapturedItem.resolvedAt != null. */
enum class ItemFilter(val label: String) {
    ACTIVE("Aktivní"),
    DONE("Vyřízené"),
    ALL("Vše")
}

// ---- TUNABLES ---------------------------------------------------------
// Jediné místo, kde se ladí vzhled a pohyb jezdícího bloku ("connector").
// ConnectorHeight  – jeho výška.
// Barva connectoru jde z LocalSnapMindPalette.current.panel (čteno v composable
//                    níže) – MUSÍ být stejná jako pozadí panelu/karet pod tímto
//                    composable, jinak bude vidět šev.
// ConnectorAnim    – pružnost přejezdu; pro pevnou délku nahraď tween(300).
// TabGap           – mezera mezi záložkami; používá se i pro výpočet pozice,
//                    takže ji měň JEN tady, ne v modifieru.
private val ConnectorHeight = 8.dp

// O kolik connector přesahuje do panelu. Panel se kreslí až po něm,
// takže ho překryje – šev tím zmizí. Zvyš, pokud by se objevila mezera.
private val ConnectorOverlap = 4.dp
private val TabGap = 6.dp

//private val ConnectorAnim = tween<Dp>(durationMillis = 220)

//níže je původní varianta
private val ConnectorAnim = spring<Dp>(
   dampingRatio = Spring.DampingRatioLowBouncy,
   stiffness = Spring.StiffnessMediumLow
)
// -----------------------------------------------------------------------

@Composable
fun FilterTabs(
    selected: ItemFilter,
    onSelect: (ItemFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val filters = ItemFilter.entries
    val palette = LocalSnapMindPalette.current
    val connectorColor = palette.panel

    // Šířky se měří za běhu (text má různou délku); pozice se z nich sečte.
    var widths by remember { mutableStateOf(List(filters.size) { 0.dp }) }

    val selectedIndex = filters.indexOf(selected)
    val targetX = remember(widths, selectedIndex) {
        var x = 0.dp
        for (i in 0 until selectedIndex) x += widths[i] + TabGap
        x
    }
    val targetWidth = widths.getOrElse(selectedIndex) { 0.dp }

    val indicatorX by animateDpAsState(targetX, ConnectorAnim, label = "tabX")
    val indicatorWidth by animateDpAsState(targetWidth, ConnectorAnim, label = "tabWidth")

    Column(modifier) {
        Row {
            filters.forEachIndexed { index, filter ->
                val isSelected = filter == selected
                Text(
                    text = filter.label,
                    color = if (isSelected) {
                        palette.accent
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(end = if (index == filters.lastIndex) 0.dp else TabGap)
                        .clip(
                            if (isSelected) RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = 0.dp,
                                bottomEnd = 0.dp
                            )
                            else RoundedCornerShape(14.dp)
                        )
                        .background(if (isSelected) connectorColor else Color.Transparent)
                        .then(
                            if (isSelected) Modifier
                            else Modifier.border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp)
                            )
                        )
                        .clickable { onSelect(filter) }
                        .onGloballyPositioned { coords ->
                            val w = with(density) { coords.size.width.toDp() }
                            if (widths[index] != w) {
                                widths = widths.toMutableList().also { it[index] = w }
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        // Jezdící blok: sjíždí a mění šířku podle vybrané záložky.
        Box(
            modifier = Modifier
                .offset(x = indicatorX)
                .width(indicatorWidth)
                .height(ConnectorHeight + ConnectorOverlap)
                .background(connectorColor)
        )
    }
}