package com.academicjourney.app.data

object HighSchoolSeedData {
    const val SCIENTIFIC_2016 = "SCIENTIFIC_2016"
    const val LITERARY_2026 = "LITERARY_2026"

    suspend fun seed(dao: AcademicDao) {
        if (dao.highSchoolGradeCount() > 0) return

        scientificSubjects.forEachIndexed { index, subject ->
            dao.insertHighSchoolGrade(
                HighSchoolGradeEntity(
                    branch = SCIENTIFIC_2016,
                    subject = subject.name,
                    maxGrade = subject.maxGrade,
                    includedInPercentage = subject.included,
                    displayOrder = index
                )
            )
        }
        literarySubjects.forEachIndexed { index, subject ->
            dao.insertHighSchoolGrade(
                HighSchoolGradeEntity(
                    branch = LITERARY_2026,
                    subject = subject.name,
                    maxGrade = subject.maxGrade,
                    includedInPercentage = subject.included,
                    displayOrder = index
                )
            )
        }
    }

    private data class SubjectSeed(val name: String, val maxGrade: Int, val included: Boolean = true)

    private val scientificSubjects = listOf(
        SubjectSeed("اللغة العربية", 400),
        SubjectSeed("اللغة الإنكليزية", 300),
        SubjectSeed("اللغة الفرنسية", 300, included = false),
        SubjectSeed("التربية الوطنية", 200),
        SubjectSeed("الرياضيات", 600),
        SubjectSeed("الفيزياء", 400),
        SubjectSeed("الكيمياء", 200),
        SubjectSeed("علم الأحياء", 300),
        SubjectSeed("التربية الدينية", 200, included = false)
    )

    private val literarySubjects = listOf(
        SubjectSeed("اللغة العربية", 600),
        SubjectSeed("الفلسفة والعلوم الإنسانية", 400),
        SubjectSeed("التاريخ", 300),
        SubjectSeed("الجغرافيا", 300),
        SubjectSeed("التربية الدينية", 200),
        SubjectSeed("اللغة الإنكليزية", 400),
        SubjectSeed("اللغة الفرنسية", 400, included = false)
    )
}
