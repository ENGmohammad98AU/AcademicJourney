package com.academicjourney.app.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** A standard PDF attachment: raw inputs, including nulls and notes, not OCR of rounded results. */
object PdfRestoreData {
    internal const val ATTACHMENT_NAME = "AcademicJourney-data.json"
    private const val FORMAT = "com.academicjourney.app.pdf-restore"

    fun attach(context: Context, pdf: ByteArray, backup: JSONObject): ByteArray {
        PDFBoxResourceLoader.init(context.applicationContext)
        val payload = backup.toString()
        val envelope = JSONObject().put("formatId", FORMAT).put("version", 1)
            .put("sha256", sha256(payload)).put("payload", payload).toString().toByteArray(Charsets.UTF_8)
        require(envelope.size <= AcademicImportFile.MAX_DATA_BYTES) { "بيانات التقرير أكبر من الحد المسموح." }
        PDDocument.load(pdf).use { document ->
            val embedded = PDEmbeddedFile(document, envelope.inputStream()).apply {
                subtype = "application/json"
                size = envelope.size
            }
            val spec = PDComplexFileSpecification().apply {
                file = ATTACHMENT_NAME
                fileUnicode = ATTACHMENT_NAME
                embeddedFile = embedded
                embeddedFileUnicode = embedded
                fileDescription = "AcademicJourney restore data for this report only"
            }
            val names = PDDocumentNameDictionary(document.documentCatalog)
            names.embeddedFiles = PDEmbeddedFilesNameTreeNode().apply { this.names = mapOf(ATTACHMENT_NAME to spec) }
            document.documentCatalog.names = names
            document.documentInformation.producer = "AcademicJourney"
            return ByteArrayOutputStream().use { output -> document.save(output); output.toByteArray().also { require(it.size <= AcademicImportFile.MAX_FILE_BYTES) { "حجم التقرير يتجاوز 32 ميغابايت." } } }
        }
    }

    fun extract(context: Context, bytes: ByteArray): JSONObject {
        PDFBoxResourceLoader.init(context.applicationContext)
        val document = try {
            PDDocument.load(bytes.inputStream(), MemoryUsageSetting.setupMixed(8L * 1024 * 1024, 64L * 1024 * 1024)
                .setTempDir(context.cacheDir))
        } catch (_: InvalidPasswordException) {
            error("ملف PDF محمي بكلمة مرور. اختر نسخة غير محمية ومصدّرة من التطبيق.")
        } catch (_: Exception) {
            error("ملف PDF غير مكتمل أو تالف. أعد تنزيله أو تصديره؛ لم يتم تغيير أي بيانات.")
        }
        document.use {
            require(!it.isEncrypted) { "ملف PDF محمي. اختر نسخة غير محمية من التقرير." }
            require(it.numberOfPages in 1..300) { "عدد صفحات PDF غير مدعوم للاستعادة (الحد 300)." }
            val tree = it.documentCatalog.names?.embeddedFiles
            val matches = mutableListOf<PDComplexFileSpecification>()
            var nodes = 0
            fun visit(node: PDEmbeddedFilesNameTreeNode?, depth: Int) {
                if (node == null) return
                require(depth <= 8 && ++nodes <= 100) { "بنية مرفقات PDF غير مدعومة." }
                node.names?.get(ATTACHMENT_NAME)?.let(matches::add)
                node.kids?.forEach { kid -> visit(kid as PDEmbeddedFilesNameTreeNode, depth + 1) }
            }
            visit(tree, 0)
            require(matches.size <= 1) { "يحتوي PDF أكثر من نسخة بيانات؛ تعذر تحديد النسخة الصحيحة." }
            val spec = matches.singleOrNull() ?: error(
                "هذا PDF قابل للعرض لكنه لا يحتوي بيانات الاستعادة. التقارير القديمة أو المصوّرة لا تُستورد آليًا. " +
                    "أعد تصديره من الإصدار 1.5.2 أو أحدث، أو استخدم نسخة JSON. لم تتغير بياناتك."
            )
            val embedded = spec.embeddedFileUnicode ?: spec.embeddedFile
                ?: error("مرفق بيانات الاستعادة مفقود من PDF.")
            val raw = embedded.createInputStream().use { stream -> AcademicImportFile.readLimited(stream, AcademicImportFile.MAX_DATA_BYTES) }
            val envelope = runCatching { JSONObject(raw.toString(Charsets.UTF_8)) }
                .getOrElse { error("بيانات الاستعادة المرفقة بـPDF تالفة؛ اختر نسخة أخرى.") }
            require(envelope.optString("formatId") == FORMAT && envelope.optInt("version") == 1) {
                "صيغة بيانات PDF غير مدعومة. قد تحتاج إلى تحديث التطبيق."
            }
            val payload = envelope.getString("payload")
            require(sha256(payload) == envelope.optString("sha256")) {
                "فشل التحقق من سلامة بيانات PDF. لم يتم تغيير أي بيانات. أعد تصدير التقرير."
            }
            return runCatching { JSONObject(payload) }.getOrElse { error("بيانات الاستعادة داخل PDF غير مكتملة.") }
        }
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
