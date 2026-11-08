package com.creativeclasses.report

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure, deterministic report builder. Uses only session data, call outcomes, concern selections and teacher remarks. */
class ReportGenerator(private val organisation: String = "Creative Classes") {

    fun generate(session: CallingSession, records: List<CallRecord>): ProfessionalReport =
        generateSections(session, listOf(ReportSection(null, records)))

    /** One report with counts and feedback per section (e.g. the Lower than and Higher than marks lists); a null title adds no heading. */
    fun generateSections(session: CallingSession, sections: List<ReportSection>): ProfessionalReport {
        val all = sections.flatMap { it.records }
        val status = statusOf(all)
        val followUps = all.count { it.needsFollowUp() }
        val subject = subjectOf(session)

        val out = StringBuilder()
        out.appendLine(organisation)
        out.appendLine("Parent Calling Report")
        out.appendLine()
        out.appendLine("Date: ${session.date.format(DATE_FORMAT)}")
        classLabel(session).takeIf { it.isNotEmpty() }?.let { label ->
            val section = session.section.clean()
            out.appendLine("Class: $label" + if (section != null) ", Section $section" else "")
        }
        purposeLabel(session)?.let { out.appendLine("Purpose: $it") }
        out.appendLine()
        out.appendLine(intro(status, subject))
        sections.forEach { section ->
            out.appendLine()
            section.title?.let {
                out.appendLine("$it (${count(section.records.size, "student", "students")}):")
                out.appendLine()
            }
            appendSection(out, subject, section.records)
        }

        out.appendLine()
        out.appendLine("Overall Status:")
        out.appendLine(status.label)
        if (followUps > 0) {
            out.appendLine(count(followUps, "parent requires", "parents require") + " follow-up.")
        }
        out.appendLine()
        out.appendLine("Prepared by:")
        out.append(session.teacher.clean()?.let { "$it, $organisation" } ?: organisation)
        return ProfessionalReport(out.toString(), status)
    }

    private fun appendSection(out: StringBuilder, subject: String, records: List<CallRecord>) {
        val total = records.size
        val contacted = records.count { it.isContacted() }
        val noAnswer = records.count { it.outcome == CallOutcome.NO_ANSWER }
        val willVisit = records.count { it.outcome == CallOutcome.WILL_VISIT }
        val callLater = records.count { it.outcome == CallOutcome.CALL_LATER }
        val noContact = records.count { it.noContact() }
        val notCalled = records.count { it.isPending() && !it.noContact() }
        val concerns = records.count { it.hasConcern() }
        val followUps = records.count { it.needsFollowUp() }
        val status = statusOf(records)

        out.appendLine("Total Parents: $total")
        out.appendLine("Parents Contacted: $contacted")
        if (noAnswer > 0) out.appendLine("Call Not Received: $noAnswer")
        if (willVisit > 0) out.appendLine("Will Come to Visit: $willVisit")
        if (callLater > 0) out.appendLine("Call Later: $callLater")
        if (notCalled > 0) out.appendLine("Not Yet Called: $notCalled")
        if (noContact > 0) out.appendLine("Contact Not Found: $noContact")
        if (concerns > 0) out.appendLine("Concerns Raised: $concerns")
        out.appendLine("Follow-ups Required: $followUps")
        out.appendLine()

        closing(status, subject, contacted, concerns, followUps, noAnswer + callLater, notCalled, noContact)
            .forEach { out.appendLine(it) }

        val listed = records.filterNot { it.isQuiet() }
        if (listed.isNotEmpty()) {
            out.appendLine()
            out.appendLine("Key Feedback & Follow-ups:")
            listed.forEachIndexed { i, r ->
                out.appendLine()
                out.appendLine("${i + 1}. ${r.displayName()}")
                out.appendLine(itemText(r))
            }
            val quiet = records.size - listed.size
            if (quiet > 0) {
                out.appendLine()
                out.appendLine("The remaining ${count(quiet, "parent was", "parents were")} contacted and informed, with no concerns raised.")
            }
        }
    }

    private fun statusOf(records: List<CallRecord>): ReportStatus {
        val total = records.size
        // A student is done once a call result or a teacher remark is recorded; unreached parents stay as follow-ups.
        val pending = records.count { it.isPending() }
        return when {
            total == 0 || pending == total -> ReportStatus.NOT_STARTED
            pending > 0 -> ReportStatus.PARTIALLY_COMPLETED
            records.any { it.hasConcern() } || records.any { it.needsFollowUp() } -> ReportStatus.COMPLETED
            else -> ReportStatus.COMPLETED_SUCCESSFULLY
        }
    }

    private fun intro(status: ReportStatus, subject: String) = when (status) {
        ReportStatus.NOT_STARTED -> "Parent calling regarding $subject has not started yet."
        ReportStatus.PARTIALLY_COMPLETED -> "Parent calling regarding $subject is in progress."
        ReportStatus.COMPLETED -> "The parent calling activity regarding $subject has been completed."
        ReportStatus.COMPLETED_SUCCESSFULLY ->
            "The parent calling activity regarding $subject has been completed successfully."
    }

