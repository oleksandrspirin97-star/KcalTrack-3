package com.example.kcaltrack

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val MEAL_EMOJI = listOf("🥣", "🍲", "🍗", "🍎")

@Composable
fun DateBar(st: AppState) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = { st.shift(-1) }) { Text("‹", fontSize = 28.sp, color = Muted) }
        val label = if (st.date == LocalDate.now()) "Сьогодні"
        else st.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("uk")))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        TextButton(onClick = { st.shift(1) }) { Text("›", fontSize = 28.sp, color = Muted) }
    }
}

@Composable
fun DiaryScreen(st: AppState, onAdd: (Int) -> Unit) {
    val goal = st.profile.calorieGoal()
    val eaten = st.eaten
    val burned = st.burned
    val budget = goal + burned
    val left = budget - eaten
    val kc = goal.toDouble()

    LazyColumn(
        Modifier.fillMaxSize().swipeDays({ st.shift(-1) }, { st.shift(1) }).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)); Greeting(st) }
        item { Text("Розумний сканер їжі", fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp) }
        item { DateBar(st) }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CalorieRing(
                    progress = (eaten / budget.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f),
                    over = left < 0
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(eaten.k(), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text(
                            " / ${budget.toDouble().k()} ккал", fontSize = 14.sp, color = Muted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        if (left >= 0) "Залишилось: ${left.k()} ккал" else "Перебір: ${(-left).k()} ккал",
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = if (left >= 0) AppGreen else AppRed
                    )
                    if (burned > 0) Text("з урахуванням активності +$burned", fontSize = 11.sp, color = Muted)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroItem(Modifier.weight(1f), "🥩", "Б", st.entries.sumOf { it.protein }, kc * 0.2 / 4, ProtColor)
                MacroItem(Modifier.weight(1f), "🧈", "Ж", st.entries.sumOf { it.fat }, kc * 0.3 / 9, FatColor)
                MacroItem(Modifier.weight(1f), "🍞", "В", st.entries.sumOf { it.carbs }, kc * 0.5 / 4, CarbColor)
            }
        }
        item { Text("Щоденник харчування", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        items(MEALS.indices.toList()) { i -> MealCard(st, i) { onAdd(i) } }
        item { WaterCard(st) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun Greeting(st: AppState) {
    val name = st.profile.name
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Card2), contentAlignment = Alignment.Center) {
            Text(name.firstOrNull()?.uppercase() ?: "🙂", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (name.isBlank()) "Привіт! 👋" else "Привіт, $name! 👋",
            fontSize = 17.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CalorieRing(progress: Float, over: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val anim by animateFloatAsState(progress, label = "ring")
    val track = Card2
    val col = if (over) AppRed else AppGreen
    Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = stroke)
            )
            if (anim > 0f) {
                drawArc(
                    color = col, startAngle = -90f, sweepAngle = 360f * anim, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
fun MacroItem(modifier: Modifier, emoji: String, label: String, value: Double, goal: Double, color: Color) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 16.sp)
            Text(" $label: ${value.f0()}/${goal.f0()}г", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (value / goal.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) },
            color = color, trackColor = Card2,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
        )
    }
}

@Composable
fun MealCard(st: AppState, i: Int, onAdd: () -> Unit) {
    val list = st.entries.filter { it.meal == i }
    val has = list.isNotEmpty()
    var open by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { if (has) open = !open else onAdd() }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Card2), contentAlignment = Alignment.Center) {
                Text(MEAL_EMOJI[i], fontSize = 26.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(MEALS[i], fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (has) list.joinToString(", ") { it.name } else "Ще не додано",
                    fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (has) {
                Text("${list.sumOf { it.kcal }.f0()} ккал", fontSize = 14.sp, color = Muted)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(AppGreen), contentAlignment = Alignment.Center) {
                    Text("✓", color = OnGreen, fontWeight = FontWeight.Bold)
                }
            } else {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Card2).clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center
                ) { Text("+", fontSize = 22.sp) }
            }
        }
        if (open && has) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                list.forEach { EntryRow(st, it) }
                TextButton(onClick = onAdd) { Text("+ Додати продукт", color = AppGreen) }
            }
        }
    }
}

private fun liters(ml: Int): String = String.format(Locale("uk"), "%.1f", ml / 1000.0)

@Composable
fun WaterCard(st: AppState) {
    val goal = st.profile.waterGoalMl.coerceAtLeast(1)
    val frac = (st.water.toFloat() / goal).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(20.dp)).background(CardBg)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(WaterColor))
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💧", fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Вода: ${liters(st.water)} л / ${liters(goal)} л",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1
            )
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF)).clickable { st.addWater(-250) },
                contentAlignment = Alignment.Center
            ) { Text("−", fontSize = 22.sp) }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x33FFFFFF)).clickable { st.addWater(250) },
                contentAlignment = Alignment.Center
            ) { Text("+", fontSize = 22.sp) }
        }
    }
}

@Composable
fun EntryRow(st: AppState, e: Entry) {
    var edit by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { edit = true }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${e.grams.f0()} г · Б ${e.protein.f0()} Ж ${e.fat.f0()} В ${e.carbs.f0()}", fontSize = 12.sp, color = Muted)
        }
        Text("${e.kcal.f0()} ккал", fontWeight = FontWeight.Medium)
        IconButton(onClick = { st.deleteEntry(e.id) }) {
            Icon(Icons.Default.Delete, contentDescription = "Видалити", tint = Muted)
        }
    }
    if (edit) {
        GramsDialog(e.name, e.grams, e.kcal100, { st.setGrams(e.id, it); edit = false }, { edit = false })
    }
}
