package com.app.snapmind.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.app.snapmind.R

/**
 * Barevné palety. Zdroj pravdy pro VŠECHNY barvy v appce — v UI kódu nesmí zůstat
 * žádný literál Color(0xFF...). Hodnoty jsou generované v OKLCH při konstantní
 * světlosti na roli, takže výměna palety nemění kontrast, jen odstín.
 *
 * Klíčové pravidlo pro `settled` (hotová položka): drží tón palety, jen ztrácí sytost.
 * V dark módu je TMAVŠÍ než panel, v light SVĚTLEJŠÍ pozadí ale tmavší než panel —
 * vždy ustupuje dozadu. Nikdy neutrální šedá, ta v tónované paletě vypadá jako chyba.
 */
@Immutable
data class SnapMindPalette(
    val accent: Color,
    val accentMuted: Color,
    val secondary: Color,
    val background: Color,
    val panel: Color,
    val surfaceRaised: Color,
    val surfaceRaisedHigh: Color,
    val settled: Color,
    val onSurface: Color,
    val onSurfaceFaded: Color,
    val onSettled: Color,
    val outline: Color,
)

enum class PaletteChoice(val labelRes: Int) {
    VIOLET(R.string.settings_palette_violet),
    TERRACOTTA(R.string.settings_palette_terracotta),
    DUSTYROSE(R.string.settings_palette_dustyrose),
    SAGE(R.string.settings_palette_sage),
    COMFORTBEIGE(R.string.settings_palette_comfortbeige),
    POWDERBLUE(R.string.settings_palette_powderblue);

