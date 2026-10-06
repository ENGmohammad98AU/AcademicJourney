package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity
import java.util.Locale

object GradeExplanation {
    fun number(value: Double?) = value?.let {
        if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.2f", it)
    } ?: "—"

    fun course(c: CourseEntity, p: ProgramEntity): List<String> {
        val r = GradeCalculator.calculate(c, p)
        if (r.passedWithoutGrade) return listOf("ناجح دون علامة. يُحتسب في التقدم والساعات، ولا يدخل في المعدل.", c.notes).filter { it.isNotBlank() }
        val partial = PartialGradePreviewBuilder.forCourse(c, p)
        if (partial != null) return partial.entered.map { "${it.label}: ${number(it.value)}" } +
            listOf(partial.missingNotice, "تُحفظ القيم المتاحة، ولا تُحسب نتيجة نهائية أو معدل لهذا المقرر حتى تكتمل درجاته.")
        if (r.finalGrade == null) return listOf("لم تُدخل درجة مكتملة وصالحة لهذا المقرر بعد.", "لا يدخل في المعدل حاليًا.")
        val formula = when {
            ProjectGradePolicy.usesSingleProjectGrade(c, p) -> "درجة المشروع المباشرة: ${number(r.rawGrade)}"
            p.gradingScheme == GradeCalculator.SINGLE_FINAL_GRADE -> "الدرجة النهائية المباشرة: ${number(c.directGrade)}"
            p.gradingScheme == GradeCalculator.SVU_WEIGHTED ->
                "الوظيفة ${number(c.assignmentGrade)} × ${number(p.assignmentWeight)}% + الامتحان ${number(c.examGrade)} × ${number(p.examWeight)}% = ${number(r.rawGrade)}"
            p.gradingScheme == GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY ->
                "أعمال الطالب/العملي ${number(c.studentWorkGrade ?: c.practicalGrade)} + الامتحان العملي ${number(c.practicalExamGrade ?: 0.0)} + النظري ${number(c.theoryGrade)} = ${number(r.rawGrade)}"
            else -> "العملي ${number(c.practicalGrade)} + النظري ${number(c.theoryGrade)} = ${number(r.rawGrade)}"
        }
        return listOf(formula,
            "بعد جبر الكسر للأعلى: ${number(r.roundedGrade)}.",
            "المساعدة المضافة: ${r.assistancePoints}. النتيجة المعتمدة: ${number(r.finalGrade)}.",
            "حد النجاح: ${number(p.passingGrade)}. تدخل الدرجة النهائية في المعدل.")
    }
    fun average(courses: List<CourseEntity>, p: ProgramEntity): String {
        val graded = courses.filter { GradeCalculator.calculate(it, p).finalGrade != null }
        val weighted = graded.isNotEmpty() && graded.all { (it.creditHours ?: 0) > 0 }
        return if (graded.isEmpty()) "لا توجد درجات مكتملة لحساب المعدل. النجاح دون علامة والدرجات الناقصة مستبعدة."
        else "${graded.size} مقررًا يدخل في المعدل. " +
            if (weighted) "المعدل = مجموع (الدرجة النهائية × الساعات) ÷ مجموع الساعات (${graded.sumOf { it.creditHours ?: 0 }}). النجاح دون علامة والدرجات الناقصة مستبعدة."
            else "المعدل = مجموع الدرجات النهائية ÷ عدد المقررات ذات الدرجات المكتملة. النجاح دون علامة والدرجات الناقصة مستبعدة."
    }
}
