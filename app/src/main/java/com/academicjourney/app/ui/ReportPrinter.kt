package com.academicjourney.app.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.HighSchoolGradeEntity
import com.academicjourney.app.data.ProgramEntity
import com.academicjourney.app.domain.GradeCalculator
import com.academicjourney.app.domain.HighSchoolCalculator
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object ReportPrinter {
    fun printProgramReport(
        context: Context,
        universityName: String,
        program: ProgramEntity,
        courses: List<CourseEntity>
    ) {
        val identifierLabel = if (program.gradingScheme == GradeCalculator.SVU_WEIGHTED) {
            "رمز المقرر"
        } else {
            "رقم المقرر"
        }
        val sortedCourses = courses.sortedWith(
            compareBy<CourseEntity> { it.academicYear }.thenBy { it.semester }.thenBy { it.name }
        )
        val results = sortedCourses.map { GradeCalculator.calculate(it, program) }
        val graded = results.mapNotNull { it.finalGrade }
        val passed = results.count { it.isPassed == true }
        val failed = results.count { it.isPassed == false }
        val rows = sortedCourses.mapIndexed { index, course ->
            val result = GradeCalculator.calculate(course, program)
            val status = when (result.isPassed) {
                true -> "<span class='passed'>ناجح</span>"
                false -> "<span class='failed'>راسب</span>"
                null -> "غير مُقيّم"
            }
            """
            <tr>
              <td>${index + 1}</td>
              <td>${escape(course.name)}</td>
              <td>${escape(course.code.ifBlank { "—" })}</td>
              <td>${course.academicYear}</td>
              <td>${course.semester}</td>
              <td>${result.finalGrade?.let(::formatGrade) ?: "—"}</td>
              <td>$status</td>
              <td>${escape(course.notes.ifBlank { "—" })}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        val average = graded.takeIf { it.isNotEmpty() }?.average()?.let(::formatGrade) ?: "—"
        val body = """
            <h1>تقرير ${escape(program.name)}</h1>
            <p class="subtitle">${escape(universityName)}</p>
            <div class="summary">
              <div><b>عدد المقررات:</b> ${courses.size}</div>
              <div><b>المقررات المُقيّمة:</b> ${graded.size}</div>
              <div><b>الناجحة:</b> $passed</div>
              <div><b>الراسبة:</b> $failed</div>
              <div><b>المعدل:</b> $average</div>
              <div><b>حد النجاح:</b> ${formatGrade(program.passingGrade)}</div>
            </div>
            <table>
              <thead><tr><th>#</th><th>المقرر</th><th>$identifierLabel</th><th>السنة</th><th>الفصل</th><th>الدرجة</th><th>الحالة</th><th>الملاحظات</th></tr></thead>
              <tbody>$rows</tbody>
            </table>
        """.trimIndent()
        printHtml(context, "تقرير-${program.name}", document(body))
    }

    fun printHighSchoolReport(
        context: Context,
        branchTitle: String,
        grades: List<HighSchoolGradeEntity>
    ) {
        val summary = HighSchoolCalculator.calculate(grades)
        val rows = grades.sortedBy { it.displayOrder }.mapIndexed { index, item ->
            val subjectPercentage = HighSchoolCalculator.subjectPercentage(item)
            """
            <tr>
              <td>${index + 1}</td>
              <td>${escape(item.subject)}</td>
              <td>${item.grade ?: "—"}</td>
              <td>${item.maxGrade}</td>
              <td>${subjectPercentage?.let { "${formatGrade(it)}%" } ?: "—"}</td>
              <td>${if (item.includedInPercentage) "نعم" else "لا"}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")
        val excluded = grades.filterNot { it.includedInPercentage }
            .joinToString("، ") { it.subject }
            .ifBlank { "لا توجد مواد مستبعدة" }
        val body = """
            <h1>تقرير $branchTitle</h1>
            <p class="subtitle">الشهادة الثانوية العامة</p>
            <div class="summary">
              <div><b>المجموع المحتسب:</b> ${summary.totalGrade} / ${summary.maximumGrade}</div>
              <div><b>النسبة:</b> ${formatGrade(summary.percentage)}%</div>
              <div><b>الدرجات المدخلة:</b> ${grades.count { it.grade != null }} / ${grades.size}</div>
              <div><b>المواد غير المحتسبة:</b> ${escape(excluded)}</div>
            </div>
            <table>
              <thead><tr><th>#</th><th>المادة</th><th>الدرجة</th><th>الدرجة العظمى</th><th>نسبة المادة</th><th>محتسبة بالنسبة العامة</th></tr></thead>
              <tbody>$rows</tbody>
            </table>
        """.trimIndent()
        printHtml(context, "تقرير-$branchTitle", document(body))
    }

    private fun printHtml(context: Context, jobName: String, html: String) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            private var printStarted = false

            override fun onPageFinished(view: WebView, url: String?) {
                if (printStarted) return
                printStarted = true
                view.post {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val adapter = view.createPrintDocumentAdapter(jobName)
                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .build()
                    printManager.print(jobName, adapter, attributes)
                }
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun document(body: String): String {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale("ar")).format(Date())
        return """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
              <meta charset="utf-8">
              <style>
                @page { size: A4; margin: 14mm; }
                body { font-family: sans-serif; color: #172235; line-height: 1.55; font-size: 11px; }
                h1 { margin: 0; color: #145d70; font-size: 22px; }
                .subtitle { color: #586575; margin: 3px 0 12px; }
                .summary { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; padding: 10px; background: #eef7f8; border-radius: 8px; margin-bottom: 14px; }
                table { width: 100%; border-collapse: collapse; }
                th, td { border: 1px solid #ccd6dd; padding: 6px; text-align: right; vertical-align: top; }
                th { background: #dceff2; color: #123f4a; }
                tr:nth-child(even) { background: #f7f9fa; }
                .passed { color: #146c38; font-weight: bold; }
                .failed { color: #b3261e; font-weight: bold; }
                footer { margin-top: 12px; color: #6d7784; font-size: 9px; text-align: center; }
              </style>
            </head>
            <body>$body<footer>مسيرتي الأكاديمية • تاريخ التقرير: $date</footer></body>
            </html>
        """.trimIndent()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun formatGrade(value: Double): String = String.format(Locale.US, "%.2f", value)
}
