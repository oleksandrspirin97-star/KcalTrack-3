package com.example.kcaltrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeightScreen(st: AppState) {
    var t by remember { mutableStateOf("") }
    val pts = st.weights
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Вага", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 12.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumField("Вага сьогодні, кг", t, { t = it }, Modifier.weight(1f))
                Button(onClick = {
                    val v = t.num()
                    if (v != null && v in 20.0..400.0) { st.addWeight(v); t = "" }
                }) { Text("Записати") }
            }
        }
        if (pts.isNotEmpty()) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                        val first = pts.first().kg
                        val last = pts.last().kg
                        val diff = last - first
                        Text("Зараз: ${last.f1()} кг", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Початок: ${first.f1()} кг · Зміна: ${if (diff > 0) "+" else ""}${diff.f1()} кг")
                        WeightChart(pts.takeLast(60))
                    }
                }
            }
        }
        items(pts.reversed()) { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(p.date)
                Text("${p.kg.f1()} кг", fontWeight = FontWeight.Medium)
                IconButton(onClick = { st.deleteWeight(p.date) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Видалити")
                }
            }
        }
    }
}

@Composable
fun WeightChart(points: List<WeightPoint>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(top = 8.dp)) {
        if (points.size < 2) return@Canvas
        val min = points.minOf { it.kg }
        val max = points.maxOf { it.kg }
        val range = (max - min).coerceAtLeast(0.5)
        val pts = points.mapIndexed { i, p ->
            val x = size.width * i / (points.size - 1)
            val y = 8f + ((size.height - 16f) * (1 - (p.kg - min) / range)).toFloat()
            Offset(x, y)
        }
        for (i in 0 until pts.size - 1) drawLine(color, pts[i], pts[i + 1], strokeWidth = 4f)
        pts.forEach { drawCircle(color, 6f, it) }
    }
}
