package com.example.kcaltrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppTheme { Root() } }
    }
}

private fun autoMeal(): Int {
    val h = LocalTime.now().hour
    return when {
        h < 11 -> 0
        h < 16 -> 1
        h < 21 -> 2
        else -> 3
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current.applicationContext
    val st = remember { AppState(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    var addMeal by remember { mutableStateOf<Int?>(null) }
    var startAi by remember { mutableStateOf(false) }
    val meal = addMeal

    if (meal != null) {
        BackHandler { addMeal = null; startAi = false }
        AddFoodScreen(st, meal, startAi) { addMeal = null; startAi = false }
    } else {
        val scan = { addMeal = autoMeal(); startAi = true }
        Scaffold(
            containerColor = AppBg,
            bottomBar = { BottomBar(tab, { tab = it }, scan) }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> DiaryScreen(st) { addMeal = it; startAi = false }
                    1 -> StatsScreen(st)
                    2 -> ActivityScreen(st)
                    else -> ProfileScreen(st)
                }
            }
        }
    }
}

@Composable
fun BottomBar(tab: Int, onTab: (Int) -> Unit, onFab: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(92.dp)) {
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(68.dp).background(CardBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavSlot("Головна", Icons.Default.Home, tab == 0) { onTab(0) }
            NavSlot("Статистика", Icons.Default.DateRange, tab == 1) { onTab(1) }
            Spacer(Modifier.weight(1f))
            NavSlot("Активність", Icons.Default.Favorite, tab == 2) { onTab(2) }
            NavSlot("Профіль", Icons.Default.Person, tab == 3) { onTab(3) }
        }
        Box(
            Modifier.align(Alignment.TopCenter).size(72.dp).clip(CircleShape).background(AppBg),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(AppGreen).clickable(onClick = onFab),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_camera), contentDescription = "Сканувати страву", tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
fun RowScope.NavSlot(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) AppGreen else Muted
    Column(
        Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = c)
        Text(label, fontSize = 11.sp, color = c, maxLines = 1)
    }
}
