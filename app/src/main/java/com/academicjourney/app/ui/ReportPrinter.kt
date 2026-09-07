package com.academicjourney.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import com.academicjourney.app.BuildConfig
import com.academicjourney.app.R
import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.HighSchoolGradeEntity
import com.academicjourney.app.data.ProgramEntity
import com.academicjourney.app.domain.GradeCalculator
import com.academicjourney.app.domain.HighSchoolCalculator
import com.academicjourney.app.domain.StudentStandingCalculator
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

object ReportPrinter {
    private val retainedPrintViews = ArrayDeque<WebView>()

    fun printProgramReport(
        context: Context,
        universityName: String,
        program: ProgramEntity,
        courses: List<CourseEntity>,
        onResult: (String) -> Unit = {}
    ) {
        runCatching { programDocument(context, universityName, program, courses) }.fold(
            onSuccess = { html ->
                printHtml(context, "تقرير-${program.name}", html, landscape = true, onResult = onResult)
            },
            onFailure = { onResult("تعذر تجهيز التقرير للطباعة: ${it.message ?: "خطأ غير معروف"}") }
        )
    }

    fun exportProgramReport(
        context: Context,
        uri: Uri,
        universityName: String,
        program: ProgramEntity,
        courses: List<CourseEntity>,
        onResult: (String) -> Unit
    ) {
        runCatching { programDocument(context, universityName, program, courses) }.fold(
            onSuccess = { html ->
                exportHtml(context, uri, html, landscape = true, onResult = onResult)
            },
            onFailure = { onResult("تعذر تجهيز تقرير PDF: ${it.message ?: "خطأ غير معروف"}") }
        )
    }

    private fun programDocument(
        context: Context,
        universityName: String,
        program: ProgramEntity,
        courses: List<CourseEntity>
    ): String {
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
              <td>${gradeComponents(course, program)}</td>
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
            <div class="appIdentity">مسيرتي الأكاديمية • الإصدار ${escape(BuildConfig.VERSION_NAME)}</div>
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
          <table class="courses"><thead><tr><th>#</th><th>اسم المقرر</th><th>$identifierLabel</th><th>السنة/الفصل</th><th>الساعات</th><th>الدرجات المدخلة</th><th>قبل الجبر</th><th>بعد الجبر</th><th>المساعدة</th><th>النهائية</th><th>الحالة</th><th>الملاحظة</th></tr></thead>
          <tbody>$courseRows</tbody></table>
        """.trimIndent()
        return document(body, landscape = true)
    }

    fun printHighSchoolReport(
        context: Context,
        branchTitle: String,
        grades: List<HighSchoolGradeEntity>,
        onResult: (String) -> Unit = {}
    ) {
        runCatching { highSchoolDocument(context, branchTitle, grades) }.fold(
            onSuccess = { html ->
                printHtml(context, "تقرير-$branchTitle", html, landscape = false, onResult = onResult)
            },
            onFailure = { onResult("تعذر تجهيز التقرير للطباعة: ${it.message ?: "خطأ غير معروف"}") }
        )
    }

    fun exportHighSchoolReport(
        context: Context,
        uri: Uri,
        branchTitle: String,
        grades: List<HighSchoolGradeEntity>,
        onResult: (String) -> Unit
    ) {
        runCatching { highSchoolDocument(context, branchTitle, grades) }.fold(
            onSuccess = { html ->
                exportHtml(context, uri, html, landscape = false, onResult = onResult)
            },
            onFailure = { onResult("تعذر تجهيز تقرير PDF: ${it.message ?: "خطأ غير معروف"}") }
        )
    }

    private fun highSchoolDocument(
        context: Context,
        branchTitle: String,
        grades: List<HighSchoolGradeEntity>
    ): String {
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
          <header><img src="${appLogoData(context)}"><div class="brand">
            <div class="appIdentity">مسيرتي الأكاديمية • الإصدار ${escape(BuildConfig.VERSION_NAME)}</div>
            <div class="university">الشهادة الثانوية العامة</div>
            <h1>تقرير ${escape(branchTitle)}</h1><div>تقرير الدرجات والنسب التفصيلية</div>
          </div></header>
          <section class="summary">
            <div><b>المجموع:</b> ${summary.totalGrade} / ${summary.maximumGrade}</div>
            <div><b>النسبة:</b> ${formatGrade(summary.percentage)}%</div>
            <div><b>الدرجات المدخلة:</b> ${grades.count { it.grade != null }} / ${grades.size}</div>
            <div><b>المواد غير المحتسبة:</b> ${escape(excluded)}</div>
          </section>
          <table><thead><tr><th>#</th><th>المادة</th><th>الدرجة</th><th>العظمى</th><th>نسبة المادة</th><th>محتسبة</th></tr></thead>
          <tbody>$rows</tbody></table>
        """.trimIndent()
        return document(body, landscape = false)
    }

