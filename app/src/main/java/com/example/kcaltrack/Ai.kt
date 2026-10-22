package com.example.kcaltrack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Розпізнавання їжі за описом або фото через Claude (Anthropic) або Gemini (Google). Потрібен власний API-ключ. */
object AiApi {
    const val CLAUDE_MODEL = "claude-sonnet-5-5"
    const val GEMINI_MODEL = "gemini-3.8-flash"
    private const val GEMINI_FALLBACK = "gemini-flash-latest"

    data class Item(val food: Food, val grams: Double)

    private val PROMPT = """
Ти — досвідчений дієтолог. Користувач описує, що він з'їв, та/або надсилає фото страви.
Визнач окремі продукти чи страви й оціни вагу кожної порції в грамах.
Поверни ТІЛЬКИ JSON-масив, без пояснень і без markdown, у такому форматі:
[{"name":"назва українською","grams":число,"kcal":число,"protein":число,"fat":число,"carbs":число}]
Поля kcal, protein, fat, carbs — значення НА 100 г продукту. grams — оцінена вага порції в грамах.
Якщо їжі не видно й не описано — поверни порожній масив [].
""".trimIndent()

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("content-type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        val resp: String
        val code: Int
        try {
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            c.disconnect()
        }
        if (code !in 200..299) {
            val msg = try { JSONObject(resp).optJSONObject("error")?.optString("message") } catch (e: Exception) { null }
            throw Exception("HTTP $code: ${msg ?: resp.take(200)}")
        }
        return resp
    }

    private fun claude(apiKey: String, userText: String, imageB64: String?, instr: String = PROMPT): String {
        val content = JSONArray()
        if (imageB64 != null) {
            content.put(
                JSONObject().put("type", "image").put(
                    "source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", imageB64)
                )
            )
        }
        content.put(JSONObject().put("type", "text").put("text", instr + "\n\nОпис: " + userText))
        val body = JSONObject()
            .put("model", CLAUDE_MODEL)
            .put("max_tokens", 1000)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val resp = post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            body
        )
        val blocks = JSONObject(resp).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until blocks.length()) {
            val b = blocks.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.optString("text"))
        }
        return sb.toString()
    }

    /** Якщо модель недоступна (404) — пробуємо псевдонім найновішої Flash-моделі. */
    private fun gemini(apiKey: String, model: String, userText: String, imageB64: String?, instr: String = PROMPT): String =
        try {
            geminiCall(apiKey, model, userText, imageB64, instr)
        } catch (e: Exception) {
            if (e.message?.startsWith("HTTP 404") == true && model != GEMINI_FALLBACK)
                geminiCall(apiKey, GEMINI_FALLBACK, userText, imageB64, instr)
            else throw e
        }

    private fun geminiCall(apiKey: String, model: String, userText: String, imageB64: String?, instr: String): String {
        val parts = JSONArray()
        parts.put(JSONObject().put("text", instr + "\n\nОпис: " + userText))
        if (imageB64 != null) {
            parts.put(
                JSONObject().put(
                    "inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", imageB64)
                )
            )
        }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val resp = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to apiKey),
            body
        )
        val cands = JSONObject(resp).optJSONArray("candidates")
        if (cands == null || cands.length() == 0) throw Exception("Gemini не повернув відповіді (можливо, запит заблоковано)")
        val ps = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw Exception("Gemini повернув порожню відповідь")
        val sb = StringBuilder()
        for (i in 0 until ps.length()) sb.append(ps.getJSONObject(i).optString("text"))
        return sb.toString()
    }

    /** provider: 0 = Claude, 1 = Gemini */
    suspend fun analyze(provider: Int, apiKey: String, model: String, text: String, imageB64: String?): List<Item> =
        withContext(Dispatchers.IO) {
            val t = text.ifBlank { "(див. фото)" }
            val raw = if (provider == 1) gemini(apiKey, model, t, imageB64) else claude(apiKey, t, imageB64)
            parse(raw)
        }

    /** Переклад назви продукту англійською (для пошуку в USDA). */
    suspend fun toEnglish(provider: Int, apiKey: String, model: String, text: String): String =
        withContext(Dispatchers.IO) {
            val instr = "Переклади назву продукту харчування англійською мовою для пошуку в базі USDA. " +
                "Поверни ТІЛЬКИ JSON {\"en\":\"переклад\"} без пояснень."
            val raw = if (provider == 1) gemini(apiKey, model, text, null, instr) else claude(apiKey, text, null, instr)
            val s = raw.indexOf('{')
            val e = raw.lastIndexOf('}')
            if (s < 0 || e <= s) text
            else JSONObject(raw.substring(s, e + 1)).optString("en", text).ifBlank { text }
        }

    private fun parse(txt: String): List<Item> {
        val s = txt.indexOf('[')
        val e = txt.lastIndexOf(']')
        if (s < 0 || e <= s) throw Exception("ШІ повернув неочікувану відповідь")
        val arr = JSONArray(txt.substring(s, e + 1))
        val out = ArrayList<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            out.add(
                Item(
                    Food(
                        name = name,
                        kcal = o.optDouble("kcal", 0.0),
                        protein = o.optDouble("protein", 0.0),
                        fat = o.optDouble("fat", 0.0),
                        carbs = o.optDouble("carbs", 0.0)
                    ),
                    o.optDouble("grams", 100.0).coerceAtLeast(1.0)
                )
            )
        }
        return out
    }
}

