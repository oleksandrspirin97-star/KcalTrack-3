package com.example.kcaltrack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileScreen(st: AppState) {
    val p = st.profile
    var male by remember { mutableStateOf(p.male) }
    var age by remember { mutableStateOf(p.age.toString()) }
    var height by remember { mutableStateOf(p.heightCm.toString()) }
    var weight by remember { mutableStateOf(p.weightKg.f1()) }
    var activity by remember { mutableStateOf(p.activity) }
    var goal by remember { mutableStateOf(p.goal) }
    var manual by remember { mutableStateOf(p.manual) }
    var manualKcal by remember { mutableStateOf(p.manualKcal.toString()) }
    var water by remember { mutableStateOf(p.waterGoalMl.toString()) }
    var key by remember { mutableStateOf(st.apiKey) }
    var gkey by remember { mutableStateOf(st.geminiKey) }
    var gmodel by remember { mutableStateOf(st.geminiModel) }
    var ukey by remember { mutableStateOf(st.usdaKey) }
    var name by remember { mutableStateOf(p.name) }
    var provider by remember { mutableStateOf(st.aiProvider) }

    fun build() = Profile(
        name = name.trim(),
        male = male,
        age = age.num()?.toInt() ?: p.age,
        heightCm = height.num()?.toInt() ?: p.heightCm,
        weightKg = weight.num() ?: p.weightKg,
        activity = activity, goal = goal, manual = manual,
        manualKcal = manualKcal.num()?.toInt() ?: p.manualKcal,
        waterGoalMl = water.num()?.toInt() ?: p.waterGoalMl
    )

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Профіль і цілі", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Ваше ім'я") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (male) Button(onClick = { male = true }) { Text("Чоловік") } else OutlinedButton(onClick = { male = true }) { Text("Чоловік") }
            if (!male) Button(onClick = { male = false }) { Text("Жінка") } else OutlinedButton(onClick = { male = false }) { Text("Жінка") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Вік", age, { age = it }, Modifier.weight(1f), decimal = false)
            NumField("Зріст, см", height, { height = it }, Modifier.weight(1f), decimal = false)
            NumField("Вага, кг", weight, { weight = it }, Modifier.weight(1f))
        }
        Text("Активність", fontWeight = FontWeight.Medium)
        ACTIVITY_NAMES.forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().clickable { activity = i }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = activity == i, onClick = { activity = i }); Text(n)
            }
        }
        Text("Ціль", fontWeight = FontWeight.Medium)
        GOAL_NAMES.forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().clickable { goal = i }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = goal == i, onClick = { goal = i }); Text(n)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = manual, onCheckedChange = { manual = it })
            Text("Задати норму калорій вручну")
        }
        if (manual) NumField("Ккал на день", manualKcal, { manualKcal = it }, Modifier.fillMaxWidth(), decimal = false)
        NumField("Норма води, мл", water, { water = it }, Modifier.fillMaxWidth(), decimal = false)
        Text("ШІ-помічник", fontWeight = FontWeight.Medium)
        listOf("Claude (Anthropic)", "Gemini (Google)").forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().clickable { provider = i }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = provider == i, onClick = { provider = i }); Text(n)
            }
        }
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text("API-ключ Claude (console.anthropic.com)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = gkey, onValueChange = { gkey = it },
            label = { Text("API-ключ Gemini (aistudio.google.com)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = gmodel, onValueChange = { gmodel = it },
            label = { Text("Модель Gemini") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = ukey, onValueChange = { ukey = it },
            label = { Text("Ключ USDA (необов'язково, api.data.gov)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Ключі зберігаються лише на вашому телефоні. Потрібен ключ лише для вибраного ШІ.", fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text("Розрахована норма: ${build().calorieGoal()} ккал/день", fontWeight = FontWeight.Bold)
        FilledTonalButton(onClick = { st.saveProfile(build()); st.saveAi(provider, key.trim(), gkey.trim(), gmodel.trim().ifBlank { AiApi.GEMINI_MODEL }); st.saveUsda(ukey.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("Зберегти") }
        Spacer(Modifier.height(16.dp))
    }
}
