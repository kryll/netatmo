package com.arsys.netatmo.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        gson.fromJson(value, object : TypeToken<List<String>>() {}.type) ?: emptyList()

    @TypeConverter
    fun fromLong(value: Long?): String? = value?.toString()

    @TypeConverter
    fun toLong(value: String?): Long? = value?.toLongOrNull()
}