    private fun closing(
        status: ReportStatus, subject: String, contacted: Int, concerns: Int,
        followUps: Int, unreached: Int, notCalled: Int, noContact: Int,
    ): List<String> {
        if (status == ReportStatus.COMPLETED_SUCCESSFULLY) {
            return listOf("All parents were informed regarding $subject and no concerns were reported.")
        }
        val lines = mutableListOf<String>()
        if (concerns == 0 && contacted > 0) lines += "No concerns were raised by the parents contacted."
        if (unreached > 0) lines += "Follow-up calls are required for the parents who could not be reached."
        if (notCalled > 0) lines += "${count(notCalled, "call is", "calls are")} yet to be made."
        if (noContact > 0) {
            lines += "${count(noContact, "parent", "parents")} could not be called as no valid contact number was found."
        }
        if (followUps == 0 && unreached == 0 && notCalled == 0) lines += "No follow-up action is required."
        return lines
    }

    private fun itemText(r: CallRecord): String {
        val remark = cleanRemark(r.remark)
        val followUp = if (r.needsFollowUp()) listOf(
            if (r.isContacted()) "Follow-up required." else "Follow-up call required.",
        ) else emptyList()
        val body = when {
            r.noContact() -> listOf("Contact number not found, so the parent could not be called.") + sentenceOrEmpty(remark)
            r.isPending() -> listOf("Call has not been made yet.")
            r.outcome == CallOutcome.NO_ANSWER -> listOf("Parent could not be reached.") + sentenceOrEmpty(remark)
            r.outcome == CallOutcome.WILL_VISIT -> listOf("Parent will come to visit the class.") + sentenceOrEmpty(remark)
            r.outcome == CallOutcome.CALL_LATER -> listOf("Parent is to be called again later.") + sentenceOrEmpty(remark)
            r.hasConcern() -> concernSentences(r, remark)
            remark != null -> listOf(sentence(remark))
            else -> listOf("Parent contacted and informed.")
        }
        return (body + followUp).joinToString(" ")
    }

    private fun concernSentences(r: CallRecord, remark: String?): List<String> {
        val label = joinNatural(r.concerns.sortedBy { it.ordinal }.map { it.phrase })
        if (remark == null) {
            return listOf(if (label.isEmpty()) "Parent raised a concern." else "Parent expressed concern regarding $label.")
        }
        val rewritten = CONCERN_LEAD.replace(remark, "Parent expressed concern regarding ")
        if (rewritten != remark) return listOf(sentence(rewritten))
        return if (label.isEmpty()) listOf(sentence(remark))
        else listOf("Parent expressed concern regarding $label.", sentence(remark))
    }

    private fun sentenceOrEmpty(remark: String?) = if (remark == null) emptyList() else listOf(sentence(remark))

    private fun cleanRemark(raw: String?): String? {
        val s = raw?.trim()?.replace(WHITESPACE, " ") ?: return null
        return if (s.isEmpty() || s.lowercase() in EMPTY_MARKERS) null else s
    }

    private fun sentence(text: String): String {
        val capitalised = SUBJECTS.replace(text) { it.value.lowercase().replaceFirstChar { c -> c.uppercase() } }
            .replaceFirstChar { it.uppercase() }
        return if (capitalised.last() in ".!?") capitalised else "$capitalised."
    }

    private fun subjectOf(session: CallingSession): String {
        val cl = classLabel(session).let { if (it.isEmpty()) "" else "$it " }
        return when (session.purpose) {
            CallingPurpose.EXAM_RESULT -> "the $cl${session.exam.clean()?.let { "$it " } ?: ""}examination results"
            CallingPurpose.ATTENDANCE -> "the attendance of ${cl}students"
            CallingPurpose.PARENT_MEETING -> "the ${cl}parent meeting"
            CallingPurpose.HOMEWORK -> "the homework and assignments of ${cl}students"
            CallingPurpose.PERFORMANCE -> "the academic performance of ${cl}students"
            CallingPurpose.GENERAL -> "general communication with ${cl}parents"
            CallingPurpose.OTHER -> session.customPurpose.clean() ?: "general communication with ${cl}parents"
        }
    }

    private fun purposeLabel(session: CallingSession): String? =
        if (session.purpose == CallingPurpose.OTHER) session.customPurpose.clean() else session.purpose.label

    private fun classLabel(session: CallingSession) =
        listOfNotNull(session.className.clean(), session.board.clean()).joinToString(" ")

    private fun CallRecord.isContacted() = outcome == CallOutcome.CONTACTED || outcome == CallOutcome.CONCERN_RAISED || outcome == CallOutcome.WILL_VISIT
    private fun CallRecord.hasConcern() = isContacted() && (outcome == CallOutcome.CONCERN_RAISED || concerns.isNotEmpty())
    private fun CallRecord.isQuiet() =
        outcome == CallOutcome.CONTACTED && !hasConcern() && !needsFollowUp() && cleanRemark(remark) == null
    private fun CallRecord.isPending() = outcome == CallOutcome.NOT_CALLED && cleanRemark(remark) == null
    private fun CallRecord.displayName() = studentName.clean() ?: "Student (name not recorded)"

    private fun String?.clean(): String? = this?.trim()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }

    private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    private fun joinNatural(items: List<String>) = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
        val WHITESPACE = Regex("\\s+")
        val EMPTY_MARKERS = setOf("null", "n/a", "na", "-", "none", "nil")
        val CONCERN_LEAD = Regex(
            "^(?:the\\s+)?parent\\s+(?:is\\s+|was\\s+)?(?:concerned|worried)\\s+(?:about|regarding|with|over)\\s+",
            RegexOption.IGNORE_CASE,
        )
        val SUBJECTS = Regex(
            "\\b(mathematics|maths|science|english|hindi|marathi|physics|chemistry|biology|geography|history|civics)\\b",
            RegexOption.IGNORE_CASE,
        )
    }
}