    private fun printHtml(
        context: Context,
        jobName: String,
        html: String,
        landscape: Boolean,
        onResult: (String) -> Unit
    ) {
        loadHtml(
            context = context,
            html = html,
            onReady = { view ->
                runCatching {
                    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    manager.print(
                        jobName,
                        view.createPrintDocumentAdapter(jobName),
                        printAttributes(landscape)
                    )
                }.fold(
                    onSuccess = { onResult("تم فتح نافذة الطباعة؛ اختر الطابعة ثم أكّد الطباعة.") },
                    onFailure = {
                        releaseWebView(view)
                        onResult("تعذر فتح نافذة الطباعة: ${it.message ?: "خطأ غير معروف"}")
                    }
                )
            },
            onFailure = { onResult("تعذر تجهيز التقرير للطباعة: ${it.message ?: "خطأ غير معروف"}") }
        )
    }

    private fun exportHtml(
        context: Context,
        uri: Uri,
        html: String,
        landscape: Boolean,
        onResult: (String) -> Unit
    ) {
        loadHtml(
            context = context,
            html = html,
            onReady = { view ->
                runCatching {
                    writeWebViewToPdf(
                        context = context,
                        uri = uri,
                        view = view,
                        attributes = printAttributes(landscape)
                    )
                }.fold(
                    onSuccess = { onResult("تم تصدير تقرير PDF بنجاح.") },
                    onFailure = { onResult("تعذر تصدير PDF: ${it.message ?: "خطأ غير معروف"}") }
                )
                releaseWebView(view)
            },
            onFailure = { onResult("تعذر تجهيز تقرير PDF: ${it.message ?: "خطأ غير معروف"}") }
        )
    }

