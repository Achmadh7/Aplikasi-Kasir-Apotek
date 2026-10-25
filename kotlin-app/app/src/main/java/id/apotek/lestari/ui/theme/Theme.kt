package id.apotek.lestari.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Material 3 color system — seed pharmacy teal #00696D, same as web app theme.css
val TealPrimary = Color(0xFF00696D)
val TealPrimaryDark = Color(0xFF7ED3D8)

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF1F5),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF4A6363),
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF06201F),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFFF5FAF9),
    onSurface = Color(0xFF171D1C),
    onSurfaceVariant = Color(0xFF3F4948),
    outlineVariant = Color(0xFFBEC9C7)
)
private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF003739),
    primaryContainer = Color(0xFF004F52),
    secondaryContainer = Color(0xFF334B4B),
    error = Color(0xFFFFB4AB),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDEE4E2)
)

@Composable
fun ApotikLestariTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

fun formatIDR(n: Int): String = "Rp " + "%,d".format(java.util.Locale("id", "ID"), n).replace(',', '.')

// Stock status rule (same as web): >20 Aman, ==20 Sedang, <20 Rendah, 0 Habis
fun stockLabel(stock: Int): String = when {
    stock <= 0 -> "Habis"
    stock < 20 -> "Rendah"
    stock == 20 -> "Sedang"
    else -> "Aman"
}
