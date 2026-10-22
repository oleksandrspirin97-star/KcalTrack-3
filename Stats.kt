package com.example.kcaltrack

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatsScreen(st: AppState) {
    var seg by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Тиждень", seg == 0) { seg = 0 }
            Chip("Вага", seg == 1) { seg = 1 }
        }
        Box(Modifier.weight(1f)) {
            if (seg == 0) WeekScreen(st) else WeightScreen(st)
        }
    }
}
