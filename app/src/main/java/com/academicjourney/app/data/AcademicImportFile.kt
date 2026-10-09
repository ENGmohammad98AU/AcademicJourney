package com.academicjourney.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Detect the file by its bytes, including providers which return an incorrect MIME type. */
object AcademicImportFile {
    const val MAX_FILE_BYTES = 32 * 1024 * 1024
    const val MAX_DATA_BYTES = 20 * 1024 * 1024
    data class Content(val json: JSONObject, val sourceDescription: String)

    fun read(context: Context, uri: Uri): Content {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_FILE_BYTES) }
            ?: error("تعذر فتح الملف. أعد اختياره من مدير الملفات.")
        return decode(context, bytes)
    }

    fun decode(context: Context, bytes: ByteArray): Content {
        require(bytes.isNotEmpty()) { "الملف فارغ. أعد تصديره ثم اختر النسخة المكتملة." }
        require(bytes.size <= MAX_FILE_BYTES) { "حجم الملف يتجاوز 32 ميغابايت." }
        if (bytes.take(5).toByteArray().contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))) {
            return Content(PdfRestoreData.extract(context, bytes), "تقرير PDF • استعادة بيانات الفرع الموجود في التقرير فقط")
        }
        require(bytes.size <= MAX_DATA_BYTES) { "بيانات النسخة الاحتياطية أكبر من الحد المسموح." }
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF").trim()
        require(text.startsWith("{")) {
            "نوع الملف غير مدعوم. اختر تقرير PDF مُصدّرًا من التطبيق أو نسخة احتياطية JSON."
        }
        val root = runCatching { JSONObject(text) }.getOrElse {
            error("ملف JSON ناقص أو تالف. أعد تصديره أو اختر نسخة أخرى؛ لم تتغير بياناتك.")
        }
        return Content(root, "نسخة احتياطية JSON")
    }

    internal fun readLimited(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(output.size().toLong() + n <= maximum) { "حجم البيانات يتجاوز الحد المسموح؛ لم يتم تغيير أي بيانات." }
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }
}