fun uriToBase64(ctx: Context, uri: Uri): Pair<String, Bitmap>? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val scale = 1024f / maxOf(bmp.width, bmp.height)
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return Pair(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), scaled)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiFoodScreen(st: AppState, meal: Int, onBack: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var m by remember { mutableIntStateOf(meal) }
    var text by remember { mutableStateOf("") }
    var img by remember { mutableStateOf<String?>(null) }
    var thumb by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<AiApi.Item>>(emptyList()) }
    var picked by remember { mutableStateOf<AiApi.Item?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val r = try { uriToBase64(ctx, uri) } catch (e: Exception) { null }
            img = r?.first
            thumb = r?.second
            msg = if (r == null) "Не вдалося прочитати фото" else ""
        }
    }

    val hasKey = st.currentKey().isNotBlank()
    val canRun = hasKey && !loading && (text.isNotBlank() || img != null)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (st.aiProvider == 1) "ШІ-помічник · Gemini" else "ШІ-помічник · Claude") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") } }
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!hasKey) {
                item { Text("Вкажіть API-ключ вибраного ШІ (Claude або Gemini) у вкладці «Профіль».", color = MaterialTheme.colorScheme.error) }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MEALS.forEachIndexed { i, n -> Chip(n, m == i) { m = i } }
                }
            }
            item {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Що ви з'їли?") },
                    placeholder = { Text("наприклад: тарілка борщу зі сметаною і 2 скибки хліба") },
                    minLines = 3, modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { launcher.launch("image/*") }) {
                        Text(if (img != null) "Змінити фото" else "Додати фото")
                    }
                    Button(
                        enabled = canRun,
                        onClick = {
                            scope.launch {
                                loading = true; msg = ""; found = emptyList()
                                try {
                                    found = AiApi.analyze(st.aiProvider, st.currentKey(), st.geminiModel, text.trim(), img)
                                    if (found.isEmpty()) msg = "ШІ не знайшов їжі в описі"
                                } catch (e: Exception) {
                                    msg = "Помилка: ${e.message}"
                                }
                                loading = false
                            }
                        }
                    ) { Text("Розпізнати") }
                }
            }
            val tb = thumb
            if (tb != null) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))) {
                        Image(
                            bitmap = tb.asImageBitmap(), contentDescription = "Фото страви",
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp).clip(CircleShape)
                                .background(Color(0x99000000)).clickable { img = null; thumb = null },
                            contentAlignment = Alignment.Center
                        ) { Text("✕", color = Color.White) }
                    }
                }
            }
            if (loading) item { CircularProgressIndicator(Modifier.padding(8.dp)) }
            if (msg.isNotEmpty()) item { Text(msg, color = MaterialTheme.colorScheme.error) }
            if (found.isNotEmpty()) {
                item {
                    val total = found.sumOf { it.food.kcal * it.grams / 100 }
                    Text("Разом: ${total.f0()} ккал. Торкніться продукту, щоб змінити вагу й додати лише його.", fontSize = 13.sp)
                }
                items(found) { it0 ->
                    AppCard(Modifier.fillMaxWidth().clickable { picked = it0 }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(it0.food.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${it0.grams.f0()} г · Б ${(it0.food.protein * it0.grams / 100).f0()} " +
                                        "Ж ${(it0.food.fat * it0.grams / 100).f0()} В ${(it0.food.carbs * it0.grams / 100).f0()}",
                                    fontSize = 12.sp
                                )
                            }
                            Text("${(it0.food.kcal * it0.grams / 100).f0()} ккал", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            found.forEach { st.addEntry(m, it.food, it.grams) }
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Додати все в «${MEALS[m]}»") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val p = picked
    if (p != null) {
        GramsDialog(
            title = p.food.name, initial = p.grams, kcal100 = p.food.kcal,
            onOk = { g ->
                st.addEntry(m, p.food, g)
                val rest = found - p
                found = rest
                picked = null
                if (rest.isEmpty()) onDone()
            },
            onDismiss = { picked = null }
        )
    }
}
