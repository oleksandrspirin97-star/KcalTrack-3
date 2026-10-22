package com.example.kcaltrack

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Відкрита база продуктів Open Food Facts (безкоштовна, без ключа). */
object OffApi {
    private const val FIELDS = "code,product_name,brands,nutriments"

    internal fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "KcalTrack/1.0 (open-source calorie tracker; Android)")
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw Exception("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Значення може бути рядком, масивом або об'єктом з мовами. */
    private fun str(v: Any?): String = when (v) {
        is String -> v
        is JSONArray -> if (v.length() > 0) str(v.opt(0)) else ""
        is JSONObject -> {
            val it = v.keys()
            if (v.has("main")) str(v.opt("main")) else if (it.hasNext()) str(v.opt(it.next())) else ""
        }
        else -> ""
    }

    private fun toFood(p: JSONObject): Food? {
        val name = str(p.opt("product_name")).trim()
        if (name.isEmpty()) return null
        val n = p.optJSONObject("nutriments") ?: return null
        var kcal = n.optDouble("energy-kcal_100g", Double.NaN)
        if (kcal.isNaN()) kcal = n.optDouble("energy-kcal", Double.NaN)
        if (kcal.isNaN()) {
            var kj = n.optDouble("energy_100g", Double.NaN)
            if (kj.isNaN()) kj = n.optDouble("energy-kj_100g", Double.NaN)
            if (kj.isNaN()) return null
            kcal = kj / 4.184
        }
        return Food(
            name = name,
            brand = str(p.opt("brands")).split(",").first().trim(),
            kcal = kcal,
            protein = n.optDouble("proteins_100g", 0.0),
            fat = n.optDouble("fat_100g", 0.0),
            carbs = n.optDouble("carbohydrates_100g", 0.0),
            barcode = str(p.opt("code"))
        )
    }

    private fun parseList(arr: JSONArray?): List<Food> {
        val out = ArrayList<Food>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            toFood(o)?.let { out.add(it) }
        }
        return out
    }

    private fun hitsOf(url: String): List<Food> {
        val json = JSONObject(httpGet(url))
        return parseList(json.optJSONArray("hits") ?: json.optJSONArray("products"))
    }

    /** Текстовий пошук: спершу українські товари, потім усі інші; запасний варіант — старий cgi/search.pl. */
    suspend fun search(query: String): List<Food> = withContext(Dispatchers.IO) {
        val clean = query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val q = URLEncoder.encode(clean, "UTF-8")
        val ua = try {
            val lq = URLEncoder.encode("($clean) AND countries_tags:\"en:ukraine\"", "UTF-8")
            hitsOf("https://search.openfoodfacts.org/search?q=$lq&page_size=15&fields=$FIELDS&langs=uk,ru,en")
        } catch (e: Exception) {
            emptyList()
        }
        var firstError: Exception? = null
        var firstOk = false
        val general = try {
            val r = hitsOf("https://search.openfoodfacts.org/search?q=$q&page_size=30&fields=$FIELDS&langs=uk,ru,en")
            firstOk = true
            r
        } catch (e: Exception) {
            firstError = e
            emptyList()
        }
        val merged = LinkedHashMap<String, Food>()
        for (f in ua + general) merged.putIfAbsent(if (f.barcode.isNotEmpty()) f.barcode else f.name + f.brand, f)
        if (merged.isNotEmpty()) return@withContext merged.values.toList()
        try {
            hitsOf("https://world.openfoodfacts.org/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=30&fields=$FIELDS")
        } catch (e: Exception) {
            if (firstOk) emptyList()
            else throw Exception("${firstError?.message ?: "?"}; ${e.message}")
        }
    }

    suspend fun byBarcode(code: String): Food? = withContext(Dispatchers.IO) {
        var netErr: Exception? = null
        val urls = listOf(
            "https://world.openfoodfacts.org/api/v3/product/$code?fields=$FIELDS",
            "https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$FIELDS"
        )
        for (u in urls) {
            try {
                val p = JSONObject(httpGet(u)).optJSONObject("product")
                if (p != null) return@withContext toFood(p)
            } catch (e: Exception) {
                if (e.message != "HTTP 404") netErr = e
            }
        }
        if (netErr != null) throw netErr
        null
    }
}

/** USDA FoodData Central — загальні продукти (назви англійською). Ключ: https://api.data.gov/signup/ */
object UsdaApi {
    suspend fun search(query: String, key: String): List<Food> = withContext(Dispatchers.IO) {
        val k = key.ifBlank { "DEMO_KEY" }
        val q = URLEncoder.encode(query, "UTF-8")
        val dt = URLEncoder.encode("Foundation,SR Legacy", "UTF-8")
        val text = try {
            OffApi.httpGet("https://api.nal.usda.gov/fdc/v1/foods/search?query=$q&pageSize=20&dataType=$dt&api_key=$k")
        } catch (e: Exception) {
            if (e.message == "HTTP 429") throw Exception("ліміт запитів, вкажіть власний ключ USDA у «Профілі»")
            throw e
        }
        val arr = JSONObject(text).optJSONArray("foods") ?: return@withContext emptyList()
        val out = ArrayList<Food>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("description").trim()
            val ns = o.optJSONArray("foodNutrients") ?: continue
            var kcal = Double.NaN
            var kj = Double.NaN
            var p = 0.0
            var f = 0.0
            var c = 0.0
            for (j in 0 until ns.length()) {
                val n = ns.optJSONObject(j) ?: continue
                val v = n.optDouble("value", Double.NaN)
                when (n.optInt("nutrientId")) {
                    1008 -> kcal = v
                    1062 -> kj = v
                    1003 -> p = if (v.isNaN()) 0.0 else v
                    1004 -> f = if (v.isNaN()) 0.0 else v
                    1005 -> c = if (v.isNaN()) 0.0 else v
                }
            }
            if (kcal.isNaN() && !kj.isNaN()) kcal = kj / 4.184
            if (name.isEmpty() || kcal.isNaN()) continue
            out.add(Food(name = name, brand = "USDA", kcal = kcal, protein = p, fat = f, carbs = c))
        }
        out
    }
}
