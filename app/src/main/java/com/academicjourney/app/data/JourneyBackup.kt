package com.academicjourney.app.data

import com.academicjourney.app.domain.GradeCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object JourneyBackup {
    data class Extras(val events: List<AcademicEventEntity>, val history: List<GradeChangeEntity>,
        val archives: List<SemesterArchiveEntity>, val skipped: Int)

    fun encode(events: List<AcademicEventEntity>, history: List<GradeChangeEntity>, archives: List<SemesterArchiveEntity>) =
        JSONObject().apply {
            put("events", JSONArray().apply { events.forEach { e -> put(JSONObject().apply {
                put("id", e.id); put("courseId", e.courseId); put("kind", e.kind); put("title", e.title)
                put("place", e.place); put("startsAt", e.startsAt)
                put("reminderMinutes", e.reminderMinutes ?: JSONObject.NULL)
                put("notifiedAt", e.notifiedAt ?: JSONObject.NULL)
            }) } })
            put("history", JSONArray().apply { history.forEach { h -> put(JSONObject().apply {
                put("id", h.id); put("targetKind", h.targetKind); put("targetId", h.targetId)
                put("title", h.title); put("beforeJson", h.beforeJson); put("afterJson", h.afterJson)
                put("createdAt", h.createdAt); put("undone", h.undone)
            }) } })
            put("archives", JSONArray().apply { archives.forEach { a -> put(JSONObject().apply {
                put("id", a.id); put("programId", a.programId); put("academicYear", a.academicYear)
                put("semester", a.semester); put("label", a.label); put("createdAt", a.createdAt)
                put("snapshotJson", a.snapshotJson)
            }) } })
        }

    fun decode(root: JSONObject?, courseIds: Map<Long, Long>, programIds: Map<Long, Long>,
        schoolIds: Map<Long, Long>, courses: List<CourseEntity>, school: List<HighSchoolGradeEntity>,
        programs: List<ProgramEntity>): Extras {
        if (root == null) return Extras(emptyList(), emptyList(), emptyList(), 0)
        var skipped = 0
        fun objects(key: String): List<JSONObject> {
            val array = root.optJSONArray(key) ?: return emptyList()
            require(array.length() <= 100_000) { "سجلات النسخة الاحتياطية أكثر من الحد المسموح." }
            return (0 until array.length()).map { array.getJSONObject(it) }
        }
        fun id(o: JSONObject) = o.getString("id").also { UUID.fromString(it) }
        fun small(o: JSONObject, key: String, max: Int = 2000) = o.getString(key).also { require(it.length <= max) }
        val events = objects("events").mapNotNull { o ->
            val courseId = courseIds[o.getLong("courseId")] ?: run { skipped++; return@mapNotNull null }
            val minutes = if (o.isNull("reminderMinutes")) null else o.getInt("reminderMinutes")
            require(minutes == null || minutes in listOf(0, 15, 30, 60, 1440, 2880, 10080))
            val kind = o.getString("kind")
            require(kind in listOf("EXAM", "ASSIGNMENT"))
            val startsAt = o.getLong("startsAt").also { require(it in 1..32_503_680_000_000L) }
            AcademicEventEntity(id(o), courseId, kind, small(o, "title"), small(o, "place"), startsAt,
                minutes, if (o.isNull("notifiedAt")) null else o.getLong("notifiedAt"))
        }
        val history = objects("history").mapNotNull { o ->
            val kind = o.getString("targetKind")
            require(kind in listOf("COURSE", "SCHOOL"))
            val targetId = (if (kind == "COURSE") courseIds else schoolIds)[o.getLong("targetId")]
                ?: run { skipped++; return@mapNotNull null }
            val before = small(o, "beforeJson", 60_000)
            val after = small(o, "afterJson", 60_000)
            if (kind == "COURSE") {
                val c = courses.first { it.id == targetId }
                val p = programs.first { it.id == c.programId }
                validateCourse(GradeSnapshot.apply(c, before), p)
                validateCourse(GradeSnapshot.apply(c, after), p)
            } else {
                val max = school.first { it.id == targetId }.maxGrade
                listOf(before, after).forEach { json ->
                    val v = JSONObject(json)
                    require(v.has("grade") && (v.isNull("grade") || v.getDouble("grade").let { it % 1.0 == 0.0 && it in 0.0..max.toDouble() }))
                }
            }
            GradeChangeEntity(id(o), kind, targetId, small(o, "title"), before, after, o.getLong("createdAt"), o.getBoolean("undone"))
        }
        val archives = objects("archives").mapNotNull { o ->
            val programId = programIds[o.getLong("programId")] ?: run { skipped++; return@mapNotNull null }
            val snapshot = small(o, "snapshotJson", 2_000_000)
            val data = JSONObject(snapshot)
            val items = data.getJSONArray("courses")
            require(items.length() in 1..1000 && data.getInt("total") == items.length())
            require(data.getInt("passed") in 0..items.length())
            require(data.isNull("average") || data.getDouble("average") in 0.0..100.0)
            for (i in 0 until items.length()) {
                val c = items.getJSONObject(i)
                require(c.getString("name").length <= 2000)
                c.getString("code"); c.getBoolean("transferred"); c.getJSONObject("values")
                require(c.isNull("grade") || c.getDouble("grade") in 0.0..100.0)
                if (!c.isNull("passed")) c.getBoolean("passed")
            }
            val year = o.getInt("academicYear").also { require(it in 1..20) }
            val semester = o.getInt("semester").also { require(it in 1..2) }
            SemesterArchiveEntity(id(o), programId, year, semester, small(o, "label"), o.getLong("createdAt"), snapshot)
        }
        return Extras(events.distinctBy { it.id }, history.distinctBy { it.id }, archives.distinctBy { it.id }, skipped)
    }

    fun validateCourse(c: CourseEntity, p: ProgramEntity) {
        val error = when (p.gradingScheme) {
            GradeCalculator.PRACTICAL_THEORY -> GradeCalculator.validatePartialPracticalTheory(c.practicalGrade, c.theoryGrade)
            GradeCalculator.ANDALUS_SPLIT_PRACTICAL_THEORY -> GradeCalculator.validatePartialAndalus(c.studentWorkGrade ?: c.practicalGrade, c.practicalExamGrade, c.theoryGrade)
            GradeCalculator.SINGLE_FINAL_GRADE -> GradeCalculator.validateDirectGrade(c.directGrade)
            else -> GradeCalculator.validatePartialSvu(c.assignmentGrade, c.examGrade)
        }
        require(error == null) { "${c.name}: $error" }
    }
}
