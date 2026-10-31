package com.creativeclasses.report

import java.time.LocalDate

enum class CallingPurpose(val label: String) {
    EXAM_RESULT("Exam Result Calling"),
    ATTENDANCE("Attendance Calling"),
    PARENT_MEETING("Parent Meeting Calling"),
    HOMEWORK("Homework/Assignment Calling"),
    PERFORMANCE("Performance Calling"),
    GENERAL("General Parent Calling"),
    OTHER("Other"),
}

enum class CallOutcome { CONTACTED, CONCERN_RAISED, WILL_VISIT, NO_ANSWER, CALL_LATER, NOT_CALLED }

/** [phrase] is used after "regarding" in report sentences. */
enum class Concern(val phrase: String) {
    LOW_MARKS("low marks"),
    ATTENDANCE("attendance"),
    BEHAVIOUR("behaviour"),
    HOMEWORK("homework"),
    FEES("fees"),
    RESULT_CLARIFICATION("result clarification"),
    OTHER("other matters"),
}

data class CallingSession(
    val sessionName: String,
    val className: String,
    val section: String? = null,
    val board: String? = null,
    val exam: String? = null,
    val purpose: CallingPurpose,
    val customPurpose: String? = null,
    val date: LocalDate,
    val teacher: String? = null,
)

data class CallRecord(
    val studentName: String,
    val outcome: CallOutcome,
    val concerns: Set<Concern> = emptySet(),
    val remark: String? = null,
    val followUpRequired: Boolean = false,
    val contactMissing: Boolean = false,
)

/** No usable phone number was found in the imported sheet and the parent has not been reached another way. */
fun CallRecord.noContact() = contactMissing && outcome == CallOutcome.NOT_CALLED

fun CallRecord.needsFollowUp() =
    followUpRequired || noContact() || outcome == CallOutcome.NO_ANSWER || outcome == CallOutcome.CALL_LATER

enum class ReportStatus(val label: String) {
    NOT_STARTED("Not Started"),
    PARTIALLY_COMPLETED("Partially Completed"),
    COMPLETED("Completed"),
    COMPLETED_SUCCESSFULLY("Completed Successfully"),
}

data class ProfessionalReport(val text: String, val status: ReportStatus)
