package com.example.kcaltrack

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

class AppState(ctx: Context) {
    val db = Db(ctx)
    private val sp = ctx.getSharedPreferences("profile", Context.MODE_PRIVATE)

    var date by mutableStateOf(LocalDate.now())
    var entries by mutableStateOf(emptyList<Entry>())
    var exercises by mutableStateOf(emptyList<Exercise>())
    var water by mutableStateOf(0)
    var weights by mutableStateOf(emptyList<WeightPoint>())
    var profile by mutableStateOf(loadProfile())
    var apiKey by mutableStateOf(sp.getString("apiKey", "") ?: "")
    var geminiKey by mutableStateOf(sp.getString("geminiKey", "") ?: "")
    var geminiModel by mutableStateOf(sp.getString("geminiModel", AiApi.GEMINI_MODEL) ?: AiApi.GEMINI_MODEL)
    var usdaKey by mutableStateOf(sp.getString("usdaKey", "") ?: "")
    var aiProvider by mutableStateOf(sp.getInt("aiProvider", 0)) // 0 = Claude, 1 = Gemini

    val eaten: Double get() = entries.sumOf { it.kcal }
    val burned: Int get() = exercises.sumOf { it.kcal }

    init { reload() }

    fun reload() {
        val d = date.toString()
        entries = db.entries(d)
        exercises = db.exercises(d)
        water = db.water(d)
        weights = db.weights()
    }

    fun shift(days: Long) { date = date.plusDays(days); reload() }

    fun addEntry(meal: Int, food: Food, grams: Double) {
        db.addEntry(date.toString(), meal, food, grams)
        db.upsertFood(food)
        reload()
    }

    fun setGrams(id: Long, grams: Double) { db.setGrams(id, grams); reload() }
    fun deleteEntry(id: Long) { db.deleteEntry(id); reload() }

    fun addWater(delta: Int) { db.setWater(date.toString(), water + delta); reload() }

    fun addExercise(name: String, minutes: Int, kcal: Int) {
        db.addExercise(date.toString(), name, minutes, kcal); reload()
    }
    fun deleteExercise(id: Long) { db.deleteExercise(id); reload() }

    fun addWeight(kg: Double) {
        db.setWeight(LocalDate.now().toString(), kg)
        saveProfile(profile.copy(weightKg = kg))
        reload()
    }
    fun deleteWeight(date: String) { db.deleteWeight(date); reload() }

    private fun loadProfile(): Profile {
        val d = Profile()
        return Profile(
            male = sp.getBoolean("male", d.male),
            age = sp.getInt("age", d.age),
            heightCm = sp.getInt("height", d.heightCm),
            weightKg = sp.getFloat("weight", d.weightKg.toFloat()).toDouble(),
            activity = sp.getInt("activity", d.activity),
            goal = sp.getInt("goal", d.goal),
            manual = sp.getBoolean("manual", d.manual),
            manualKcal = sp.getInt("manualKcal", d.manualKcal),
            waterGoalMl = sp.getInt("waterGoal", d.waterGoalMl),
            name = sp.getString("name", "") ?: ""
        )
    }

    fun saveUsda(k: String) {
        usdaKey = k
        sp.edit().putString("usdaKey", k).apply()
    }

    /** Якщо для вибраного ШІ ключа немає, а для іншого є — використовуємо інший. */
    fun activeProvider(): Int {
        val selKey = if (aiProvider == 1) geminiKey else apiKey
        val otherKey = if (aiProvider == 1) apiKey else geminiKey
        return if (selKey.isBlank() && otherKey.isNotBlank()) 1 - aiProvider else aiProvider
    }

    fun currentKey(): String = if (activeProvider() == 1) geminiKey else apiKey

    fun saveAi(provider: Int, claudeKey: String, gKey: String, gModel: String) {
        aiProvider = provider
        apiKey = claudeKey
        geminiKey = gKey
        geminiModel = gModel
        sp.edit().putInt("aiProvider", provider).putString("apiKey", claudeKey)
            .putString("geminiKey", gKey).putString("geminiModel", gModel).apply()
    }

    fun saveApiKey(k: String) {
        apiKey = k
        sp.edit().putString("apiKey", k).apply()
    }

    /** Статистика за 7 днів, що закінчуються датою [end]. */
    fun week(end: LocalDate): List<DayStat> = (6 downTo 0).map { off ->
        val d = end.minusDays(off.toLong())
        val s = d.toString()
        val es = db.entries(s)
        DayStat(d, es.sumOf { it.kcal }, es.sumOf { it.protein }, es.sumOf { it.fat }, es.sumOf { it.carbs },
            db.exercises(s).sumOf { it.kcal }, db.water(s))
    }

    fun saveProfile(p: Profile) {
        profile = p
        sp.edit()
            .putBoolean("male", p.male).putInt("age", p.age).putInt("height", p.heightCm)
            .putFloat("weight", p.weightKg.toFloat()).putInt("activity", p.activity).putInt("goal", p.goal)
            .putBoolean("manual", p.manual).putInt("manualKcal", p.manualKcal).putInt("waterGoal", p.waterGoalMl).putString("name", p.name)
            .apply()
    }
}
