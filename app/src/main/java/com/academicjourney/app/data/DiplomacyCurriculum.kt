package com.academicjourney.app.data

/**
 * Official course numbers for the International Studies and Diplomacy programme.
 *
 * The mapping was transcribed from the supplied 2025–2026 registration workbook. Course names
 * intentionally match the stable names already stored by the app so migrations can update the
 * number in place without replacing rows or losing grades and notes.
 */
object DiplomacyCurriculum {
    val courseNumberByName: LinkedHashMap<String, String> = linkedMapOf(
        "مدخل إلى علم القانون" to "510",
        "المدخل إلى علم العلاقات الدولية" to "511",
        "مبادئ علم السياسة" to "512",
        "تاريخ الحضارة العام" to "513",
        "مدخل إلى علم الإدارة" to "514",
        "اللغة الإنكليزية (1)" to "515",
        "تاريخ الدبلوماسية" to "516",
        "الفكر السياسي القديم والوسيط" to "517",
        "علم الاجتماع السياسي" to "518",
        "القانون الدستوري والنظم السياسية" to "519",
        "مبادئ الاقتصاد" to "520",
        "اللغة الإنكليزية (2)" to "521",
        "تاريخ العلاقات الدولية (1)" to "522",
        "التنظيم الدولي" to "523",
        "الأخلاق" to "524",
        "الإحصاء" to "525",
        "الرأي العام ونظريات الاتصال" to "526",
        "اللغة الإنكليزية (3)" to "527",
        "القانون الدولي العام" to "528",
        "الفكر السياسي الحديث والمعاصر" to "529",
        "علم النفس الاجتماعي" to "530",
        "تاريخ العلاقات الدولية (2)" to "531",
        "مناهج البحث" to "532",
        "اللغة الإنكليزية (4)" to "533",
        "نظرية العلاقات الدولية" to "534",
        "حقوق الإنسان والقانون الدولي الإنساني" to "535",
        "تاريخ العرب الحديث والمعاصر" to "536",
        "التنمية البشرية" to "537",
        "الاستراتيجية والأمن القومي" to "538",
        "اللغة العربية (الأدب السياسي)" to "539",
        "نظرية السياسة الخارجية" to "540",
        "الإعلام الدولي" to "541",
        "القانون الدبلوماسي باللغة الإنكليزية" to "542",
        "النظم السياسية المقارنة" to "543",
        "الاقتصاد الدولي (1)" to "544",
        "العلاقات العربية الآسيوية والإفريقية" to "545",
        "العلاقات العربية الأوروبية والأمريكية" to "546",
        // The legacy app label corresponds to no. 547 in the supplied curriculum workbook.
        "التمرين الدولي الخاص باللغة الإنكليزية" to "547",
        "السياسات الخارجية المقارنة" to "548",
        "قضايا عالمية معاصرة" to "549",
        "إدارة الأزمات وفن التفاوض" to "550",
        "اللغة العربية (البلاغة والكتابة)" to "551",
        "إدارة المؤسسات الدولية" to "552",
        "الدبلوماسية والبروتوكول" to "553",
        "السياسات الخارجية السورية" to "554",
        // The legacy app label corresponds to no. 555 in the supplied curriculum workbook.
        "النظم الدبلوماسية العربية" to "555",
        "الاقتصاد الدولي (2)" to "556",
        "الجغرافيا السياسية" to "557"
    )

    fun numberFor(courseName: String): String =
        requireNotNull(courseNumberByName[courseName]) {
            "لا يوجد رقم رسمي للمقرر: $courseName"
        }

    fun isProgramme(programName: String): Boolean =
        programName.contains("الدراسات الدولية") && programName.contains("الدبلوماسية")
}
