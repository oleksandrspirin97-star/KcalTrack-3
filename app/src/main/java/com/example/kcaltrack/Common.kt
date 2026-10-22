package com.example.kcaltrack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.util.Locale
import kotlin.math.roundToInt

val AppBg = Color(0xFF0B0F0C)
val CardBg = Color(0xFF171B18)
val Card2 = Color(0xFF232824)
val AppGreen = Color(0xFF2ED573)
val OnGreen = Color(0xFF05210F)
val Muted = Color(0xFF9AA5A0)
val AppRed = Color(0xFFFF6B6B)
val ProtColor = Color(0xFF3FA9F5)
val FatColor = Color(0xFFF5C542)
val CarbColor = Color(0xFFA855F7)
val WaterColor = Color(0xFF1F5A8C)
val TextMain = Color(0xFFF2F5F3)

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun interTypography(): Typography {
    val t = Typography()
    val f = InterFamily
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = f, letterSpacing = 0.sp),
        displayMedium = t.displayMedium.copy(fontFamily = f, letterSpacing = 0.sp),
        displaySmall = t.displaySmall.copy(fontFamily = f, letterSpacing = 0.sp),
        headlineLarge = t.headlineLarge.copy(fontFamily = f, letterSpacing = 0.sp),
        headlineMedium = t.headlineMedium.copy(fontFamily = f, letterSpacing = 0.sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = f, letterSpacing = 0.sp),
        titleLarge = t.titleLarge.copy(fontFamily = f, letterSpacing = 0.sp),
        titleMedium = t.titleMedium.copy(fontFamily = f, letterSpacing = 0.sp),
        titleSmall = t.titleSmall.copy(fontFamily = f, letterSpacing = 0.sp),
        bodyLarge = t.bodyLarge.copy(fontFamily = f, letterSpacing = 0.sp),
        bodyMedium = t.bodyMedium.copy(fontFamily = f, letterSpacing = 0.sp),
        bodySmall = t.bodySmall.copy(fontFamily = f, letterSpacing = 0.sp),
        labelLarge = t.labelLarge.copy(fontFamily = f, letterSpacing = 0.sp),
        labelMedium = t.labelMedium.copy(fontFamily = f, letterSpacing = 0.sp),
        labelSmall = t.labelSmall.copy(fontFamily = f, letterSpacing = 0.sp)
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AppGreen,
            onPrimary = OnGreen,
            secondary = Color(0xFFF5A742),
            tertiary = ProtColor,
            background = AppBg,
            onBackground = TextMain,
            surface = AppBg,
            onSurface = TextMain,
            surfaceVariant = CardBg,
            onSurfaceVariant = Muted,
            error = AppRed,
            outline = Color(0xFF3A413C)
        ),
        typography = interTypography(),
        content = content
    )
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg, contentColor = TextMain),
        content = content
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AppGreen else Card2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) OnGreen else TextMain, fontWeight = FontWeight.Medium)
    }
}

fun Double.f0(): String = roundToInt().toString()
fun Double.f1(): String = String.format(Locale.US, "%.1f", this)
/** 1240 -> «1 240» (нерозривний пробіл) */
fun Double.k(): String = String.format(Locale.US, "%,d", roundToInt()).replace(',', '\u00A0')
fun String.num(): Double? = trim().replace(',', '.').toDoubleOrNull()

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
fun GramsDialog(title: String, initial: Double, kcal100: Double, onOk: (Double) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial.f0()) }
    val g = t.num() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            Column {
                NumField("Вага, г", t, { t = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("${(kcal100 * g / 100).f0()} ккал")
            }
        },
        confirmButton = { TextButton(onClick = { if (g > 0) onOk(g) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

/** Свайп вправо — назад, вліво — вперед. */
fun Modifier.swipeDays(onPrev: () -> Unit, onNext: () -> Unit): Modifier = pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total > 120f) onPrev() else if (total < -120f) onNext()
            total = 0f
        },
        onDragCancel = { total = 0f },
        onHorizontalDrag = { _, dx -> total += dx }
    )
}
