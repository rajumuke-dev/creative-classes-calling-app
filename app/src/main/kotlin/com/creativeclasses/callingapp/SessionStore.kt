package com.creativeclasses.callingapp

import android.content.Context
import com.creativeclasses.report.CallOutcome
import com.creativeclasses.report.CallRecord
import com.creativeclasses.report.CallingPurpose
import com.creativeclasses.report.Concern
import com.creativeclasses.report.ImportedStudent
import com.creativeclasses.report.MarksRule
import java.io.File
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class SavedSession(
    val id: String,
    val form: SessionForm,
    val date: LocalDate,
    val students: List<ImportedStudent>,
    val records: List<CallRecord>,
    /** Marks text per student, parallel to [students]: "41", "AB", "-" or blank. */
    val marks: List<String>,
    /** Marks threshold for the "By marks" filter. Blank until the teacher sets it. */
    val threshold: String,
    val marksRule: MarksRule,
    /** Report text the teacher saved to share later, exactly as previewed/edited. Null until saved. */
    val savedReport: String? = null,
) {
    val title: String
        get() = form.sessionName.ifBlank {
            val purpose = if (form.purpose == CallingPurpose.OTHER) form.customPurpose else form.purpose.label
            listOf(form.className, form.board, purpose).filter { it.isNotBlank() }.joinToString(" ")
        }
}

/** One JSON file per session in the app's private storage, written atomically. */
class SessionStore(context: Context) {
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }

    /** Most recently updated first. */
    fun list(): List<SavedSession> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .mapNotNull {
                runCatching { decode(JSONObject(it.readText())) }
                    .onFailure { e -> android.util.Log.w("SessionStore", "Skipping unreadable session ${it.name}", e) }
                    .getOrNull()
            }

    @Synchronized
    fun save(session: SavedSession) {
        val tmp = File(dir, "${session.id}.tmp")
        tmp.writeText(encode(session).toString())
        tmp.renameTo(file(session.id))
    }

    fun delete(id: String) {
        file(id).delete()
    }

    private fun file(id: String) = File(dir, "$id.json")

    private fun encode(s: SavedSession) = JSONObject().apply {
        put("id", s.id)
        put("date", s.date.toString())
        put("form", JSONObject().apply {
            put("sessionName", s.form.sessionName)
            put("className", s.form.className)
            put("section", s.form.section)
            put("board", s.form.board)
            put("exam", s.form.exam)
            put("purpose", s.form.purpose.name)
            put("customPurpose", s.form.customPurpose)
            put("teacher", s.form.teacher)
        })
        put("students", JSONArray(s.students.map { st ->
            JSONObject().apply {
                put("name", st.name)
                put("contact1", st.contact1 ?: JSONObject.NULL)
                put("contact2", st.contact2 ?: JSONObject.NULL)
                put("group", st.group ?: JSONObject.NULL)
                put("school", st.school ?: JSONObject.NULL)
            }
        }))
        put("records", JSONArray(s.records.map { r ->
            JSONObject().apply {
                put("name", r.studentName)
                put("outcome", r.outcome.name)
                put("concerns", JSONArray(r.concerns.map { it.name }))
                put("remark", r.remark ?: JSONObject.NULL)
                put("followUp", r.followUpRequired)
            }
        }))
        put("marks", JSONArray(s.marks))
        put("threshold", s.threshold)
        put("marksRule", s.marksRule.name)
        put("savedReport", s.savedReport ?: JSONObject.NULL)
    }

    private fun decode(o: JSONObject): SavedSession {
        val f = o.getJSONObject("form")
        return SavedSession(
            id = o.getString("id"),
            date = LocalDate.parse(o.getString("date")),
            form = SessionForm(
                sessionName = f.getString("sessionName"),
                className = f.getString("className"),
                section = f.getString("section"),
                board = f.getString("board"),
                exam = f.getString("exam"),
                purpose = CallingPurpose.valueOf(f.getString("purpose")),
                customPurpose = f.getString("customPurpose"),
                teacher = f.getString("teacher"),
            ),
            students = o.getJSONArray("students").objects().map {
                ImportedStudent(it.getString("name"), it.str("contact1"), it.str("contact2"), it.str("group"), it.str("school"))
            },
            records = o.getJSONArray("records").objects().map { r ->
                CallRecord(
                    studentName = r.getString("name"),
                    outcome = CallOutcome.valueOf(r.getString("outcome")),
                    concerns = r.getJSONArray("concerns").let { a -> (0 until a.length()).map { Concern.valueOf(a.getString(it)) }.toSet() },
                    remark = r.str("remark"),
                    followUpRequired = r.getBoolean("followUp"),
                )
            },
            marks = o.optJSONArray("marks")?.let { a -> (0 until a.length()).map { a.getString(it) } }
                ?: List(o.getJSONArray("students").length()) { "" },
            threshold = o.optString("threshold", o.optString("lowBelow", "")),
            marksRule = MarksRule.values().firstOrNull { it.name == o.optString("marksRule") } ?: MarksRule.BELOW,
            savedReport = if (o.has("savedReport")) o.str("savedReport") else null,
        )
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)
}
