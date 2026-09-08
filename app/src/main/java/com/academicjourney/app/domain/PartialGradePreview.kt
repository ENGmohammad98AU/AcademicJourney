package com.academicjourney.app.domain

import com.academicjourney.app.data.CourseEntity
import com.academicjourney.app.data.ProgramEntity

data class EnteredGradeComponent(
    val label: String,
    val value: Double
)

data class PartialGradePreview(
    val entered: List<EnteredGradeComponent>,
    val missingLabels: List<String>
) {
    val missingNotice: String = missingLabels.joinToString("، ") { label ->
        "لم يتم إدخال $label بعد"
    } + "."
}

object PartialGradePreviewBuilder {
    fun build(fields: List<Pair<String, Double?>>): PartialGradePreview? {
        val entered = fields.mapNotNull { (label, value) ->
            value?.let { EnteredGradeComponent(label, it) }
        }
        val missing = fields.filter { (_, value) -> value == null }.map { (label, _) -> label }
        return if (entered.isNotEmpty() && missing.isNotEmpty()) {
            PartialGradePreview(entered = entered, missingLabels = missing)
        } else {
            null
        }
    }

    fun forCourse(course: CourseEntity, program: ProgramEntity): PartialGradePreview? {
        val fields = when (program.gradingScheme) {
            GradeCalculator.SVU_WEIGHTED -> listOf(
                "درجة الوظيفة" to course.assignmentGrade,
                "درجة الامتحان" to course.examGrade
            )

            GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY -> {
                if (
                    course.studentWorkGrade == null &&
                    course.practicalExamGrade == null &&
                    course.practicalGrade != null
                ) {
                    listOf(
                        "المجموع العملي" to course.practicalGrade,
                        "درجة النظري" to course.theoryGrade
                    )
                } else {
                    listOf(
                        "أعمال الطالب" to course.studentWorkGrade,
                        "الامتحان العملي" to course.practicalExamGrade,
                        "درجة النظري" to course.theoryGrade
                    )
                }
            }

            GradeCalculator.PRACTICAL_THEORY -> listOf(
                "درجة العملي" to course.practicalGrade,
                "درجة النظري" to course.theoryGrade
            )

            else -> emptyList()
        }
        return build(fields)
    }
}