    private fun writeWebViewToPdf(
        context: Context,
        uri: Uri,
        view: WebView,
        attributes: PrintAttributes
    ) {
        val document = PrintedPdfDocument(context, attributes)
        try {
            val contentRect = document.pageContentRect
            val pageWidth = contentRect.width().coerceAtLeast(1)
            val pageHeight = contentRect.height().coerceAtLeast(1)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(pageWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val contentHeight = view.measuredHeight.coerceAtLeast(pageHeight)
            view.layout(0, 0, pageWidth, contentHeight)
            val pageCount = ceil(contentHeight.toDouble() / pageHeight).toInt().coerceAtLeast(1)

            repeat(pageCount) { pageIndex ->
                val page = document.startPage(pageIndex)
                page.canvas.drawColor(Color.WHITE)
                page.canvas.save()
                page.canvas.translate(
                    contentRect.left.toFloat(),
                    (contentRect.top - (pageIndex * pageHeight)).toFloat()
                )
                view.draw(page.canvas)
                page.canvas.restore()
                document.finishPage(page)
            }

            val output = checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
                "تعذر فتح الملف المحدد للكتابة."
            }
            output.use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private fun loadHtml(
        context: Context,
        html: String,
        onReady: (WebView) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        var createdView: WebView? = null
        runCatching {
            val webView = WebView(context)
            createdView = webView
            retainWebView(webView)
            webView.settings.javaScriptEnabled = false
            webView.settings.defaultTextEncodingName = "UTF-8"
            webView.webViewClient = object : WebViewClient() {
                private var ready = false

                override fun onPageFinished(view: WebView, url: String?) {
                    if (ready) return
                    ready = true
                    view.postDelayed({ onReady(view) }, 200L)
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }.onFailure {
            createdView?.let(::releaseWebView)
            onFailure(it)
        }
    }

    private fun printAttributes(landscape: Boolean): PrintAttributes {
        val media = if (landscape) {
            PrintAttributes.MediaSize.ISO_A4.asLandscape()
        } else {
            PrintAttributes.MediaSize.ISO_A4.asPortrait()
        }
        return PrintAttributes.Builder()
            .setMediaSize(media)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
    }

    private fun retainWebView(view: WebView) {
        retainedPrintViews.addLast(view)
        while (retainedPrintViews.size > 4) {
            retainedPrintViews.removeFirst().destroy()
        }
    }

    private fun releaseWebView(view: WebView) {
        retainedPrintViews.remove(view)
        view.stopLoading()
        view.destroy()
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
          .brand { direction: rtl; text-align: right; } .appIdentity { color:#49636b; font-size:9px; }
          .university { font-size: 15px; font-weight: bold; }
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
          .courses th:nth-child(2),.courses td:nth-child(2) { width:15%; }
          .courses th:nth-child(3),.courses td:nth-child(3) { width:6%; direction:ltr; text-align:center; }
          .courses th:nth-child(4),.courses td:nth-child(4) { width:6%; }
          .courses th:nth-child(5),.courses td:nth-child(5) { width:4%; }
          .courses th:nth-child(6),.courses td:nth-child(6) { width:14%; }
          .courses th:nth-child(7),.courses td:nth-child(7),.courses th:nth-child(8),.courses td:nth-child(8),
          .courses th:nth-child(9),.courses td:nth-child(9),.courses th:nth-child(10),.courses td:nth-child(10) { width:6%; }
          .courses th:nth-child(11),.courses td:nth-child(11) { width:8%; }
          .name,.grade { font-weight:bold; } .passed { color:#146c38; font-weight:bold; }
          .failed { color:#b3261e; font-weight:bold; } .assist { color:#6b4f00; background:#fff1bd; font-weight:bold; }
          footer { margin-top:12px; color:#6d7784; font-size:9px; text-align:center; }
        </style></head><body>$body<footer>مسيرتي الأكاديمية • الإصدار ${escape(BuildConfig.VERSION_NAME)} • تاريخ التقرير: $date</footer></body></html>
        """.trimIndent()
    }

    private fun appLogoData(context: Context): String {
        val bitmap = checkNotNull(
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground_image)
        ) { "تعذر تحميل شعار التطبيق." }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        return "data:image/png;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
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

    private fun gradeComponents(course: CourseEntity, program: ProgramEntity): String {
        val components = when (program.gradingScheme) {
            GradeCalculator.SVU_WEIGHTED -> listOf(
                "الوظيفة" to course.assignmentGrade,
                "الامتحان" to course.examGrade
            )
            GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY -> listOf(
                "أعمال الطالب" to (course.studentWorkGrade ?: course.practicalGrade),
                "العملي" to (course.practicalExamGrade ?: if (course.practicalGrade != null) 0.0 else null),
                "النظري" to course.theoryGrade
            )
            else -> listOf(
                "العملي" to course.practicalGrade,
                "النظري" to course.theoryGrade
            )
        }
        val entered = components.mapNotNull { (label, value) ->
            value?.let { "${escape(label)}: <b>${formatGrade(it)}</b>" }
        }
        return entered.joinToString("<br>").ifBlank { "—" }
    }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    private fun formatGrade(value: Double): String = String.format(Locale.US, "%.2f", value)
    private fun yearName(year: Int): String = when (year) {
        1 -> "الأولى"; 2 -> "الثانية"; 3 -> "الثالثة"; 4 -> "الرابعة"; 5 -> "الخامسة"; else -> year.toString()
    }
}
