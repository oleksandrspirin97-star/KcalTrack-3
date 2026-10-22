package com.example.kcaltrack

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodMain(st: AppState, meal: Int, onClose: () -> Unit, onAi: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var online by remember { mutableStateOf<List<Food>>(emptyList()) }
    var usda by remember { mutableStateOf<List<Food>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Food?>(null) }
    var creating by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }

    val localAll = remember(q, refresh) { st.db.foods(q) }
    val local = if (tab == 1) localAll.filter { it.fav } else localAll
    val favKeys = remember(refresh) { st.db.foods("").filter { it.fav }.map { it.name + "|" + it.brand }.toSet() }

    fun doSearch() {
        val query = q.trim()
        if (query.isEmpty()) return
        scope.launch {
            loading = true; msg = ""; online = emptyList(); usda = emptyList()
            val errs = ArrayList<String>()
            try {
                online = OffApi.search(query)
            } catch (e: Exception) {
                errs.add("Open Food Facts: ${e.message}")
            }
            try {
                val cyr = query.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC }
                val en = if (cyr && st.currentKey().isNotBlank()) {
                    try { AiApi.toEnglish(st.aiProvider, st.currentKey(), st.geminiModel, query) } catch (e: Exception) { query }
                } else query
                usda = UsdaApi.search(en, st.usdaKey)
            } catch (e: Exception) {
                errs.add("USDA: ${e.message}")
            }
            if (online.isEmpty() && usda.isEmpty()) {
                msg = if (errs.isEmpty()) "Нічого не знайдено"
                else "Не вдалося виконати пошук (${errs.joinToString("; ")}). Перевірте інтернет і спробуйте ще раз."
            }
            loading = false
        }
    }

    fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { b ->
                val code = b.rawValue
                if (code != null) {
                    scope.launch {
                        loading = true; msg = ""
                        try {
                            val f = OffApi.byBarcode(code)
                            if (f != null) picked = f else msg = "Продукт $code не знайдено. Створіть його вручну."
                        } catch (e: Exception) {
                            msg = "Помилка мережі: ${e.message}"
                        }
                        loading = false
                    }
                }
            }
            .addOnFailureListener { msg = "Сканер недоступний: ${it.message}" }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(MEALS[meal]) },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") }
            }
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    label = { Text("Назва продукту") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTile(Modifier.weight(1f), "🔎", "Пошук", false) { doSearch() }
                    ActionTile(Modifier.weight(1f), "🏷️", "Штрихкод", false) { scan() }
                    ActionTile(Modifier.weight(1f), "✨", "ШІ", true) { onAi() }
                    ActionTile(Modifier.weight(1f), "➕", "Свій", false) { creating = true }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Мої продукти", tab == 0) { tab = 0 }
                    Chip("★ Вибране", tab == 1) { tab = 1 }
                }
            }
            if (loading) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
            if (msg.isNotEmpty()) item { Text(msg, color = MaterialTheme.colorScheme.error) }

            if (local.isEmpty() && tab == 1) {
                item { Text("Тут з'являться продукти, відмічені зірочкою ☆", color = Muted, fontSize = 13.sp) }
            }
            items(local) { f ->
                FoodRow(
                    f, fav = f.fav,
                    onFav = { st.db.toggleFav(f); refresh++ },
                    onClick = { picked = f },
                    onDelete = { st.db.deleteFood(f.id); refresh++ }
                )
            }
            if (online.isNotEmpty()) {
                item { SectionTitle("Open Food Facts (спершу українські товари)") }
                items(online) { f ->
                    FoodRow(
                        f, fav = (f.name + "|" + f.brand) in favKeys,
                        onFav = { st.db.toggleFav(f); refresh++ },
                        onClick = { picked = f }, onDelete = null
                    )
                }
            }
            if (usda.isNotEmpty()) {
                item { SectionTitle("USDA — загальні продукти (англійською)") }
                items(usda) { f ->
                    FoodRow(
                        f, fav = (f.name + "|" + f.brand) in favKeys,
                        onFav = { st.db.toggleFav(f); refresh++ },
                        onClick = { picked = f }, onDelete = null
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val p = picked
    if (p != null) {
        GramsDialog(
            title = p.name, initial = 100.0, kcal100 = p.kcal,
            onOk = { g -> st.addEntry(meal, p, g); picked = null; onClose() },
            onDismiss = { picked = null }
        )
    }
    if (creating) {
        CreateFoodDialog(
            onSave = { st.db.upsertFood(it); refresh++; creating = false },
            onDismiss = { creating = false }
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
fun ActionTile(modifier: Modifier, emoji: String, label: String, highlight: Boolean, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) AppGreen else Card2)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (highlight) OnGreen else TextMain
        )
    }
}

@Composable
fun FoodRow(f: Food, fav: Boolean, onFav: () -> Unit, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(f.name, maxLines = 2)
            val brand = if (f.brand.isNotEmpty()) "${f.brand} · " else ""
            Text(
                "$brand${f.kcal.f0()} ккал/100 г · Б ${f.protein.f1()} Ж ${f.fat.f1()} В ${f.carbs.f1()}",
                fontSize = 12.sp, color = Muted
            )
        }
        Text(
            if (fav) "★" else "☆", fontSize = 24.sp, color = if (fav) FatColor else Muted,
            modifier = Modifier.clickable { onFav() }.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Видалити", tint = Muted) }
        }
    }
}

@Composable
fun CreateFoodDialog(onSave: (Food) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новий продукт (на 100 г)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Назва") }, singleLine = true)
                NumField("Калорії", kcal, { kcal = it })
                NumField("Білки, г", p, { p = it })
                NumField("Жири, г", f, { f = it })
                NumField("Вуглеводи, г", c, { c = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val k = kcal.num()
                if (name.isNotBlank() && k != null) {
                    onSave(Food(name = name.trim(), kcal = k, protein = p.num() ?: 0.0, fat = f.num() ?: 0.0, carbs = c.num() ?: 0.0))
                }
            }) { Text("Зберегти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } }
    )
}

@Composable
fun AddFoodScreen(st: AppState, meal: Int, startAi: Boolean, onClose: () -> Unit) {
    var ai by remember { mutableStateOf(startAi) }
    if (ai) {
        BackHandler { ai = false }
        AiFoodScreen(st, meal, { ai = false }, onClose)
    } else {
        AddFoodMain(st, meal, onClose) { ai = true }
    }
}
