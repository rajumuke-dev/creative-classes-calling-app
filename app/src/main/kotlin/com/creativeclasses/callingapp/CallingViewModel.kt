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
import com.creativeclasses.report.NameMatcher
import com.creativeclasses.report.ReportGenerator
import com.creativeclasses.report.SessionProgress
import com.creativeclasses.report.needsFollowUp
import com.creativeclasses.report.StudentImporter
import com.creativeclasses.report.XlsxReader
import com.creativeclasses.report.XlsxWorkbook
import android.net.Uri
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
    ALL("All"), NOT_CALLED("Not called"), FOLLOW_UP("Follow-up"), LOW_MARKS("Low marks"), ABSENT("Absent")
}

data class SessionForm(
    val sessionName: String = "",
    val className: String = "",
    val section: String = "",
    val board: String = "",
    val exam: String = "",
    val purpose: CallingPurpose = CallingPurpose.EXAM_RESULT,
    val customPurpose: String = "",
    val teacher: String = "",
)

/** Calling sessions are saved to private storage after every change, so work resumes after the app is closed. */
class CallingViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
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
    var maxMarks by mutableStateOf("")
    var cutoff by mutableStateOf(DEFAULT_CUTOFF)
    var marksNotice by mutableStateOf<String?>(null)
    var form by mutableStateOf(SessionForm())
    var message by mutableStateOf<String?>(null)
    var reportText by mutableStateOf("")
    var date: LocalDate = LocalDate.now()
    private var workbook: XlsxWorkbook? = null

    init {
        refreshSessions()
    }

    private val maxMarksValue get() = maxMarks.trim().toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    private val cutoffValue get() = cutoff.trim().toDoubleOrNull()?.takeIf { it in 0.0..100.0 } ?: DEFAULT_CUTOFF.toDouble()

    fun isLow(index: Int) = Marks.isLow(marks.getOrNull(index), maxMarksValue, cutoffValue)

    val hasMarks get() = marks.any { it.isNotBlank() }
    val marksReady get() = maxMarksValue != null

    /** Low marks are listed lowest score first so the weakest students are called first. */
    val visibleIndices: List<Int>
        get() = when (filter) {
            CallFilter.LOW_MARKS -> records.indices.filter { isLow(it) }.sortedBy { Marks.parse(marks[it]).score }
            else -> records.indices.filter {
                when (filter) {
                    CallFilter.NOT_CALLED -> records[it].outcome == CallOutcome.NOT_CALLED
                    CallFilter.FOLLOW_UP -> records[it].needsFollowUp()
                    CallFilter.ABSENT -> Marks.isAbsent(marks.getOrNull(it))
                    else -> true
                }
            }
        }

    fun setMark(index: Int, text: String) {
        marks = marks.toMutableList().also { it[index] = text.take(8) }
        scheduleSave()
    }

    fun updateMaxMarks(text: String) {
        maxMarks = text.filter { it.isDigit() || it == '.' }.take(6)
        scheduleSave()
    }

    fun updateCutoff(text: String) {
        cutoff = text.filter { it.isDigit() || it == '.' }.take(5)
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
        maxMarks = ""
        cutoff = DEFAULT_CUTOFF
        form = SessionForm()
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
        maxMarks = session.maxMarks
        cutoff = session.cutoff
        filter = CallFilter.ALL
        resumeIndex = SessionProgress.of(session.records).resumeIndex ?: 0
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

    /** Synchronous so it also works from onStop; the file is small. */
    fun saveNow() {
        saveJob?.cancel()
        write()
    }

    private fun write() {
        val id = currentId ?: return
        store.save(SavedSession(id, form, date, students, records, marks, maxMarks, cutoff))
    }

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
            filter = CallFilter.ALL
            resumeIndex = 0
            saveNow()
            screen = Screen.CALLING
        }
    }

    fun updateRecord(index: Int, change: (CallRecord) -> CallRecord) {
        records = records.toMutableList().also { it[index] = change(it[index]) }
        scheduleSave()
    }

    fun prepareReport() {
        val session = CallingSession(
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
        reportText = ReportGenerator().generate(session, records).text
        screen = Screen.REPORT
    }
}
