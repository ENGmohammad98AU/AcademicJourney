package com.academicjourney.app.ui

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.academicjourney.app.BuildConfig
import com.academicjourney.app.R
import com.academicjourney.app.data.*
import com.academicjourney.app.domain.*
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Native PDF pages: no hidden WebView, arbitrary delay, or screenshot clipping. */
object AcademicReportPdf {
    fun program(context: Context, university: String, program: ProgramEntity, courses: List<CourseEntity>): ByteArray {
        require(courses.isNotEmpty() && courses.all { it.programId == program.id }) { "لا توجد مقررات لهذا التقرير." }
        val backup = AcademicBackupManager.encode(listOf(UniversityEntity(program.universityId, university)),
            listOf(program), courses, emptyList())
        val report = Pages(context, university, program.name, landscape = true)
        val pdf = report.use { out ->
            val standing = StudentStandingCalculator.calculate(university, program, courses)
            val results = courses.map { GradeCalculator.calculate(it, program) }
            out.section("الملخص الأكاديمي")
            out.paragraph("${standing.title}  •  المعدل العام: ${grade(GradeCalculator.average(courses, program))}  •  ناجح: ${results.count { it.isPassed == true }}  •  راسب: ${results.count { it.isPassed == false }}  •  المقررات: ${courses.size}")
            out.paragraph(standing.details)
            out.paragraph("حد النجاح: ${grade(program.passingGrade)} / 100. يُجبر أي كسر إلى العدد الصحيح الأعلى. النجاح دون علامة لا يدخل في المعدل.", small = true)
            if (GradeCalculator.maximumAssistance(program) > 0) out.paragraph("تظهر درجات المساعدة بجانب النتيجة، وتُمنح وفق قواعد البرنامج عندما توصل الدرجة إلى حد النجاح.", small = true)
            out.paragraph("يمكن استيراد هذا PDF من «المزيد ← استيراد PDF أو JSON». يحتوي بيانات درجات هذا الفرع وملاحظاته فقط، ولا يغني عن النسخة الاحتياطية الشاملة للمواعيد والسجل والأرشيف.", small = true, tinted = true)
            out.section("المعدلات الفصلية والسنوية")
            out.tableHeader(listOf("السنة", "الفصل الأول", "الفصل الثاني", "المعدل السنوي"), listOf(.25f, .25f, .25f, .25f))
            courses.map { it.academicYear }.distinct().sorted().forEach { year ->
                val selected = courses.filter { it.academicYear == year }
                out.row(listOf(year.toString(), grade(GradeCalculator.average(selected.filter { it.semester == 1 }, program)),
                    grade(GradeCalculator.average(selected.filter { it.semester == 2 }, program)), grade(GradeCalculator.average(selected, program))))
            }
            if (courses.any { it.creditHours != null }) out.paragraph("المعدل موزون بالساعات المعتمدة للمقررات ذات العلامات المكتملة.", small = true)
            courses.groupBy { it.academicYear to it.semester }.toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second }).forEach { (term, selected) ->
                out.section("السنة ${term.first} • الفصل ${if (term.second == 1) "الأول" else "الثاني"}")
                out.tableHeader(listOf("المقرر / الرمز أو الرقم", "الدرجات المدخلة", "قبل الجبر / بعده", "النهائية / الحالة"), listOf(.31f, .32f, .17f, .20f))
                selected.sortedBy { it.name }.forEach { course ->
                    val result = GradeCalculator.calculate(course, program)
                    val status = when {
                        result.passedWithoutGrade -> "ناجح دون علامة"
                        result.isPassed == true -> "ناجح"
                        result.isPassed == false -> "راسب"
                        else -> "غير مكتملة"
                    }
                    val state = "${grade(result.finalGrade)}\n$status" + if (result.assistancePoints > 0) "\n+${result.assistancePoints} مساعدة" else ""
                    val name = course.name + (if (course.code.isNotBlank()) "\n${course.code}" else "") + (course.creditHours?.let { " • $it ساعة" } ?: "")
                    out.row(listOf(name, components(course, program), "${grade(result.rawGrade)} / ${grade(result.roundedGrade)}", state),
                        stateColor = when (result.isPassed) { true -> Color.rgb(20, 108, 56); false -> Color.rgb(179, 38, 30); else -> null })
                    if (course.notes.isNotBlank()) out.paragraph("ملاحظة ${course.name}: ${course.notes}", small = true)
                }
            }
            out.bytes()
        }
        return PdfRestoreData.attach(context, pdf, backup)
    }

    fun highSchool(context: Context, branchTitle: String, grades: List<HighSchoolGradeEntity>): ByteArray {
        require(grades.isNotEmpty() && grades.map { it.branch }.distinct().size == 1) { "اختر فرع الثانوية أولًا." }
        val backup = AcademicBackupManager.encode(emptyList(), emptyList(), emptyList(), grades)
        val pdf = Pages(context, "الشهادة الثانوية العامة", branchTitle, landscape = false).use { out ->
            val summary = HighSchoolCalculator.calculate(grades)
            out.section("ملخص الدرجات")
            out.paragraph("المجموع: ${summary.totalGrade} / ${summary.maximumGrade} • النسبة: ${grade(summary.percentage)}%")
            out.paragraph("يمكن استعادة علامات هذا الفرع من ملف PDF عبر «المزيد ← استيراد PDF أو JSON».", small = true, tinted = true)
            out.tableHeader(listOf("المادة", "الدرجة / العظمى", "النسبة", "محتسبة"), listOf(.38f, .25f, .19f, .18f))
            grades.sortedBy { it.displayOrder }.forEach { item ->
                out.row(listOf(item.subject, "${item.grade ?: "—"} / ${item.maxGrade}",
                    HighSchoolCalculator.subjectPercentage(item)?.let { "${grade(it)}%" } ?: "—", if (item.includedInPercentage) "نعم" else "لا"))
            }
            out.bytes()
        }
        return PdfRestoreData.attach(context, pdf, backup)
    }

    private fun grade(value: Double?): String = value?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
    private fun components(c: CourseEntity, p: ProgramEntity): String {
        if (c.passedWithoutGrade) return "ناجح دون علامة"
        val fields = when {
            ProjectGradePolicy.usesSingleProjectGrade(c, p) -> listOf("درجة المشروع" to ProjectGradePolicy.displayedGrade(c, p))
            p.gradingScheme == GradeCalculator.SVU_WEIGHTED -> listOf("الوظيفة" to c.assignmentGrade, "الامتحان" to c.examGrade)
            p.gradingScheme == GradeCalculator.SINGLE_FINAL_GRADE -> if (c.directGrade == null && DiplomacyCurriculum.isProgramme(p.name))
                listOf("العملي المحفوظ سابقًا" to c.practicalGrade, "النظري المحفوظ سابقًا" to c.theoryGrade) else listOf("الدرجة النهائية" to c.directGrade)
            p.gradingScheme == GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY -> listOf(
                "أعمال الطالب" to (c.studentWorkGrade ?: c.practicalGrade),
                "العملي" to (c.practicalExamGrade ?: if (c.practicalGrade != null) 0.0 else null), "النظري" to c.theoryGrade)
            else -> listOf("العملي" to c.practicalGrade, "النظري" to c.theoryGrade)
        }
        val entered = fields.filter { it.second != null }.joinToString("\n") { "${it.first}: ${grade(it.second)}" }.ifBlank { "لم تُدخل درجات بعد" }
        return entered + (PartialGradePreviewBuilder.forCourse(c, p)?.let { "\n${it.missingNotice}" } ?: "")
    }

    private class Pages(context: Context, private val university: String, private val title: String, landscape: Boolean) : AutoCloseable {
        private val doc = PdfDocument()
        private val width = if (landscape) 842 else 595
        private val height = if (landscape) 595 else 842
        private val margin = 30f
        private val usable = width - margin * 2
        private val bottom = height - 38f
        private var page: PdfDocument.Page? = null
        private var count = 0
        private var y = 0f
        private var finished = false
        private val teal = Color.rgb(20, 93, 112)
        private val line = Paint().apply { color = Color.rgb(210, 224, 229); strokeWidth = .6f }
        private val fill = Paint()
        private var headers = emptyList<String>()
        private var widths = emptyList<Float>()
        private val logo = BitmapFactory.decodeResource(context.resources, when {
            university.contains("الافتراضية") -> R.drawable.logo_svu
            university.contains("اللاذقية") -> R.drawable.logo_latakia
            university.contains("دمشق") -> R.drawable.logo_damascus
            university.contains("الأندلس") -> R.drawable.logo_andalus
            else -> R.drawable.ic_launcher_foreground_image
        })
        private val date = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
        init { newPage(false) }

        private fun textLayout(text: String, width: Float, size: Float = 10f, bold: Boolean = false, color: Int = Color.rgb(23, 34, 53)): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
                .setTextDirection(TextDirectionHeuristics.RTL).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(3f, 1f).build()
        }
        private fun draw(layout: StaticLayout, x: Float, top: Float) {
            val canvas = page!!.canvas
            canvas.save(); canvas.translate(x, top); layout.draw(canvas); canvas.restore()
        }
        private fun finishPage() {
            page?.let { current ->
                draw(textLayout("مسيرتي الأكاديمية • ${BuildConfig.VERSION_NAME} • $date • صفحة $count", usable, 8f), margin, height - 23f)
                doc.finishPage(current)
            }
            page = null
        }
        private fun newPage(repeatHeader: Boolean = true) {
            finishPage()
            count++
            require(count <= 300) { "التقرير يتجاوز 300 صفحة. اختصر الملاحظات الطويلة جدًا ثم أعد التصدير." }
            page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, count).create())
            page!!.canvas.drawColor(Color.WHITE)
            logo?.let { image ->
                val scale = minOf(55f / image.width, 55f / image.height)
                val w = image.width * scale; val h = image.height * scale
                page!!.canvas.drawBitmap(image, null, RectF(margin, margin, margin + w, margin + h), Paint(Paint.ANTI_ALIAS_FLAG))
            }
            draw(textLayout("مسيرتي الأكاديمية", usable - 75f, 10f, color = teal), margin + 75f, margin)
            draw(textLayout(university, usable - 75f, 14f, true, teal), margin + 75f, margin + 17f)
            val name = textLayout(title, usable - 75f, 16f, true)
            draw(name, margin + 75f, margin + 40f)
            y = margin + 45f + max(22, name.height)
            page!!.canvas.drawLine(margin, y, width - margin, y, line)
            y += 12f
            if (repeatHeader && headers.isNotEmpty()) row(headers, header = true)
        }
        fun section(text: String) {
            headers = emptyList()
            if (bottom - y < 105f) newPage(false)
            val layout = textLayout(text, usable, 13f, true, teal)
            draw(layout, margin, y + 6f); y += layout.height + 16f
        }
        fun paragraph(text: String, small: Boolean = false, tinted: Boolean = false) {
            val layout = textLayout(text, usable - 16f, if (small) 9f else 11f)
            var start = 0
            while (start < layout.lineCount) {
                if (bottom - y < 40f) newPage()
                val top = layout.getLineTop(start)
                var end = start + 1
                while (end < layout.lineCount && layout.getLineBottom(end) - top + 16 <= bottom - y) end++
                val used = layout.getLineBottom(end - 1) - top + 16f
                if (tinted) { fill.color = Color.rgb(235, 247, 249); page!!.canvas.drawRect(margin, y, width - margin, y + used, fill) }
                page!!.canvas.save()
                page!!.canvas.clipRect(margin, y + 8f, width - margin, y + used - 8f)
                draw(layout, margin + 8f, y + 8f - top)
                page!!.canvas.restore()
                y += used + 3f
                start = end
            }
        }
        fun tableHeader(labels: List<String>, proportions: List<Float>) {
            headers = labels; widths = proportions.map { usable * it }
            if (bottom - y < 70f) newPage(false)
            row(headers, header = true)
        }
        fun row(values: List<String>, header: Boolean = false, stateColor: Int? = null) {
            val layouts = values.mapIndexed { i, value -> textLayout(value, widths[i] - 14f, 10f, header || i == values.lastIndex,
                if (header) teal else if (i == values.lastIndex && stateColor != null) stateColor else Color.rgb(23, 34, 53)) }
            val totalHeight = layouts.maxOf { it.height } + 16f
            if (!header && totalHeight <= bottom - 150 && y + totalHeight > bottom) newPage()
            val starts = IntArray(layouts.size)
            while (layouts.indices.any { starts[it] < layouts[it].lineCount }) {
                if (bottom - y < 36f) newPage()
                val ends = layouts.mapIndexed { i, layout ->
                    if (starts[i] >= layout.lineCount) starts[i] else {
                        val top = layout.getLineTop(starts[i]); var end = starts[i] + 1
                        while (end < layout.lineCount && layout.getLineBottom(end) - top + 16f <= bottom - y) end++
                        end
                    }
                }
                val used = layouts.indices.maxOf { i ->
                    if (ends[i] == starts[i]) 0f else (layouts[i].getLineBottom(ends[i] - 1) - layouts[i].getLineTop(starts[i])).toFloat()
                } + 16f
                var right = width - margin
                layouts.forEachIndexed { i, layout ->
                    val left = right - widths[i]
                    fill.color = if (header) Color.rgb(220, 239, 242) else Color.rgb(248, 251, 252)
                    page!!.canvas.drawRect(left, y, right, y + used, fill)
                    page!!.canvas.drawLine(left, y, left, y + used, line)
                    if (ends[i] > starts[i]) {
                        page!!.canvas.save(); page!!.canvas.clipRect(left, y + 8f, right, y + used - 8f)
                        draw(layout, left + 7f, y + 8f - layout.getLineTop(starts[i])); page!!.canvas.restore()
                    }
                    starts[i] = ends[i]; right = left
                }
                y += used
                page!!.canvas.drawLine(margin, y, width - margin, y, line)
            }
        }
        fun bytes(): ByteArray {
            finishPage(); finished = true
            return ByteArrayOutputStream().use { out -> doc.writeTo(out); out.toByteArray() }
        }
        override fun close() { if (!finished) finishPage(); doc.close(); logo?.recycle() }
    }
}
