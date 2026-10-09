package com.academicjourney.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.academicjourney.app.domain.GradeCalculator
import com.academicjourney.app.ui.AcademicReportPdf
import com.academicjourney.app.ui.RestorablePdfPrintAdapter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfImportTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val university = UniversityEntity(1, "جامعة دمشق")
    private val program = ProgramEntity(2, 1, "الدراسات الدولية والدبلوماسية – التعليم المفتوح",
        gradingScheme = GradeCalculator.SINGLE_FINAL_GRADE, passingGrade = 50.0)
    private val course = CourseEntity(3, 2, "مقدمة في العلاقات الدولية", "510", academicYear = 1, semester = 1,
        directGrade = 76.1, notes = "ملاحظة عربية محفوظة\nسطر ثانٍ", isCurrentSemester = true)

    private fun bytes(doc: PDDocument) = ByteArrayOutputStream().use { out -> doc.save(out); out.toByteArray() }
    private fun barePdf(): ByteArray {
        PDFBoxResourceLoader.init(context)
        return PDDocument().use { doc -> doc.addPage(PDPage()); bytes(doc) }
    }
    private fun backup(courses: List<CourseEntity> = listOf(course)) = AcademicBackupManager.encode(listOf(university), listOf(program), courses, emptyList())
    private fun plan(pdf: ByteArray, existing: List<CourseEntity>) = AcademicBackupManager.plan(
        AcademicImportFile.decode(context, pdf).json, listOf(university), listOf(program), existing, emptyList())
    private fun failsContaining(text: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull() ?: error("Expected an actionable rejection")
        assertTrue(error.message, error.message.orEmpty().contains(text))
    }

    @Test fun realProgramPdfOpensAndRestoresExactRawGradesNotesAndNullsWithoutChangingOtherProgrammes() {
        val incomplete = course.copy(id = 4, code = "511", name = "النظم السياسية", directGrade = null,
            practicalGrade = 20.0, notes = "لم يُدخل النظري بعد")
        val zero = course.copy(id = 5, code = "512", name = "القانون الدولي", directGrade = 0.0)
        val transferred = course.copy(id = 6, code = "513", name = "مقرر معادل", directGrade = null, passedWithoutGrade = true, creditHours = 4)
        val source = listOf(course, incomplete, zero, transferred)
        val pdf = AcademicReportPdf.program(context, university.name, program, source)
        val unrelated = course.copy(id = 40, programId = 20, name = "برنامج آخر", directGrade = 88.0)
        val current = source.map { it.copy(directGrade = null, practicalGrade = null, notes = "", isCurrentSemester = false) } + unrelated
        val restored = plan(pdf, current)
        assertEquals(source, restored.courses)
        assertEquals(current, restored.originalCourses)
        assertFalse(restored.courses.any { it.id == unrelated.id })
        assertEquals(77.0, GradeCalculator.calculate(restored.courses.first(), program).finalGrade!!, .001)
        assertNull(restored.courses[1].directGrade)
        assertEquals(20.0, restored.courses[1].practicalGrade!!, .001)
        assertEquals(0.0, restored.courses[2].directGrade!!, .001)
        assertTrue(restored.courses[3].passedWithoutGrade)
        assertTrue(restored.events.isEmpty() && restored.history.isEmpty() && restored.archives.isEmpty())
        val file = File("build/reports/pdf/diplomacy-restorable.pdf").apply { parentFile!!.mkdirs(); writeBytes(pdf) }
        assertRenderable(file, "diplomacy-preview.png")
        // Some file managers report PDFs as binary or JSON; content controls detection.
        val renamed = File(context.cacheDir, "renamed.json").apply { writeBytes(pdf) }
        assertTrue(AcademicBackupManager.readAndPlan(context, Uri.fromFile(renamed), listOf(university), listOf(program), current, emptyList())
            .sourceDescription.startsWith("تقرير PDF"))
    }

    @Test fun highSchoolPdfRestoresScoresAbove100AndExcludedSubjectsAndOnlyItsBranch() {
        val grades = listOf(HighSchoolGradeEntity(1, "LITERARY_2026", "اللغة العربية", 600, true, 1, 439),
            HighSchoolGradeEntity(2, "LITERARY_2026", "اللغة الفرنسية", 400, false, 2, 305),
            HighSchoolGradeEntity(3, "LITERARY_2026", "التاريخ", 300, true, 3, null))
        val other = grades.first().copy(id = 8, branch = "SCIENTIFIC_2016", grade = 500)
        val pdf = AcademicReportPdf.highSchool(context, "الفرع الأدبي 2026", grades)
        val result = AcademicBackupManager.plan(AcademicImportFile.decode(context, pdf).json, emptyList(), emptyList(), emptyList(), grades + other)
        assertEquals(grades, result.highSchoolGrades)
        assertEquals(3, result.restoredHighSchoolCount)
        assertTrue(result.courses.isEmpty())
        val file = File("build/reports/pdf/highschool-restorable.pdf").apply { parentFile!!.mkdirs(); writeBytes(pdf) }
        assertRenderable(file, "highschool-preview.png")
    }

    @Test fun allGradingSchemesSurvivePdfRoundTripAndMultiPagePrintPreservesRestoreAttachment() {
        val schemes = listOf(GradeCalculator.SVU_WEIGHTED, GradeCalculator.PRACTICAL_THEORY,
            GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY, GradeCalculator.SINGLE_FINAL_GRADE)
        schemes.forEach { scheme ->
            val p = program.copy(name = "برنامج اختبار", gradingScheme = scheme, assignmentWeight = 25.0, examWeight = 75.0)
            val c = course.copy(assignmentGrade = 70.25, examGrade = 82.5, practicalGrade = 20.0, theoryGrade = 56.1,
                studentWorkGrade = 10.0, practicalExamGrade = 10.0)
            val pdf = AcademicReportPdf.program(context, university.name, p, listOf(c))
            val read = AcademicBackupManager.plan(AcademicImportFile.decode(context, pdf).json,
                listOf(university), listOf(p), listOf(c), emptyList())
            assertEquals(c, read.courses.single())
        }
        val many = (0 until 48).map { n -> course.copy(id = n + 10L, code = (510 + n).toString(), name = "مقرر رقم $n",
            academicYear = n / 12 + 1, semester = (n / 6) % 2 + 1,
            notes = if (n == 0) "ملاحظة طويلة للاختبار ".repeat(400) else course.notes) }
        val pdf = AcademicReportPdf.program(context, university.name, program, many)
        val count = PDDocument.load(pdf).use { it.numberOfPages }
        assertTrue(count > 3)
        val subset = RestorablePdfPrintAdapter.selectPages(pdf, listOf(0, count - 1), count)
        assertEquals(2, PDDocument.load(subset).use { it.numberOfPages })
        assertEquals(many, plan(subset, many).courses)
        val file = File("build/reports/pdf/multipage-restorable.pdf").apply { parentFile!!.mkdirs(); writeBytes(pdf) }
        assertRenderable(file, "multipage-preview.png", allPages = true)
    }

    @Test fun emptyDamagedAndLegacyFilesHaveSpecificErrorsInsteadOfInvalidBackup() {
        failsContaining("فارغ") { AcademicImportFile.decode(context, byteArrayOf()) }
        failsContaining("غير مكتمل أو تالف") { AcademicImportFile.decode(context, "%PDF-1.7\nbroken".toByteArray()) }
        failsContaining("لا يحتوي بيانات الاستعادة") { AcademicImportFile.decode(context, barePdf()) }
        failsContaining("نوع الملف غير مدعوم") { AcademicImportFile.decode(context, "<html>download error</html>".toByteArray()) }
        failsContaining("ناقص أو تالف") { AcademicImportFile.decode(context, "{\"formatId\":".toByteArray()) }
        failsContaining("الحد المسموح") { AcademicImportFile.readLimited(byteArrayOf(1, 2, 3, 4).inputStream(), 3) }
    }

    @Test fun damagedAttachmentAndOutOfRangeMarksAreRejectedBeforeProducingPlan() {
        val good = PdfRestoreData.attach(context, barePdf(), backup())
        val changed = PDDocument.load(good).use { doc ->
            val spec = doc.documentCatalog.names.embeddedFiles.names.getValue(PdfRestoreData.ATTACHMENT_NAME)
            val envelope = spec.embeddedFile.createInputStream().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
            envelope.put("payload", envelope.getString("payload").replace("76.1", "99.0"))
            val embedded = PDEmbeddedFile(doc, envelope.toString().byteInputStream())
            spec.embeddedFile = embedded; spec.embeddedFileUnicode = embedded
            bytes(doc)
        }
        failsContaining("فشل التحقق") { AcademicImportFile.decode(context, changed) }
        val invalid = PdfRestoreData.attach(context, barePdf(), backup(listOf(course.copy(directGrade = 101.0))))
        failsContaining("قيمة غير صالحة") { plan(invalid, listOf(course)) }
        val duplicates = PdfRestoreData.attach(context, barePdf(), backup(listOf(course, course)))
        failsContaining("مكرر") { plan(duplicates, listOf(course)) }
        val foreign = PdfRestoreData.attach(context, barePdf(), backup().put("formatId", "another.app"))
        failsContaining("لا تطابق") { plan(foreign, listOf(course)) }
    }

    @Test fun protectedPdfIsExplainedAndExistingJsonWithBomStillImports() {
        PDFBoxResourceLoader.init(context)
        val locked = PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.protect(StandardProtectionPolicy("owner-test", "reader-test", AccessPermission()))
            bytes(doc)
        }
        failsContaining("محمي") { AcademicImportFile.decode(context, locked) }
        val root = AcademicImportFile.decode(context, ("\uFEFF  " + backup()).toByteArray(Charsets.UTF_8))
        assertEquals(listOf(course), AcademicBackupManager.plan(root.json, listOf(university), listOf(program), listOf(course), emptyList()).courses)
    }

    private fun assertRenderable(file: File, previewName: String, allPages: Boolean = false) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue(renderer.pageCount > 0)
                val pages = if (allPages) 0 until renderer.pageCount else 0..0
                pages.forEach { n -> renderer.openPage(n).use { page ->
                    assertTrue(page.width > 0 && page.height > 0)
                    val image = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    if (n == 0) File(file.parentFile, previewName).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    image.recycle()
                } }
            }
        }
    }
}
