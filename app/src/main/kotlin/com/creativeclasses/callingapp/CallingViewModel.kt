package com.creativeclasses.callingapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.creativeclasses.report.CallOutcome
import com.creativeclasses.report.CallRecord
import com.creativeclasses.report.CallingPurpose
import com.creativeclasses.report.CallingSession
import com.creativeclasses.report.ImportedStudent
import com.creativeclasses.report.MarkRowParser
import com.creativeclasses.report.Marks
import com.creativeclasses.report.MarksRule
import com.creativeclasses.report.NameMatcher
import com.creativeclasses.report.PhoneNumbers
import com.creativeclasses.report.ReportGenerator
import com.creativeclasses.report.SessionWorkbook
import com.creativeclasses.report.SheetRow
import com.creativeclasses.report.needsFollowUp
import com.creativeclasses.report.StudentImporter
import com.creativeclasses.report.XlsxReader
import com.creativeclasses.report.XlsxWorkbook
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { DASHBOARD, SETUP, CALLING, REPORT }

enum class CallFilter(val label: String) {
    ALL("All"), NOT_CALLED("Not called"), FOLLOW_UP("Follow-up"), BY_MARKS("By marks"), ABSENT("Absent")
}

private fun thresholdOf(text: String) = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }

/** Students shown for [filter], in calling order. Low marks are listed lowest score first so the weakest students are called first. */
fun visibleIndices(filter: CallFilter, records: List<CallRecord>, marks: List<String>, rule: MarksRule, threshold: String): List<Int> =
    when (filter) {
        CallFilter.BY_MARKS -> records.indices.filter { Marks.matches(marks.getOrNull(it), rule, thresholdOf(threshold)) }.let { hits ->
            if (rule == MarksRule.BELOW) hits.sortedBy { Marks.parse(marks[it]).score }
            else hits.sortedByDescending { Marks.parse(marks[it]).score }
        }
        else -> records.indices.filter {
            when (filter) {
                CallFilter.NOT_CALLED -> records[it].outcome == CallOutcome.NOT_CALLED
                CallFilter.FOLLOW_UP -> records[it].needsFollowUp()
                CallFilter.ABSENT -> Marks.isAbsent(marks.getOrNull(it))
                else -> true
            }
        }
    }

/** Next student to call in the list "Continue calling" reopens (the saved filter), or null when that list is done. */
fun SavedSession.nextInList(): Int? =
    visibleIndices(filter, records, marks, marksRule, threshold).firstOrNull { records[it].outcome == CallOutcome.NOT_CALLED }

data class SessionForm(
    val sessionName: String = "",
    val className: String = "",
    val section: String = "",
    val board: String = "",
    val exam: String = "",
    /** Told to parents who were not reached, together with the student's mark. */
    val subject: String = "",
    val purpose: CallingPurpose = CallingPurpose.EXAM_RESULT,
    val customPurpose: String = "",
    val teacher: String = "",
    /** When the teacher is available, told to parents who were not reached in the pre-filled text message. */
    val availability: String = "",
)

