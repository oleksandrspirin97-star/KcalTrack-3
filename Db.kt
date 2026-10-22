package com.example.kcaltrack

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "kcal.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT, meal INTEGER, name TEXT, grams REAL, kcal REAL, protein REAL, fat REAL, carbs REAL)")
        db.execSQL("CREATE TABLE foods(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, brand TEXT, kcal REAL, protein REAL, fat REAL, carbs REAL, barcode TEXT, used INTEGER, fav INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE exercises(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT, name TEXT, minutes INTEGER, kcal INTEGER)")
        db.execSQL("CREATE TABLE weights(date TEXT PRIMARY KEY, kg REAL)")
        db.execSQL("CREATE TABLE water(date TEXT PRIMARY KEY, ml INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE foods ADD COLUMN fav INTEGER DEFAULT 0")
    }

    // ---------- Дневник питания ----------
    fun entries(date: String): List<Entry> =
        readableDatabase.rawQuery(
            "SELECT id,date,meal,name,grams,kcal,protein,fat,carbs FROM entries WHERE date=? ORDER BY id",
            arrayOf(date)
        ).use { c ->
            val list = ArrayList<Entry>()
            while (c.moveToNext()) {
                list.add(Entry(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3), c.getDouble(4),
                    c.getDouble(5), c.getDouble(6), c.getDouble(7), c.getDouble(8)))
            }
            list
        }

    fun addEntry(date: String, meal: Int, f: Food, grams: Double) {
        val cv = ContentValues().apply {
            put("date", date); put("meal", meal); put("name", f.name); put("grams", grams)
            put("kcal", f.kcal); put("protein", f.protein); put("fat", f.fat); put("carbs", f.carbs)
        }
        writableDatabase.insert("entries", null, cv)
    }

    fun setGrams(id: Long, grams: Double) {
        val cv = ContentValues().apply { put("grams", grams) }
        writableDatabase.update("entries", cv, "id=?", arrayOf(id.toString()))
    }

    fun deleteEntry(id: Long) {
        writableDatabase.delete("entries", "id=?", arrayOf(id.toString()))
    }

    // ---------- Мої продукти ----------
    fun upsertFood(f: Food) {
        val db = writableDatabase
        val id = db.rawQuery("SELECT id FROM foods WHERE name=? AND brand=?", arrayOf(f.name, f.brand))
            .use { if (it.moveToFirst()) it.getLong(0) else -1L }
        val cv = ContentValues().apply {
            put("name", f.name); put("brand", f.brand); put("kcal", f.kcal); put("protein", f.protein)
            put("fat", f.fat); put("carbs", f.carbs); put("barcode", f.barcode)
            put("used", System.currentTimeMillis())
        }
        if (id > 0) db.update("foods", cv, "id=?", arrayOf(id.toString())) else db.insert("foods", null, cv)
    }

    fun foods(query: String): List<Food> {
        val all = readableDatabase.rawQuery(
            "SELECT id,name,brand,kcal,protein,fat,carbs,barcode,fav FROM foods ORDER BY fav DESC, used DESC LIMIT 500", null
        ).use { c ->
            val list = ArrayList<Food>()
            while (c.moveToNext()) {
                list.add(Food(c.getLong(0), c.getString(1), c.getString(2) ?: "", c.getDouble(3),
                    c.getDouble(4), c.getDouble(5), c.getDouble(6), c.getString(7) ?: "", c.getInt(8) == 1))
            }
            list
        }
        val q = query.trim()
        return if (q.isEmpty()) all else all.filter { it.name.contains(q, true) || it.brand.contains(q, true) }
    }

    /** Додає/прибирає продукт з вибраного (якщо продукту ще немає в базі — спершу зберігає його). */
    fun toggleFav(f: Food) {
        val db = writableDatabase
        val row = db.rawQuery("SELECT id,fav FROM foods WHERE name=? AND brand=?", arrayOf(f.name, f.brand))
            .use { if (it.moveToFirst()) Pair(it.getLong(0), it.getInt(1)) else null }
        if (row == null) {
            val cv = ContentValues().apply {
                put("name", f.name); put("brand", f.brand); put("kcal", f.kcal); put("protein", f.protein)
                put("fat", f.fat); put("carbs", f.carbs); put("barcode", f.barcode)
                put("used", System.currentTimeMillis()); put("fav", 1)
            }
            db.insert("foods", null, cv)
        } else {
            val cv = ContentValues().apply { put("fav", if (row.second == 1) 0 else 1) }
            db.update("foods", cv, "id=?", arrayOf(row.first.toString()))
        }
    }

    fun deleteFood(id: Long) {
        writableDatabase.delete("foods", "id=?", arrayOf(id.toString()))
    }

    // ---------- Упражнения ----------
    fun exercises(date: String): List<Exercise> =
        readableDatabase.rawQuery("SELECT id,date,name,minutes,kcal FROM exercises WHERE date=? ORDER BY id", arrayOf(date))
            .use { c ->
                val list = ArrayList<Exercise>()
                while (c.moveToNext()) list.add(Exercise(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4)))
                list
            }

    fun addExercise(date: String, name: String, minutes: Int, kcal: Int) {
        val cv = ContentValues().apply { put("date", date); put("name", name); put("minutes", minutes); put("kcal", kcal) }
        writableDatabase.insert("exercises", null, cv)
    }

    fun deleteExercise(id: Long) {
        writableDatabase.delete("exercises", "id=?", arrayOf(id.toString()))
    }

    // ---------- Вес ----------
    fun weights(): List<WeightPoint> =
        readableDatabase.rawQuery("SELECT date,kg FROM weights ORDER BY date", null).use { c ->
            val list = ArrayList<WeightPoint>()
            while (c.moveToNext()) list.add(WeightPoint(c.getString(0), c.getDouble(1)))
            list
        }

    fun setWeight(date: String, kg: Double) {
        val cv = ContentValues().apply { put("date", date); put("kg", kg) }
        writableDatabase.insertWithOnConflict("weights", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteWeight(date: String) {
        writableDatabase.delete("weights", "date=?", arrayOf(date))
    }

    // ---------- Вода ----------
    fun water(date: String): Int =
        readableDatabase.rawQuery("SELECT ml FROM water WHERE date=?", arrayOf(date))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun setWater(date: String, ml: Int) {
        val cv = ContentValues().apply { put("date", date); put("ml", ml.coerceAtLeast(0)) }
        writableDatabase.insertWithOnConflict("water", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
