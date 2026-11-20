# 📞 Creative Classes Calling App

[![License: MIT](https://img.shields.io/github/license/rajumuke-dev/creative-classes-calling-app)](LICENSE) [![Platform](https://img.shields.io/badge/platform-Android-lightgrey)](#) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md) [![Stars](https://img.shields.io/github/stars/rajumuke-dev/creative-classes-calling-app?style=social)](https://github.com/rajumuke-dev/creative-classes-calling-app)

Android app for teachers to call parents from an Excel list, record outcomes and share a professional report.

- `report-core` – pure Kotlin/JVM: `ReportGenerator` (session + call records → report), `XlsxReader`, `StudentImporter`.
  Deterministic; uses only session data, outcomes, concern selections and teacher remarks; never invents facts.
- `app` – Jetpack Compose UI: import an `.xlsx`, pick the sheet to call (class and board are pre-filled from the sheet
  name, e.g. "8th CBSE ALL"; sheets need a "Name Of Student" header, with "Contact No 1/2", Publication/Medium and
  School Name columns read when present; title rows and repeated header rows are handled) →
  confirm session (purpose, class, etc.) → record calls (dial either parent contact, outcome, concerns, remark,
  follow-up; numbers that are not 10 digits are flagged invalid and can't be dialled) →
  **Share Report** → editable preview → **Share on WhatsApp** (`ACTION_SEND`, `text/plain`; falls back to the
  share sheet), or **Save for Later** to keep the previewed text with the session and share it later in one tap
  from the dashboard. Every dashboard card has **Share Report**: it shares the saved report, or else a fresh report
  from the latest calls. Recording or changing a call afterwards discards the saved report, so an outdated report is
  never shared. Nothing is ever sent automatically.
  **Share Excel** (report preview) and **Excel** (each dashboard card) share `<session name>.xlsx` through the share
  sheet (WhatsApp, Drive, Gmail, ...). It is rebuilt from the saved session on every tap, so it always has the latest
  calls: a "Calls" sheet with every student (marks, call status, concerns, remarks, follow-up, contacts) under a
  title, session details and summary, with a frozen header and name column, filters, striped rows and colour-coded
  status; one sheet per marks list (e.g. "Lower than 20", "Higher than 35") with the same table for just those
  students; and a "Report" sheet with the report text laid out with headings and bold values. All sheets print to fit
  the page width with a page-number footer.

Sessions are saved on the phone after every change (private app storage, one JSON file per session). The
dashboard lists every session with called / total, the outcome breakdown and the next student to call. Tapping any
count (called, an outcome tile or Follow-ups) opens a swipe-up sheet of those students with their marks: chips switch
between counts, students can be sorted by marks, and tapping a student shows their call (status, concerns, remark,
contact). Nothing can be changed there; calls are recorded on the calling screen.
"Continue calling" reopens the filter that was active (e.g. By marks) at the first student in it not yet called; the
dashboard's "Next:" name follows the same list. Sessions
from older app versions keep their calls, reopen on By marks if a marks filter was set, and drop their saved report. The calling list can be filtered
(All / Not called / Follow-up). Call Not Received and Call Later count as called (attempted).
Student cards minimise once an outcome is recorded (Contacted, Will Come to Visit and Call Later after a
short pause; Concern Raised and Call Not Received via **Done ✓**) and show a coloured outcome badge, follow-up
flag and remark preview. Tap a card heading to reopen it.
For Call Not Received, **Send text message to parent** opens the SMS app with a pre-filled message (nothing is
sent until you press Send). It comes from Creative Classes and tells the parent when the teacher is available
(e.g. "Priya Sharma is available on Monday from 4 PM to 7 PM. Please do visit the class."), using the Teacher and
"Teacher available" fields of the New Calling Session form; both carry over from the most recent session. If a
session has no availability yet, Send text message asks for it first; tap the availability line to change it.
When the student has an imported mark, the message also states it with the Subject and Exam fields
(e.g. "Asha Rao scored 19 marks in History (Unit Test 1)." or "Asha Rao was absent for History (Unit Test 1).").
Short synthesised sounds and a brief full-screen animation mark outcomes: confetti and a "ta-da" for Contacted,
a check-mark "visit booked" ripple and doorbell for Will Come to Visit, gentle rain and a falling tune for Call Not
Received, one soft note for the others. When the last student in the list being called gets an outcome (the By marks / Absent
selection when that filter is on, otherwise the whole class), a short dance plays (dancers bouncing to a party
tune under confetti); if it hasn't played yet, Share Report / Next play it before the preview opens, and a three-note chime when the report preview opens. They play at the
phone's media volume, like reels (the volume buttons control it inside the app; turn it down to mute the sounds).
**4D cinema effects:** every sound is also felt — the phone vibrates on each note's beat (a strong impact pulse for
Contacted, a "ding-dong" for Visit, soft long buzzes for Call Not Received, one pulse per dance beat), so reactions
are still felt on vibrate. Big moments add a low "boom" under the sound, a short echo, a flash of light and a brief
camera shake; Call Not Received dims the screen edges instead. Vibration follows the system haptic setting and the
shake follows the system animation setting.

**Take a break** (works like YouTube's reminder): after 15 minutes of continuous work a reminder appears with a
glass filling with water, a soft "water drop" sound and a short health quote, asking you to drink water, stretch
and rest your eyes. Tap **OK** to start the next 15 minutes, **Snooze 5 min** to be reminded shortly, or **Change** to pick
every 15, 30, 45 or 60 minutes or switch reminders off (remembered; also under "Break reminder" on the dashboard).
The reminder never interrupts typing: it waits until you leave the text box. Time on parent calls counts as work; leaving the app for 30 minutes or closing it restarts
the timer, and a reminder that falls due during a call appears when you return.

**Marks** (one subject per session): marks are used only when the Calling Purpose (chosen first on the setup screen) is Exam Result or Performance; other purposes such as Parent Meeting hide the marks image, the Marks button, the By marks / Absent filters and the marks field. For a marks purpose you can upload the marks image on the setup screen; calling then opens on "By marks", where you pick Lower than / Higher than and the mark. Once uploaded, the Excel and the marks image show as cards (file name, sheet and student count; image thumbnail and rows read) with **Change**; tap the image card to view it full screen with pinch to zoom. A copy of the image is kept with the session (deleted with it), so the card is also under Marks after reopening. The image can also be uploaded from the Marks button while calling. You can also type marks on
each card or use "Read marks from image" (on-device OCR, nothing is uploaded). Names are matched exactly, then
by same words in any order, then by a unique close spelling, then by the only student whose name contains all the
marklist name's words (e.g. surname only), then by the same letters ignoring spaces (e.g. "Harpreetsingh" =
"Harpreet Singh"). Any number of side-by-side tables (e.g. three batches) are split into separate
rows, even when the OCR misses a serial number; title, date and subject lines are skipped. A marks cell the OCR
skipped or misread (e.g. "9" read as "o") is read again on its own, in every table. Each table's marks column is also re-read
enlarged twice; when both readings agree they replace the page reading (fixes "19" read as "10"). A cell that stays
unreadable is shown as "-" so you can type the mark. "By marks" lists scorers below (lowest first) or above (highest first) the
mark; "Absent" lists AB. Lower than and Higher than each keep their own mark, so after the low-marks calls you can switch
to Higher than in the same session for a second round; switching back restores the other mark. On By marks and Absent the
top counter counts that list (e.g. "Lower than 20: 1 / 12"). Students without marks are summed up in a tap-to-open
"not listed" line. OCR can misread digits, so review the marks; the import notice lists
unmatched names and students left without marks. **Search names** (the always-visible 🔍 bar on the calling screen, the import notice and Marks) shows each student's Excel phone numbers and finds a name,
or part of it, in both the student list and the rows read from the image (kept with the session), so a misspelt name
can be told from a missing one; the student's mark can be typed right in the results. Search ignores word order and
extra middle names, and also finds names that sound the same (Shrawani / Shravani, Zeeshan / Jishan) or have a small
typo. Image rows that matched no student are marked "Not matched"; **Use** gives that row's mark to a student in the
list (e.g. a misspelt name), or **Add as new student** (optional parent phone) adds them, so they join the Lower than /
Higher than list being called. Marks are never added to the report. **Compare with marks image** shows the zoomable image (pinch, drag, double tap) above the full list, or beside it in landscape, to check names one by one.

## Build

Requires JDK 17+ and an Android SDK (set `sdk.dir` in `local.properties`).

```
./gradlew :report-core:test
./gradlew :app:assembleRelease # app/build/outputs/apk/release/app-arm64-v8a-release.apk (install this on phones)
./gradlew :app:assembleDebug   # debuggable build for development
```

Release builds are shrunk and optimised (R8 + resource shrinking) and split per phone CPU type, because the
bundled OCR engine alone is ~11 MB per CPU type. Use `app-arm64-v8a-release.apk` for almost all phones,
`app-armeabi-v7a-release.apk` for old 32-bit phones, or `app-universal-release.apk` if unsure. Measured on the
emulator against the debug build: APK 54 MB → 14.4 MB, cold start ~0.45 s → ~0.18 s, memory ~103 MB → ~44 MB.
Release builds are signed with the local debug key so they install over the existing app and keep its data; use a
proper release key before publishing to a store.

The report covers only the students in the active filter. Every student in it is listed by name with their result, including parents simply contacted. WhatsApp cuts one message at 4,096 characters, so a longer report is shared as one PDF with the full report (nothing trimmed) and the summary as the message caption. On By marks with both a Lower than and a Higher than list set,
one report has a section (counts and feedback) for each list, with one overall status. Students with no valid phone number are listed as "Contact Not Found" follow-ups. Remarks can be dictated with Speak. The overall status is "Completed" once every student has a call result (including Not Received or Call Later, which stay as follow-ups) or a remark; it is "Partially Completed" only while some students have neither.

## Contributing and security
Fork, branch, and open a Pull Request; see [CONTRIBUTING.md](CONTRIBUTING.md). Report vulnerabilities privately per [SECURITY.md](SECURITY.md). Licensed under [MIT](LICENSE).
