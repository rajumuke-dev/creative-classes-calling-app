package com.creativeclasses.report

/** Dashboard summary of a calling session. A call is "attempted" once any outcome is recorded. */
data class SessionProgress(
    val total: Int,
    val attempted: Int,
    val contacted: Int,
    val concerns: Int,
    val noAnswer: Int,
    val callLater: Int,
    val notCalled: Int,
    val followUps: Int,
    /** Index of the first student not yet called, or null when every student has an outcome. */
    val resumeIndex: Int?,
) {
    val isFinished get() = total > 0 && notCalled == 0

    companion object {
        fun of(records: List<CallRecord>): SessionProgress {
            fun count(outcome: CallOutcome) = records.count { it.outcome == outcome }
            val notCalled = count(CallOutcome.NOT_CALLED)
            return SessionProgress(
                total = records.size,
                attempted = records.size - notCalled,
                contacted = count(CallOutcome.CONTACTED),
                concerns = count(CallOutcome.CONCERN_RAISED),
                noAnswer = count(CallOutcome.NO_ANSWER),
                callLater = count(CallOutcome.CALL_LATER),
                notCalled = notCalled,
                followUps = records.count { it.needsFollowUp() },
                resumeIndex = records.indexOfFirst { it.outcome == CallOutcome.NOT_CALLED }.takeIf { it >= 0 },
            )
        }
    }
}
