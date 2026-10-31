package com.creativeclasses.callingapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.creativeclasses.report.CallOutcome
import com.creativeclasses.report.CallRecord
import com.creativeclasses.report.CallingPurpose
import com.creativeclasses.report.CallingSession
import com.creativeclasses.report.ImportedStudent
import com.creativeclasses.report.ReportGenerator
import com.creativeclasses.report.StudentImporter
import com.creativeclasses.report.XlsxReader
import com.creativeclasses.report.XlsxWorkbook
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class Screen { SETUP, CALLING, REPORT }

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

/** State is held in memory only: it survives rotation but not process death. */
class CallingViewModel : ViewModel() {
    var screen by mutableStateOf(Screen.SETUP)
    var sheetNames by mutableStateOf<List<String>>(emptyList())
    var selectedSheet by mutableStateOf<Int?>(null)
    var students by mutableStateOf<List<ImportedStudent>>(emptyList())
    var records by mutableStateOf<List<CallRecord>>(emptyList())
    var form by mutableStateOf(SessionForm())
    var message by mutableStateOf<String?>(null)
    var reportText by mutableStateOf("")
    val date: LocalDate = LocalDate.now()
    private var workbook: XlsxWorkbook? = null

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
        if (message == null) screen = Screen.CALLING
    }

    fun updateRecord(index: Int, change: (CallRecord) -> CallRecord) {
        records = records.toMutableList().also { it[index] = change(it[index]) }
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
