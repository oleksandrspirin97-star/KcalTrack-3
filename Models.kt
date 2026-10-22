package com.example.kcaltrack

val MEALS = listOf("Сніданок", "Обід", "Вечеря", "Перекуси")
val ACTIVITY_NAMES = listOf("Сидячий спосіб життя", "Легка активність", "Помірна активність", "Висока активність")
val GOAL_NAMES = listOf("Схуднути", "Підтримувати вагу", "Набрати вагу")

data class Food(
    val id: Long = 0,
    val name: String,
    val brand: String = "",
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val barcode: String = "",
    val fav: Boolean = false
)

data class Entry(
    val id: Long,
    val date: String,
    val meal: Int,
    val name: String,
    val grams: Double,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carbs100: Double
) {
    val kcal: Double get() = kcal100 * grams / 100
    val protein: Double get() = protein100 * grams / 100
    val fat: Double get() = fat100 * grams / 100
    val carbs: Double get() = carbs100 * grams / 100
}

data class Exercise(val id: Long, val date: String, val name: String, val minutes: Int, val kcal: Int)

data class WeightPoint(val date: String, val kg: Double)

data class Profile(
    val male: Boolean = true,
    val age: Int = 30,
    val heightCm: Int = 175,
    val weightKg: Double = 75.0,
    val activity: Int = 1,
    val goal: Int = 1,
    val manual: Boolean = false,
    val manualKcal: Int = 2000,
    val waterGoalMl: Int = 2000,
    val name: String = ""
) {
    /** Формула Миффлина — Сан-Жеора */
    fun calorieGoal(): Int {
        if (manual) return manualKcal
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + (if (male) 5 else -161)
        val factor = doubleArrayOf(1.2, 1.375, 1.55, 1.725)[activity.coerceIn(0, 3)]
        val adj = when (goal) { 0 -> -500; 2 -> 300; else -> 0 }
        return (bmr * factor + adj).toInt().coerceAtLeast(1200)
    }
}

data class DayStat(
    val date: java.time.LocalDate,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val burned: Int,
    val water: Int
)
