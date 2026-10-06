package com.academicjourney.app.data

/**
 * Curriculum transcribed from the supplied 2025–2026 Latakia University exam schedule for
 * Translation in the English Language (Open Education).
 *
 * The source contains both three- and four-digit course codes. They are intentionally kept as
 * separate courses exactly as requested. The printed F1/F2 marker is stored as semester 1/2.
 */
object LatakiaTranslationCurriculum {
    const val UNIVERSITY_NAME = "جامعة اللاذقية"
    const val PROGRAM_NAME = "الترجمة في اللغة الإنكليزية – التعليم المفتوح"
    const val DEGREE_TYPE = "إجازة"
    const val GRADING_SCHEME = "SINGLE_FINAL_GRADE"
    const val PASSING_GRADE = 50.0

    data class CourseDefinition(
        val code: String,
        val name: String,
        val academicYear: Int,
        val semester: Int
    )

    val courses: List<CourseDefinition> = listOf(
        // First year — F1
        course("111", "القواعد (1)", 1, 1),
        course("112", "القراءة والاستيعاب (1)", 1, 1),
        course("113", "المعاجم", 1, 1),
        course("114", "الكتابة (1)", 1, 1),
        course("115", "الترجمة العامة (1)", 1, 1),
        course("116", "اللغة العربية (1)", 1, 1),

        // First year — F2
        course("121", "القواعد (2)", 1, 2),
        course("122", "القراءة والاستيعاب (2)", 1, 2),
        course("123", "الكتابة (2)", 1, 2),
        course("124", "الترجمة العامة (2)", 1, 2),
        course("125", "الصوتيات (1)", 1, 2),
        course("126", "اللغة الفرنسية (1)", 1, 2),

        // Second year — F1
        course("211", "القواعد (3)", 2, 1),
        course("212", "الكتابة (3)", 2, 1),
        course("213", "الاستماع والمحادثة (1)", 2, 1),
        course("214", "الصوتيات (2)", 2, 1),
        course("215", "الترجمة العامة (3)", 2, 1),
        course("216", "اللغة العربية (2)", 2, 1),

        // Second year — F2
        course("221", "القواعد (4)", 2, 2),
        course("222", "نصوص ثقافية", 2, 2),
        course("223", "الترجمة العامة (4)", 2, 2),
        course("224", "الاستماع والمحادثة (2)", 2, 2),
        course("225", "المدخل إلى علم الترجمة", 2, 2),
        course("226", "اللغة الفرنسية (2)", 2, 2),

        // Third year — F1
        course("311", "علم اللغة (التركيب والدلالة)", 3, 1),
        course("312", "ترجمة تخصصية (1): سياسية إعلامية", 3, 1),
        course("313", "ترجمة تخصصية (2): اقتصادية تجارية", 3, 1),
        course("314", "ترجمة سمعية ومنظورة (1)", 3, 1),
        course("315", "اللغة العربية (3)", 3, 1),
        course("3312", "تدريبات في الاستماع والتعبير الشفهي", 3, 1),
        course("3313", "نصوص أدبية باللغة الإنكليزية (1)", 3, 1),
        course("3315", "نصوص ومصطلحات علمية باللغة الإنكليزية", 3, 1),

        // Third year — F2
        course("321", "نظريات الترجمة (1)", 3, 2),
        course("322", "لغويات مقارنة", 3, 2),
        course("323", "ترجمة تخصصية (3): سياحية وأثرية", 3, 2),
        course("324", "ترجمة تخصصية (4): علمية", 3, 2),
        course("325", "كتابة المقال", 3, 2),
        course("3321", "نصوص في الأدب العربي المعاصر", 3, 2),
        course("3323", "نصوص أدبية باللغة الإنكليزية (2)", 3, 2),
        course("3324", "ترجمة سمعية ومنظورة (2)", 3, 2),
        course("3325", "نصوص ومصطلحات سياسية باللغة الإنكليزية", 3, 2),

        // Fourth year — F1
        course("411", "ترجمة أدبية (1)", 4, 1),
        course("412", "ترجمة تخصصية (5)", 4, 1),
        course("413", "ترجمة تخصصية (6)", 4, 1),
        course("414", "ترجمة فورية (1)", 4, 1),
        course("415", "اللغة العربية (4)", 4, 1),
        course("4412", "المقال باللغة الإنكليزية (2)", 4, 1),
        course("4413", "لغويات مقارنة", 4, 1),

        // Fourth year — F2
        course("421", "ترجمة أدبية (2)", 4, 2),
        course("422", "ترجمة تخصصية (7)", 4, 2),
        course("423", "ترجمة تخصصية (8)", 4, 2),
        course("424", "ترجمة فورية (2)", 4, 2),
        course("425", "دراسات لغوية تتعلق بالترجمة", 4, 2),
        course("4421", "نصوص في الأدب العربي المعاصر", 4, 2),
        course("4422", "المقال باللغة الإنكليزية (3)", 4, 2),
        course("4423", "مقدمة في تحليل النصوص باللغة الإنكليزية", 4, 2)
    )

    fun isProgramme(programName: String): Boolean = programName == PROGRAM_NAME

    private fun course(code: String, name: String, year: Int, semester: Int) =
        CourseDefinition(code, name, year, semester)
}
