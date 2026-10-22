package com.example.kcaltrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PRESETS = listOf("Ходьба" to 4.5, "Біг" to 10.0, "Велосипед" to 8.0, "Силове" to 6.0, "Плавання" to 9.0)

@Composable
fun ActivityScreen(st: AppState) {
    var adding by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().swipeDays({ st.shift(-1) }, { st.shift(1) }).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { DateBar(st) }
        item {
            Text("Спалено: ${st.burned} ккал", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        item { Button(onClick = { adding = true }) { Text("+ Додати вправу") } }
        items(st.exercises) { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.name)
                    Text("${e.minutes} хв", fontSize = 12.sp)
                }
                Text("${e.kcal} ккал", fontWeight = FontWeight.Medium)
                IconButton(onClick = { st.deleteExercise(e.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Видалити")
                }
            }
        }
    }
    if (adding) AddExerciseDialog({ n, m, k -> st.addExercise(n, m, k); adding = false }, { adding = false })
}

@Composable
fun AddExerciseDialog(onSave: (String, Int, Int) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("30") }
    var kcal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(0.0) }

    fun recalc() {
        val m = minutes.num() ?: 0.0
        if (rate > 0) kcal = (m * rate).f0()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вправа") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PRESETS.take(3).forEach { (n, r) ->
                        TextButton(onClick = { name = n; rate = r; recalc() }) { Text(n, fontSize = 12.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PRESETS.drop(3).forEach { (n, r) ->
                        TextButton(onClick = { name = n; rate = r; recalc() }) { Text(n, fontSize = 12.sp) }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Назва") }, singleLine = true)
                NumField("Хвилини", minutes, { minutes = it; recalc() }, decimal = false)
                NumField("Ккал", kcal, { kcal = it; rate = 0.0 }, decimal = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = minutes.num()?.toInt()
                val k = kcal.num()?.toInt()
                if (name.isNotBlank() && m != null && k != null) onSave(name.trim(), m, k)
            }) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}
