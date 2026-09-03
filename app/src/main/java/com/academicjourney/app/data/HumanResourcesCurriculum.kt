package com.academicjourney.app.data

/** Official SVU Human Resources credit-hour data supplied with the project. */
object HumanResourcesCurriculum {
    const val SECOND_YEAR_HOURS = 45
    const val THIRD_YEAR_HOURS = 105
    const val FOURTH_YEAR_HOURS = 180
    const val GRADUATION_HOURS = 269

    val creditHoursByCode: Map<String, Int> = linkedMapOf(
        "GBS301" to 4,
        "GBS302" to 4,
        "GBS303" to 2,
        "GMA401" to 5,
        "GMA402" to 5,
        "GMA403" to 5,
        "BAC401" to 5,
        "BAC402" to 5,
        "GBL401" to 4,
        "GBS504" to 5,
        "BMN401" to 4,
        "BEC401" to 5,
        "BMK401" to 5,
        "BEC402" to 5,
        "BHR401" to 5,
        "BEC403" to 5,
        "BMK502" to 5,
        "BQM501" to 5,
        "BHR502" to 5,
        "BFB401" to 5,
        "BMN502" to 6,
        "BMN503" to 5,
        "BFB502" to 6,
        "BQM502" to 6,
        "BQM603" to 5,
        "BMN504" to 5,
        "BMN505" to 5,
        "BMN606" to 5,
        "BMN507" to 6,
        "BMN508" to 5,
        "BAC506" to 5,
        "BMN609" to 5,
        "BAC607" to 5,
        "BQM604" to 6,
        "BMN610" to 5,
        "BQM607" to 5,
        "GRM501" to 5,
        "L1" to 3,
        "L2" to 3,
        "L3" to 3,
        "L4" to 3,
        "L5" to 3,
        "BAC504" to 5,
        "BHR603" to 5,
        "BHR604" to 5,
        "BHR605" to 5,
        "BHR606" to 5,
        "BHR607" to 6,
        "BHR608" to 6,
        "BHR609" to 6,
        "BHR610" to 5,
        "BHR611" to 6,
        "BHR612" to 5,
        // The supplied table totals 269 hours and lists the final 12-hour row
        // with duplicated BAC504 text. The existing curriculum identifies it as the project.
        "PHR601" to 12
    )

    private const val TRANSFER_NOTE =
        "تم الترفيع عند الانتقال من جامعة اللاذقية إلى الجامعة الافتراضية السورية."
    private const val ENGLISH_PLACEMENT_NOTE =
        "تم الترفيع بناءً على تحديد امتحان مستوى اللغة الإنكليزية."

    val passedWithoutGradeByCode: Map<String, String> = linkedMapOf(
        "BMN401" to TRANSFER_NOTE,
        "GBL401" to TRANSFER_NOTE,
        "BEC401" to TRANSFER_NOTE,
        "BMK401" to TRANSFER_NOTE,
        "BAC402" to TRANSFER_NOTE,
        "L1" to ENGLISH_PLACEMENT_NOTE,
        "L2" to ENGLISH_PLACEMENT_NOTE,
        "L3" to ENGLISH_PLACEMENT_NOTE
    )

    init {
        check(creditHoursByCode.values.sum() == GRADUATION_HOURS)
    }
}
