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
import com.creativeclasses.report.ReportGenerator
import com.creativeclasses.report.SessionProgress
import com.creativeclasses.report.needsFollowUp
import com.creativeclasses.report.StudentImporter
import com.creativeclasses.report.XlsxReader
import com.creativeclasses.report.XlsxWorkbook
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { DASHBOARD, SETUP, CALLING, REPORT }

enum class CallFilter(val label: String) { ALL("All"), NOT_CALLED("Not called"), FOLLOW_UP("Follow-up") }

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
    var form by mutableStateOf(SessionForm())
    var message by mutableStateOf<String?>(null)
    var reportText by mutableStateOf("")
    var date: LocalDate = LocalDate.now()
    private var workbook: XlsxWorkbook? = null

    init {
        refreshSessions()
    }

    val visibleIndices: List<Int>
        get() = records.indices.filter {
            when (filter) {
                CallFilter.ALL -> true
                CallFilter.NOT_CALLED -> records[it].outcome == CallOutcome.NOT_CALLED
                CallFilter.FOLLOW_UP -> records[it].needsFollowUp()
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
        store.save(SavedSession(id, form, date, students, records))
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
            form = form.copy(className = info.className.orEmpty(), board = info.board.orEmpty())
            message = "${list.size} students loaded from '${sheetNames[index]}'."
        } catch (e: IllegalArgumentException) {
            students = emptyList()
            records = emptyList()
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