/** Calling sessions are saved to private storage after every change, so work resumes after the app is closed. */
class CallingViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    val breakTimer = BreakTimer(app)
    private var currentId: String? = null
    private var saveJob: Job? = null
    var sessions by mutableStateOf<List<SavedSession>>(emptyList())
    var filter by mutableStateOf(CallFilter.ALL)
    var resumeIndex by mutableStateOf(0)
    var screen by mutableStateOf(Screen.DASHBOARD)
    var sheetNames by mutableStateOf<List<String>>(emptyList())
    var selectedSheet by mutableStateOf<Int?>(null)
    var students by mutableStateOf<List<ImportedStudent>>(emptyList())
    var records by mutableStateOf<List<CallRecord>>(emptyList())
    var marks by mutableStateOf<List<String>>(emptyList())
    var threshold by mutableStateOf("")
    var marksRule by mutableStateOf(MarksRule.BELOW)
    var marksNotice by mutableStateOf<String?>(null)
    var filterNotice by mutableStateOf<String?>(null)
    var reportScope by mutableStateOf<String?>(null)
    var form by mutableStateOf(SessionForm())
    var message by mutableStateOf<String?>(null)
    var reportText by mutableStateOf("")
    private var savedReport: String? = null
    var date: LocalDate = LocalDate.now()
    private var workbook: XlsxWorkbook? = null

    init {
        refreshSessions()
    }

    private val thresholdValue get() = thresholdOf(threshold)

    fun matchesMarks(index: Int) = Marks.matches(marks.getOrNull(index), marksRule, thresholdValue)

    val hasMarks get() = marks.any { it.isNotBlank() }
    /** Students whose marks are blank can never match a marks filter, so the teacher is told who they are. */
    val studentsWithoutMarks get() = students.indices.filter { marks.getOrNull(it).isNullOrBlank() }.map { students[it].name }

    val marksReady get() = thresholdValue != null

    /** Every student in the list being worked through has an outcome: the marks/absent selection when that filter is on, otherwise the whole class. */
    val listDone: Boolean
        get() = (if (filter == CallFilter.BY_MARKS || filter == CallFilter.ABSENT) visibleIndices else records.indices.toList())
            .let { list -> list.isNotEmpty() && list.all { records[it].outcome != CallOutcome.NOT_CALLED } }

    val visibleIndices: List<Int> get() = visibleIndices(filter, records, marks, marksRule, threshold)

    fun setMark(index: Int, text: String) {
        marks = marks.toMutableList().also { it[index] = text.take(8) }
        scheduleSave()
    }

    fun updateTeacher(teacher: String, availability: String) {
        form = form.copy(teacher = teacher.trim(), availability = availability.trim())
        scheduleSave()
    }

    fun updateMarksRule(rule: MarksRule) {
        marksRule = rule
        scheduleSave()
    }

    fun updateThreshold(text: String) {
        threshold = text.filter { it.isDigit() || it == '.' }.take(6)
        scheduleSave()
    }

    fun importMarksImage(uri: Uri) {
        viewModelScope.launch {
            marksNotice = try {
                val lines = withContext(Dispatchers.IO) { MarksOcr.readRows(getApplication(), uri) }
                val rows = MarkRowParser.parse(lines)
                val result = NameMatcher.match(rows, students.map { it.name })
                marks = marks.toMutableList().also { m -> result.matched.forEach { (i, v) -> m[i] = v } }
                scheduleSave()
                buildString {
                    append("Marks filled for ${result.matched.size} of ${students.size} students (${rows.size} rows read from the image).")
                    if (result.unmatched.isNotEmpty()) {
                        append("\n\nCould not match these names, please enter their marks on the student cards:\n")
                        append(result.unmatched.joinToString("\n") { "${it.name} (${it.marks})" })
                    }
                    val missing = students.indices.filter { it !in result.matched }
                    if (missing.isNotEmpty()) {
                        append("\n\nNo marks found for: ")
                        append(missing.joinToString(", ") { students[it].name })
                    }
                    if (result.matched.isEmpty()) append("\n\nNo marks could be read. Try a clearer, straight-on image.")
                }
            } catch (e: Exception) {
                "Could not read this image."
            }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch { sessions = withContext(Dispatchers.IO) { store.list() } }
    }

    fun newSession() {
        currentId = null
        workbook = null
        sheetNames = emptyList()
        selectedSheet = null
        students = emptyList()
        records = emptyList()
        marks = emptyList()
        threshold = ""
        marksRule = MarksRule.BELOW
        savedReport = null
        // Teacher details rarely change, so they carry over from the most recent session.
        form = sessions.firstOrNull()?.form.let { SessionForm(teacher = it?.teacher.orEmpty(), availability = it?.availability.orEmpty()) }
        date = LocalDate.now()
        message = null
        screen = Screen.SETUP
    }

    fun openSession(session: SavedSession) {
        currentId = session.id
        form = session.form
        date = session.date
        students = session.students
        records = session.records
        marks = session.marks
        threshold = session.threshold
        marksRule = session.marksRule
        savedReport = session.savedReport
        filter = session.filter
        // Position in the visible (possibly filtered) list, as that is what the calling screen scrolls.
        resumeIndex = visibleIndices.indexOfFirst { records[it].outcome == CallOutcome.NOT_CALLED }.coerceAtLeast(0)
        screen = Screen.CALLING
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            sessions = withContext(Dispatchers.IO) {
                store.delete(id)
                store.list()
            }
        }
    }

    fun goToDashboard() {
        saveNow()
        screen = Screen.DASHBOARD
        refreshSessions()
    }

    /** Keeps the previewed (possibly edited) report with the session so it can be shared later from the dashboard. */
    fun saveReportForLater() {
        savedReport = reportText
        goToDashboard()
    }

    /** Synchronous so it also works from onStop; the file is small. */
    fun saveNow() {
        saveJob?.cancel()
        write()
    }

    private fun write() {
        val id = currentId ?: return
        store.save(snapshot(id))
    }

    private fun snapshot(id: String) = SavedSession(id, form, date, students, records, marks, threshold, marksRule, savedReport, filter)

    /** Saves first, then exports the open session with the report text being previewed. */
    fun exportCurrentExcel(): File {
        saveNow()
        return exportExcel(snapshot(currentId ?: "current"), reportText.takeIf { it.isNotBlank() })
    }

    /**
     * Writes "<session name>.xlsx" from the session's latest saved state, so the file always matches the calls made.
     * The report sheet uses [reportText], else the saved report, else a fresh report for the whole class.
     */
    fun exportExcel(session: SavedSession, reportText: String? = null): File {
        val calling = callingSession(session.form, session.date)
        val rows = session.records.mapIndexed { i, record ->
            SheetRow(session.students.getOrNull(i) ?: ImportedStudent(record.studentName, null, null), record, session.marks.getOrNull(i))
        }
        val report = reportText ?: session.savedReport ?: ReportGenerator().generate(
            calling,
            rows.map { r -> r.record.copy(contactMissing = listOfNotNull(r.student.contact1, r.student.contact2).none { PhoneNumbers.isValid(it) }) },
        ).text
        // Only the latest export is kept; it is rebuilt on every share.
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
        return File(dir, SessionWorkbook.fileName(session.title)).also { file ->
            file.outputStream().use { SessionWorkbook.write(it, calling.copy(sessionName = session.title), session.form.subject, rows, report) }
        }
    }

    private fun callingSession(form: SessionForm, date: LocalDate) = CallingSession(
        sessionName = form.sessionName,
        className = form.className,
        section = form.section,
        board = form.board,
        exam = form.exam,
        purpose = form.purpose,
        customPurpose = form.customPurpose,
        date = date,
        teacher = form.teacher,
    )

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            write()
        }
    }

    fun importFile(open: () -> InputStream?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = open() ?: throw IllegalArgumentException("Could not open the selected file.")
                val wb = stream.use { XlsxReader.open(it) }
                workbook = wb
                sheetNames = wb.sheetNames.map { it.trim() }
                selectedSheet = null
                students = emptyList()
                records = emptyList()
                marks = emptyList()
                message = "Select the sheet to call."
                if (wb.sheetNames.size == 1) loadSheet(0)
            } catch (e: IllegalArgumentException) {
                message = e.message
            } catch (e: Exception) {
                message = "Could not read this file. Please use an .xlsx Excel file."
            }
        }
    }

    fun selectSheet(index: Int) {
        viewModelScope.launch(Dispatchers.IO) { loadSheet(index) }
    }

    private fun loadSheet(index: Int) {
        val wb = workbook ?: return
        selectedSheet = index
        try {
            val list = StudentImporter.fromRows(wb.rows(index))
            val info = StudentImporter.parseSheetName(wb.sheetNames[index])
            students = list
            records = list.map { CallRecord(it.name, CallOutcome.NOT_CALLED) }
            marks = List(list.size) { "" }
            form = form.copy(className = info.className.orEmpty(), board = info.board.orEmpty())
            message = "${list.size} students loaded from '${sheetNames[index]}'."
        } catch (e: IllegalArgumentException) {
            students = emptyList()
            records = emptyList()
            marks = emptyList()
            message = e.message
        }
    }

    fun startCalling() {
        message = when {
            students.isEmpty() -> "Import an Excel file first."
            form.className.isBlank() -> "Please enter the class."
            form.purpose == CallingPurpose.OTHER && form.customPurpose.isBlank() -> "Please enter the custom purpose."
            else -> null
        }
        if (message == null) {
            if (currentId == null) currentId = UUID.randomUUID().toString()
            filter = if (hasMarks && marksReady) CallFilter.BY_MARKS else CallFilter.ALL
            resumeIndex = 0
            saveNow()
            screen = Screen.CALLING
        }
    }

    private fun hasValidContact(index: Int) = students[index].let { s ->
        listOfNotNull(s.contact1, s.contact2).any { PhoneNumbers.isValid(it) }
    }

    fun updateRecord(index: Int, change: (CallRecord) -> CallRecord) {
        val current = records[index]
        val updated = change(current)
        if (updated == current) return
        records = records.toMutableList().also { it[index] = updated }
        // A saved report no longer reflects the calls, so it must not be shared from the dashboard.
        savedReport = null
        // A recorded call is written at once so it survives the app being killed; remark typing stays debounced.
        if (updated.outcome != current.outcome) saveNow() else scheduleSave()
    }

    fun prepareReport() {
        val session = callingSession(form, date)
        val scope = visibleIndices.sorted()
        if (scope.isEmpty()) {
            filterNotice = "No students in this view to report. Change the filter first."
            return
        }
        reportScope = if (filter == CallFilter.ALL) null else "${scope.size} of ${records.size} students (${filter.label} filter)"
        reportText = ReportGenerator().generate(session, scope.map { records[it].copy(contactMissing = !hasValidContact(it)) }).text
        screen = Screen.REPORT
    }
}
