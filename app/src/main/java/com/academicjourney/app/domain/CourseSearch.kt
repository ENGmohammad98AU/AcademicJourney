package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import java.util.Locale

object CourseSearch {
    fun matches(course: CourseEntity, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return true
        return sequenceOf(course.name, course.code, course.language)
            .map(::normalize)
            .any { normalizedQuery in it }
    }

    internal fun normalize(value: String): String = buildString(value.length) {
        value.lowercase(Locale.ROOT).forEach { character ->
            append(
                when (character) {
                    '٠', '۰' -> '0'
                    '١', '۱' -> '1'
                    '٢', '۲' -> '2'
                    '٣', '۳' -> '3'
                    '٤', '۴' -> '4'
                    '٥', '۵' -> '5'
                    '٦', '۶' -> '6'
                    '٧', '۷' -> '7'
                    '٨', '۸' -> '8'
                    '٩', '۹' -> '9'
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى' -> 'ي'
                    'ة' -> 'ه'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    'ـ', 'َ', 'ً', 'ُ', 'ٌ', 'ِ', 'ٍ', 'ْ', 'ّ' -> ' '
                    else -> character
                }
            )
        }
    }.replace(Regex("\\s+"), " ").trim()
}
