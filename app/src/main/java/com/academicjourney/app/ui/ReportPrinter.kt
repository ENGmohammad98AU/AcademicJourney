package com.academicjourney.app.ui

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import com.academicjourney.app.R
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.HighSchoolGradeEntity
import com.academicjourney.app.data.ProgramEntity
import com.academicjourney.app.domain.GradeCalculator
import com.academicjourney.app.domain.HighSchoolCalculator
import com.academicjourney.app.domain.StudentStandingCalculator
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
        val identifierLabel = if (program.gradingScheme == GradeCalculator.SVU_WEIGHTED) "رمز المقرر" else "رقم المقرر"
        val sorted = courses.sortedWith(compareBy<CourseEntity> { it.academicYear }.thenBy { it.semester }.thenBy { it.name })
        val results = sorted.map { GradeCalculator.calculate(it, program) }
        val standing = StudentStandingCalculator.calculate(universityName, program, courses)

        val courseRows = sorted.mapIndexed { index, course ->
            val result = GradeCalculator.calculate(course, program)
            val status = when {
                result.passedWithoutGrade -> "<span class='passed'>ناجح دون علامة</span>"
                result.isPassed == true && result.receivedAssistance -> "<span class='passed'>ناجح بمساعدة</span>"
                result.isPassed == true -> "<span class='passed'>ناجح</span>"
                result.isPassed == false -> "<span class='failed'>راسب</span>"
                else -> "غير مُقيّم"
            }
            val assistance = result.assistancePoints.takeIf { it > 0 }
                ?.let { "<span class='assist'>+$it مساعدة</span>" } ?: "—"
            """
            <tr>
              <td>${index + 1}</td><td class="name">${escape(course.name)}</td>
              <td class="code">${escape(course.code.ifBlank { "—" })}</td>
              <td>${course.academicYear} / ${course.semester}</td><td>${course.creditHours ?: "—"}</td>
              <td>${result.rawGrade?.let(::formatGrade) ?: "—"}</td>
              <td>${result.roundedGrade?.let(::formatGrade) ?: "—"}</td><td>$assistance</td>
              <td class="grade">${result.finalGrade?.let(::formatGrade) ?: "—"}</td><td>$status</td>
              <td>${escape(course.notes.ifBlank { "—" })}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        val averageRows = courses.map { it.academicYear }.distinct().sorted().joinToString("\n") { year ->
            val yearCourses = courses.filter { it.academicYear == year }
            val first = yearCourses.filter { it.semester == 1 }
            val second = yearCourses.filter { it.semester == 2 }
            """
            <tr><td>السنة ${yearName(year)}</td>
              <td>${GradeCalculator.average(first, program)?.let(::formatGrade) ?: "—"}</td>
              <td>${GradeCalculator.average(second, program)?.let(::formatGrade) ?: "—"}</td>
              <td>${GradeCalculator.average(yearCourses, program)?.let(::formatGrade) ?: "—"}</td></tr>
            """.trimIndent()
        }

        val assistanceNotice = if (GradeCalculator.maximumAssistance(program) > 0) {
            "<div class='notice'>تُمنح درجة أو درجتا مساعدة كحد أقصى فقط عندما تكفيان للوصول إلى حد النجاح، وتظهر كل مساعدة في الجدول.</div>"
        } else ""
        val averageNotice = if (courses.any { it.creditHours != null }) {
            "المعدلات موزونة بعدد الساعات، والمقررات الناجحة دون علامة لا تدخل في المعدل."
        } else {
            "المعدلات محسوبة من المقررات التي أُدخلت درجاتها، والنجاح دون علامة لا يدخل في المعدل."
        }

        val body = """
          <header><img src="${universityLogoData(context, universityName)}"><div class="brand">
            <div class="university">${escape(universityName)}</div>
            <h1>تقرير فرع ${escape(program.name)}</h1><div>${escape(program.degreeType)}</div>
          </div></header>
          <section class="summary">
            <div><b>حالة الطالب:</b> ${escape(standing.title)}</div>
            <div><b>المعدل العام:</b> ${GradeCalculator.average(courses, program)?.let(::formatGrade) ?: "—"}</div>
            <div><b>المقررات:</b> ${courses.size}</div><div><b>المُقيّمة:</b> ${results.count { it.finalGrade != null }}</div>
            <div><b>الناجحة:</b> <span class="passed">${results.count { it.isPassed == true }}</span></div>
            <div><b>الراسبة:</b> <span class="failed">${results.count { it.isPassed == false }}</span></div>
            <div><b>نجاح دون علامة:</b> ${results.count { it.passedWithoutGrade }}</div>
            <div><b>نتائج بمساعدة:</b> ${results.count { it.receivedAssistance }}</div>
          </section>
          <div class="standing">${escape(standing.details)}</div>
          <div class="notice">قاعدة عامة: أي كسر في محصلة الدرجة يُجبر إلى العدد الصحيح الأعلى؛ مثال 76.1 تصبح 77.</div>
          $assistanceNotice
          <h2>المعدلات الفصلية والسنوية</h2><div class="note">$averageNotice</div>
          <table><thead><tr><th>السنة</th><th>الفصل الأول</th><th>الفصل الثاني</th><th>المعدل السنوي</th></tr></thead><tbody>$averageRows</tbody></table>
          <h2>المقررات والدرجات</h2>
          <table class="courses"><thead><tr><th>#</th><th>اسم المقرر</th><th>$identifierLabel</th><th>السنة/الفصل</th><th>الساعات</th><th>قبل الجبر</th><th>بعد الجبر</th><th>المساعدة</th><th>النهائية</th><th>الحالة</th><th>الملاحظة</th></tr></thead>
          <tbody>$courseRows</tbody></table>
        """.trimIndent()
        printHtml(context, "تقرير-${program.name}", document(body, landscape = true), landscape = true)
    }

    fun printHighSchoolReport(context: Context, branchTitle: String, grades: List<HighSchoolGradeEntity>) {
        val summary = HighSchoolCalculator.calculate(grades)
        val rows = grades.sortedBy { it.displayOrder }.mapIndexed { index, item ->
            val percentage = HighSchoolCalculator.subjectPercentage(item)
            """
            <tr><td>${index + 1}</td><td>${escape(item.subject)}</td><td>${item.grade ?: "—"}</td>
              <td>${item.maxGrade}</td><td>${percentage?.let { "${formatGrade(it)}%" } ?: "—"}</td>
              <td>${if (item.includedInPercentage) "نعم" else "لا"}</td></tr>
            """.trimIndent()
        }.joinToString("\n")
        val excluded = grades.filterNot { it.includedInPercentage }.joinToString("، ") { it.subject }
            .ifBlank { "لا توجد مواد مستبعدة" }
        val body = """
          <h1>تقرير ${escape(branchTitle)}</h1><div class="note">الشهادة الثانوية العامة</div>
          <section class="summary">
            <div><b>المجموع:</b> ${summary.totalGrade} / ${summary.maximumGrade}</div>
            <div><b>النسبة:</b> ${formatGrade(summary.percentage)}%</div>
            <div><b>الدرجات المدخلة:</b> ${grades.count { it.grade != null }} / ${grades.size}</div>
            <div><b>المواد غير المحتسبة:</b> ${escape(excluded)}</div>
          </section>
          <table><thead><tr><th>#</th><th>المادة</th><th>الدرجة</th><th>العظمى</th><th>نسبة المادة</th><th>محتسبة</th></tr></thead>
          <tbody>$rows</tbody></table>
        """.trimIndent()
        printHtml(context, "تقرير-$branchTitle", document(body, landscape = false), landscape = false)
    }

    private fun printHtml(context: Context, jobName: String, html: String, landscape: Boolean) {
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            private var started = false
            override fun onPageFinished(view: WebView, url: String?) {
                if (started) return
                started = true
                view.post {
                    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val media = if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape()
                    else PrintAttributes.MediaSize.ISO_A4.asPortrait()
                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(media).setColorMode(PrintAttributes.COLOR_MODE_COLOR).build()
                    manager.print(jobName, view.createPrintDocumentAdapter(jobName), attributes)
                }
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun document(body: String, landscape: Boolean): String {
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale("ar")).format(Date())
        val page = if (landscape) "A4 landscape" else "A4 portrait"
        return """
        <!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><style>
          @page { size: $page; margin: 11mm; }
          * { box-sizing: border-box; }
          body { font-family: sans-serif; color: #172235; line-height: 1.45; font-size: 10px; }
          header { direction: ltr; display: grid; grid-template-columns: 76px 1fr; align-items: center;
                   gap: 14px; border-bottom: 2px solid #145d70; padding-bottom: 8px; margin-bottom: 10px; }
          header img { width: 70px; height: 70px; object-fit: contain; justify-self: start; }
          .brand { direction: rtl; text-align: right; } .university { font-size: 15px; font-weight: bold; }
          h1 { color: #145d70; font-size: 21px; margin: 2px 0; } h2 { color: #145d70; font-size: 15px; margin: 13px 0 5px; }
          .summary { display: grid; grid-template-columns: repeat(4,1fr); gap: 6px; padding: 9px;
                     background: #eef7f8; border-radius: 8px; margin-bottom: 7px; }
          .standing,.notice,.note { padding: 7px 9px; margin: 6px 0; border-radius: 6px; }
          .standing { background: #f1f5f9; } .notice { background: #fff7db; border-right: 4px solid #d89c00; }
          .note { background: #eef7f8; color: #31505a; }
          table { width: 100%; border-collapse: collapse; } thead { display: table-header-group; }
          tr { page-break-inside: avoid; } th,td { border: 1px solid #ccd6dd; padding: 5px; text-align: right; vertical-align: top; }
          th { background: #dceff2; color: #123f4a; } tr:nth-child(even) { background: #f7f9fa; }
          .courses { table-layout: fixed; font-size: 8px; }
          .courses th:nth-child(1),.courses td:nth-child(1) { width:3%; }
          .courses th:nth-child(2),.courses td:nth-child(2) { width:17%; }
          .courses th:nth-child(3),.courses td:nth-child(3) { width:8%; direction:ltr; text-align:center; }
          .courses th:nth-child(4),.courses td:nth-child(4) { width:7%; }
          .courses th:nth-child(5),.courses td:nth-child(5) { width:5%; }
          .courses th:nth-child(6),.courses td:nth-child(6),.courses th:nth-child(7),.courses td:nth-child(7),
          .courses th:nth-child(8),.courses td:nth-child(8),.courses th:nth-child(9),.courses td:nth-child(9) { width:7%; }
          .courses th:nth-child(10),.courses td:nth-child(10) { width:9%; }
          .name,.grade { font-weight:bold; } .passed { color:#146c38; font-weight:bold; }
          .failed { color:#b3261e; font-weight:bold; } .assist { color:#6b4f00; background:#fff1bd; font-weight:bold; }
          footer { margin-top:12px; color:#6d7784; font-size:9px; text-align:center; }
        </style></head><body>$body<footer>مسيرتي الأكاديمية • تاريخ التقرير: $date</footer></body></html>
        """.trimIndent()
    }

    private fun universityLogoData(context: Context, name: String): String {
        val (resource, mime) = when {
            name.contains("الافتراضية") -> R.drawable.logo_svu to "image/png"
            name.contains("اللاذقية") -> R.drawable.logo_latakia to "image/png"
            name.contains("دمشق") -> R.drawable.logo_damascus to "image/jpeg"
            name.contains("الأندلس") -> R.drawable.logo_andalus to "image/png"
            else -> R.drawable.logo_svu to "image/png"
        }
        val bytes = context.resources.openRawResource(resource).use { it.readBytes() }
        return "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private fun formatGrade(value: Double): String = String.format(Locale.US, "%.2f", value)
    private fun yearName(year: Int): String = when (year) {
        1 -> "الأولى"; 2 -> "الثانية"; 3 -> "الثالثة"; 4 -> "الرابعة"; 5 -> "الخامسة"; else -> year.toString()
    }
}
