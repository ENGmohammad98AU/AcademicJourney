package com.academicjourney.app.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.HighSchoolGradeEntity
import com.academicjourney.app.data.ProgramEntity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

/** Export and printing use the same native, restorable PDF. */
object ReportPrinter {
    fun exportProgramReport(context: Context, uri: Uri, universityName: String, program: ProgramEntity,
        courses: List<CourseEntity>, onResult: (String) -> Unit) = export(context, uri, onResult) {
        AcademicReportPdf.program(context, universityName, program, courses)
    }
    fun exportHighSchoolReport(context: Context, uri: Uri, branchTitle: String,
        grades: List<HighSchoolGradeEntity>, onResult: (String) -> Unit) = export(context, uri, onResult) {
        AcademicReportPdf.highSchool(context, branchTitle, grades)
    }
    fun printProgramReport(context: Context, universityName: String, program: ProgramEntity,
        courses: List<CourseEntity>, onResult: (String) -> Unit = {}) = print(context, program.name, true, onResult) {
        AcademicReportPdf.program(context, universityName, program, courses)
    }
    fun printHighSchoolReport(context: Context, branchTitle: String,
        grades: List<HighSchoolGradeEntity>, onResult: (String) -> Unit = {}) = print(context, branchTitle, false, onResult) {
        AcademicReportPdf.highSchool(context, branchTitle, grades)
    }
    private fun export(context: Context, uri: Uri, onResult: (String) -> Unit, build: () -> ByteArray) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                // Finish and validate the PDF before opening/truncating the destination.
                val bytes = build()
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes); it.flush() }
                    ?: error("تعذر فتح الملف المحدد للكتابة.")
            } }
            onResult(result.fold({ "تم تصدير PDF قابل للعرض والطباعة واستعادة درجات هذا الفرع." },
                { "تعذر تصدير PDF: ${it.message ?: "أعد اختيار مكان الحفظ."}" }))
        }
    }
    private fun print(context: Context, title: String, landscape: Boolean, onResult: (String) -> Unit, build: () -> ByteArray) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val result = runCatching {
                val bytes = withContext(Dispatchers.IO) { build() }
                val pages = withContext(Dispatchers.IO) { PDDocument.load(bytes).use { it.numberOfPages } }
                val media = if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4
                val attributes = PrintAttributes.Builder().setMediaSize(media).setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build()
                (context.getSystemService(Context.PRINT_SERVICE) as PrintManager)
                    .print("تقرير-$title", RestorablePdfPrintAdapter("AcademicJourney.pdf", bytes, pages), attributes)
            }
            onResult(result.fold({ "تم فتح نافذة الطباعة. يمكنك اختيار «حفظ كـ PDF» أيضًا." },
                { "تعذر فتح الطباعة: ${it.message ?: "تحقق من خدمة الطباعة على الجهاز."}" }))
        }
    }
}

internal class RestorablePdfPrintAdapter(private val name: String, private val bytes: ByteArray, private val pageCount: Int) : PrintDocumentAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
        callback.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount).build(), oldAttributes != newAttributes)
    }
    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal,
        callback: WriteResultCallback) {
        scope.launch {
            if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return@launch }
            val result = runCatching { withContext(Dispatchers.IO) {
                val selected = (0 until pageCount).filter { page -> pages.any { page in it.start..it.end } }
                require(selected.isNotEmpty()) { "لم تُحدد صفحات للطباعة." }
                val output = selectPages(bytes, selected, pageCount)
                if (!cancellationSignal.isCanceled) FileOutputStream(destination.fileDescriptor).use { it.write(output) }
                selected
            } }
            if (cancellationSignal.isCanceled) callback.onWriteCancelled()
            else result.fold({ selected -> callback.onWriteFinished(selected.map { PageRange(it, it) }.toTypedArray()) },
                { callback.onWriteFailed(it.message ?: "تعذرت كتابة ملف الطباعة.") })
        }
    }
    override fun onFinish() { scope.cancel() }
    companion object {
        internal fun selectPages(bytes: ByteArray, selected: List<Int>, total: Int): ByteArray {
            if (selected.size == total) return bytes
            return PDDocument.load(bytes).use { doc ->
                for (index in total - 1 downTo 0) if (index !in selected) doc.removePage(index)
                ByteArrayOutputStream().use { output -> doc.save(output); output.toByteArray() }
            }
        }
    }
}
