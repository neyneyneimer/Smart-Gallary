package ai.sakana.aura.gallery.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ai.sakana.aura.gallery.R

object AuraTokens {
    val Void = Color(0xFF0A0A0B)
    val Surface1 = Color(0xFF101011)
    val Surface2 = Color(0xFF171719)
    val Surface3 = Color(0xFF1E1E21)
    val Paper = Color(0xFFF4F2EE)
    val PaperMuted = Color(0xFFA8A5A0)
    val PaperFaint = Color(0xFF7C7A74)
    val Safelight = Color(0xFFE0A24A)
    val InkOnAmber = Color(0xFF1A1206)
    val AuraA = Color(0xFF7FA8FF)
    val AuraB = Color(0xFFC9A2FF)
    val Hairline = Paper.copy(alpha = 0.09f)
    val HairlineStrong = Paper.copy(alpha = 0.18f)
}

val InstrumentSerif = FontFamily(Font(R.font.instrument_serif))
val Manrope = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal),
    Font(R.font.manrope, weight = FontWeight.Medium),
    Font(R.font.manrope, weight = FontWeight.SemiBold),
    Font(R.font.manrope, weight = FontWeight.Bold),
    Font(R.font.manrope, weight = FontWeight.ExtraBold),
)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, weight = FontWeight.Normal),
    Font(R.font.jetbrains_mono, weight = FontWeight.Medium),
)

private val AuraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.6).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 34.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.9.sp,
        fontFeatureSettings = "tnum",
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        fontFeatureSettings = "tnum",
    ),
)

private val AuraColorScheme = darkColorScheme(
    primary = AuraTokens.Safelight,
    onPrimary = AuraTokens.InkOnAmber,
    background = AuraTokens.Void,
    onBackground = AuraTokens.Paper,
    surface = AuraTokens.Surface1,
    onSurface = AuraTokens.Paper,
    surfaceVariant = AuraTokens.Surface2,
    onSurfaceVariant = AuraTokens.PaperMuted,
    outline = AuraTokens.PaperFaint,
    error = AuraTokens.Safelight,
)

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AuraColorScheme,
        typography = AuraTypography,
        content = content,
    )
}