    companion object { val DEFAULT = VIOLET }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val VioletDark = SnapMindPalette(
    accent = Color(0xFF9B8CFF),
    accentMuted = Color(0xFF6C5FB8),
    secondary = Color(0xFF7FD1C1),
    background = Color(0xFF14131A),
    panel = Color(0xFF1B1830),
    surfaceRaised = Color(0xFF1F1D28),
    surfaceRaisedHigh = Color(0xFF2A2735),
    settled = Color(0xFF16151C),
    onSurface = Color(0xFFE6E2F0),
    onSurfaceFaded = Color(0xFF9A96A8),
    onSettled = Color(0xFF83818D),
    outline = Color(0xFF4A4657),
)

private val VioletLight = SnapMindPalette(
    accent = Color(0xFF6854A8),
    accentMuted = Color(0xFFA99DDF),
    secondary = Color(0xFF6C581E),
    background = Color(0xFFF6F4FD),
    panel = Color(0xFFE9E7F8),
    surfaceRaised = Color(0xFFFDFDFF),
    surfaceRaisedHigh = Color(0xFFFFFFFF),
    settled = Color(0xFFDFDEEA),
    onSurface = Color(0xFF1E1B2B),
    onSurfaceFaded = Color(0xFF656371),
    onSettled = Color(0xFF6C6A75),
    outline = Color(0xFFBAB8C7),
)

private val TerracottaDark = SnapMindPalette(
    accent = Color(0xFFF0986F),
    accentMuted = Color(0xFF9A5332),
    secondary = Color(0xFF77CED6),
    background = Color(0xFF1A100B),
    panel = Color(0xFF29160E),
    surfaceRaised = Color(0xFF2F1E17),
    surfaceRaisedHigh = Color(0xFF3D281F),
    settled = Color(0xFF1C1411),
    onSurface = Color(0xFFF2E2DB),
    onSurfaceFaded = Color(0xFFAA9B94),
    onSettled = Color(0xFF8D807A),
    outline = Color(0xFF564842),
)

private val TerracottaLight = SnapMindPalette(
    accent = Color(0xFF9F4718),
    accentMuted = Color(0xFFDA9575),
    secondary = Color(0xFF00686F),
    background = Color(0xFFFDF3EF),
    panel = Color(0xFFF7E5DE),
    surfaceRaised = Color(0xFFFFFCFB),
    surfaceRaisedHigh = Color(0xFFFFFFFF),
    settled = Color(0xFFE9DDD7),
    onSurface = Color(0xFF2A1A12),
    onSurfaceFaded = Color(0xFF70615A),
    onSettled = Color(0xFF746964),
    outline = Color(0xFFC6B7B0),
)

private val DustyRoseDark = SnapMindPalette(
    accent = Color(0xFFEF939A),
    accentMuted = Color(0xFF985057),
    secondary = Color(0xFF88CFB9),
    background = Color(0xFF1A0F10),
    panel = Color(0xFF291517),
    surfaceRaised = Color(0xFF2F1D1E),
    surfaceRaisedHigh = Color(0xFF3C2729),
    settled = Color(0xFF1C1414),
    onSurface = Color(0xFFF2E2E2),
    onSurfaceFaded = Color(0xFFAA9A9B),
    onSettled = Color(0xFF8D7F80),
    outline = Color(0xFF564748),
)

private val DustyRoseLight = SnapMindPalette(
    accent = Color(0xFF9D434E),
    accentMuted = Color(0xFFD99196),
    secondary = Color(0xFF256857),
    background = Color(0xFFFDF3F3),
    panel = Color(0xFFF7E4E5),
    surfaceRaised = Color(0xFFFFFCFC),
    surfaceRaisedHigh = Color(0xFFFFFFFF),
    settled = Color(0xFFE9DCDC),
    onSurface = Color(0xFF2A191A),
    onSurfaceFaded = Color(0xFF706061),
    onSettled = Color(0xFF746869),
    outline = Color(0xFFC6B6B7),
)

private val SageDark = SnapMindPalette(
    accent = Color(0xFF88C28B),
    accentMuted = Color(0xFF46764A),
    secondary = Color(0xFFC7B3E3),
    background = Color(0xFF0E150E),
    panel = Color(0xFF131F14),
    surfaceRaised = Color(0xFF1B261C),
    surfaceRaisedHigh = Color(0xFF253225),
    settled = Color(0xFF131713),
    onSurface = Color(0xFFE0E9E0),
    onSurfaceFaded = Color(0xFF98A198),
    onSettled = Color(0xFF7E867E),
    outline = Color(0xFF464E46),
)

private val SageLight = SnapMindPalette(
    accent = Color(0xFF37743D),
    accentMuted = Color(0xFF88B58A),
    secondary = Color(0xFF635279),
    background = Color(0xFFF2F8F2),
    panel = Color(0xFFE2EDE2),
    surfaceRaised = Color(0xFFFCFEFC),
    surfaceRaisedHigh = Color(0xFFFFFFFF),
    settled = Color(0xFFDAE2DA),
    onSurface = Color(0xFF172117),
    onSurfaceFaded = Color(0xFF5E685F),
    onSettled = Color(0xFF676E67),
    outline = Color(0xFFB4BEB4),
)

private val ComfortBeigeDark = SnapMindPalette(
    accent = Color(0xFFC0AF8D),
    accentMuted = Color(0xFF75674C),
    secondary = Color(0xFF9DC3E1),
    background = Color(0xFF14120E),
    panel = Color(0xFF1F1B14),
    surfaceRaised = Color(0xFF26221C),
    surfaceRaisedHigh = Color(0xFF312D26),
    settled = Color(0xFF171613),
    onSurface = Color(0xFFE9E6E0),
    onSurfaceFaded = Color(0xFFA19E99),
    onSettled = Color(0xFF85837E),
    outline = Color(0xFF4E4B46),
)

private val ComfortBeigeLight = SnapMindPalette(
    accent = Color(0xFF736240),
    accentMuted = Color(0xFFB4A68C),
    secondary = Color(0xFF3E5F78),
    background = Color(0xFFF7F5F2),
    panel = Color(0xFFECE9E3),
    surfaceRaised = Color(0xFFFEFDFC),
    surfaceRaisedHigh = Color(0xFFFFFFFF),
    settled = Color(0xFFE2DFDB),
    onSurface = Color(0xFF211E17),
    onSurfaceFaded = Color(0xFF67645F),
    onSettled = Color(0xFF6E6B67),
    outline = Color(0xFFBDBAB5),
)

private val PowderBlueDark = SnapMindPalette(
    accent = Color(0xFF75B8F0),
    accentMuted = Color(0xFF366E9A),
    secondary = Color(0xFFE8AF97),
    background = Color(0xFF0C141A),
    panel = Color(0xFF0F1D29),
    surfaceRaised = Color(0xFF18242F),
    surfaceRaisedHigh = Color(0xFF21303C),
    settled = Color(0xFF11171B),
    onSurface = Color(0xFFDDE8F1),
    onSurfaceFaded = Color(0xFF96A0A9),
    onSettled = Color(0xFF7C848C),
    outline = Color(0xFF434D56),
)

private val PowderBlueLight = SnapMindPalette(
    accent = Color(0xFF1B6AA1),
    accentMuted = Color(0xFF7AAED9),
    secondary = Color(0xFF7C4D3A),
    background = Color(0xFFF0F7FD),
    panel = Color(0xFFDFEBF6),
    surfaceRaised = Color(0xFFFBFEFF),
    surfaceRaisedHigh = Color(0xFFFFFFFF),
    settled = Color(0xFFD8E1E9),
    onSurface = Color(0xFF131F2A),
    onSurfaceFaded = Color(0xFF5C666F),
    onSettled = Color(0xFF656D74),
    outline = Color(0xFFB2BCC5),
)

fun paletteFor(choice: PaletteChoice, dark: Boolean): SnapMindPalette = when (choice) {
    PaletteChoice.VIOLET -> if (dark) VioletDark else VioletLight
    PaletteChoice.TERRACOTTA -> if (dark) TerracottaDark else TerracottaLight
    PaletteChoice.DUSTYROSE -> if (dark) DustyRoseDark else DustyRoseLight
    PaletteChoice.SAGE -> if (dark) SageDark else SageLight
    PaletteChoice.COMFORTBEIGE -> if (dark) ComfortBeigeDark else ComfortBeigeLight
    PaletteChoice.POWDERBLUE -> if (dark) PowderBlueDark else PowderBlueLight
}

// errorContainer / onErrorContainer are not part of SnapMindPalette (no palette varies them),
// but Theme.kt still needs two literal-free shades: dark keeps the pre-split values, light
// reuses the same two shades with the roles swapped rather than inventing new hex.
internal val WarningContainer = Color(0xFF5A1F26)
internal val OnWarningContainer = Color(0xFFFFDAD9)
