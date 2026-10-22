package com.example.kcaltrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val UK = Locale("uk")

@Composable
fun WeekScreen(st: AppState) {
    var end by remember { mutableStateOf(LocalDate.now()) }
    val goal = st.profile.calorieGoal()
    // перераховуємо, коли змінюються дані щоденника
    val days = remember(end, st.entries, st.exercises, st.water) { st.week(end) }
    val logged = days.filter { it.kcal > 0 }
    val n = logged.size.coerceAtLeast(1)
    val fmt = DateTimeFormatter.ofPattern("d MMM", UK)
    val start = days.first().date
    val wInRange = st.weights.filter { it.date >= start.toString() && it.date <= end.toString() }

    LazyColumn(Modifier.fillMaxSize().swipeDays({ end = end.minusDays(7) }, { end = end.plusDays(7) }).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { end = end.minusDays(7) }) { Text("‹", fontSize = 28.sp) }
                Text("${start.format(fmt)} – ${end.format(fmt)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextButton(onClick = { end = end.plusDays(7) }) { Text("›", fontSize = 28.sp) }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Статистика за тиждень", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Днів із записами: ${logged.size} з 7")
                    if (logged.isNotEmpty()) {
                        val avg = logged.sumOf { it.kcal } / n
                        Text("Середнє: ${avg.f0()} ккал/день (ціль ${goal})", fontWeight = FontWeight.Medium)
                        Text("Б ${(logged.sumOf { it.protein } / n).f0()} г · Ж ${(logged.sumOf { it.fat } / n).f0()} г · В ${(logged.sumOf { it.carbs } / n).f0()} г на день")
                        val diff = avg - goal
                        Text(
                            if (diff > 0) "У середньому понад норму на ${diff.f0()} ккал/день"
                            else "У середньому нижче норми на ${(-diff).f0()} ккал/день"
                        )
                    }
                    Text("Спалено активністю: ${days.sumOf { it.burned }} ккал")
                    Text("Вода: у середньому ${days.sumOf { it.water } / 7} мл/день")
                    if (wInRange.size >= 2) {
                        val d = wInRange.last().kg - wInRange.first().kg
                        Text("Вага за тиждень: ${if (d > 0) "+" else ""}${d.f1()} кг")
                    }
                }
            }
        }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Калорії по днях", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    WeekChart(days, goal)
                }
            }
        }
        items(days.reversed()) { d ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("${d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, UK)}, ${d.date.format(fmt)}", fontWeight = FontWeight.Medium)
                    Text("Б ${d.protein.f0()} Ж ${d.fat.f0()} В ${d.carbs.f0()}", fontSize = 12.sp)
                }
                Text(if (d.kcal > 0) "${d.kcal.f0()} ккал" else "—", fontWeight = FontWeight.Bold)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun WeekChart(days: List<DayStat>, goal: Int) {
    val bar = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error
    val line = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val maxV = maxOf(goal.toDouble(), days.maxOf { it.kcal }, 1.0) * 1.1
        val w = size.width / days.size
        days.forEachIndexed { i, d ->
            val h = (size.height * d.kcal / maxV).toFloat()
            drawRect(if (d.kcal > goal) over else bar, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h))
        }
        val gy = size.height - (size.height * goal / maxV).toFloat()
        drawLine(line, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 3f)
    }
    Row(Modifier.fillMaxWidth()) {
        days.forEach {
            Text(
                it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, UK),
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp
            )
        }
    }
}
