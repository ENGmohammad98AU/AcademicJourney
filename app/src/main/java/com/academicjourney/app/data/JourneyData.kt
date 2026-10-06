package com.academicjourney.app.data

import com.academicjourney.app.domain.GradeCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Only editable values: undo can never change curriculum, IDs or transferred credits. */
object GradeSnapshot {
    val fields = linkedMapOf(
        "practicalGrade" to "العملي", "theoryGrade" to "النظري",
        "assignmentGrade" to "الوظيفة", "examGrade" to "الامتحان",
        "studentWorkGrade" to "أعمال الطالب", "practicalExamGrade" to "الامتحان العملي",
        "directGrade" to "الدرجة النهائية", "notes" to "الملاحظة"
    )
    fun encode(c: CourseEntity): String = JSONObject().apply {
        put("practicalGrade", c.practicalGrade ?: JSONObject.NULL)
        put("theoryGrade", c.theoryGrade ?: JSONObject.NULL)
        put("assignmentGrade", c.assignmentGrade ?: JSONObject.NULL)
        put("examGrade", c.examGrade ?: JSONObject.NULL)
        put("studentWorkGrade", c.studentWorkGrade ?: JSONObject.NULL)
        put("practicalExamGrade", c.practicalExamGrade ?: JSONObject.NULL)
        put("directGrade", c.directGrade ?: JSONObject.NULL)
        put("notes", c.notes)
    }.toString()

    fun apply(c: CourseEntity, json: String): CourseEntity {
        val o = JSONObject(json)
        fun grade(key: String): Double? = if (o.isNull(key)) null else o.getDouble(key).also {
            require(it.isFinite() && it in 0.0..100.0) { "درجة غير صالحة في سجل التعديلات." }
        }
        val notes = o.getString("notes")
        require(notes.length <= 50_000)
        return c.copy(practicalGrade = grade("practicalGrade"), theoryGrade = grade("theoryGrade"),
            assignmentGrade = grade("assignmentGrade"), examGrade = grade("examGrade"),
            studentWorkGrade = grade("studentWorkGrade"), practicalExamGrade = grade("practicalExamGrade"),
            directGrade = grade("directGrade"), notes = notes)
    }

    fun differences(before: String, after: String): List<String> {
        val a = JSONObject(before)
        val b = JSONObject(after)
        val labels = if (a.has("grade")) mapOf("grade" to "الدرجة") else fields
        fun value(o: JSONObject, k: String) = if (o.isNull(k)) "فارغ" else o.get(k).toString()
        return labels.mapNotNull { (k, label) ->
            val av = value(a, k)
            val bv = value(b, k)
            if (av == bv) null else "$label: قبل «$av» • بعد «$bv»"
        }
    }
    fun same(a: String, b: String) = differences(a, b).isEmpty()
    fun highSchool(grade: Int?) = JSONObject().put("grade", grade ?: JSONObject.NULL).toString()
}

object SemesterSnapshots {
    fun create(program: ProgramEntity, courses: List<CourseEntity>, year: Int, semester: Int, label: String): SemesterArchiveEntity {
        require(courses.isNotEmpty())
        require(courses.all { it.programId == program.id && it.academicYear == year && it.semester == semester })
        val root = JSONObject().apply {
            put("program", program.name)
            put("average", GradeCalculator.average(courses, program) ?: JSONObject.NULL)
            put("passed", courses.count { GradeCalculator.calculate(it, program).isPassed == true })
            put("total", courses.size)
            put("courses", JSONArray().apply {
                courses.forEach { c ->
                    val result = GradeCalculator.calculate(c, program)
                    put(JSONObject().apply {
                        put("name", c.name); put("code", c.code); put("hours", c.creditHours ?: JSONObject.NULL)
                        put("grade", result.finalGrade ?: JSONObject.NULL)
                        put("passed", result.isPassed ?: JSONObject.NULL)
                        put("assistance", result.assistancePoints)
                        put("transferred", c.passedWithoutGrade)
                        put("values", JSONObject(GradeSnapshot.encode(c)))
                    })
                }
            })
        }
        return SemesterArchiveEntity(UUID.randomUUID().toString(), program.id, year, semester,
            label.trim().ifBlank { "${program.name} • السنة $year • الفصل $semester" },
            System.currentTimeMillis(), root.toString())
    }
}
