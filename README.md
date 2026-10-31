# Creative Classes Calling App

Android app for teachers to call parents from an Excel list, record outcomes and share a professional report.

- `report-core` – pure Kotlin/JVM: `ReportGenerator` (session + call records → report), `XlsxReader`, `StudentImporter`.
  Deterministic; uses only session data, outcomes, concern selections and teacher remarks; never invents facts.
- `app` – Jetpack Compose UI: import an `.xlsx`, pick the sheet to call (class and board are pre-filled from the sheet
  name, e.g. "8th CBSE ALL"; sheets need a "Name Of Student" header, with "Contact No 1/2", Publication/Medium and
  School Name columns read when present; title rows and repeated header rows are handled) →
  confirm session (purpose, class, etc.) → record calls (dial either parent contact, outcome, concerns, remark,
  follow-up; numbers that are not 10 digits are flagged invalid and can't be dialled) →
  **Share Report** → editable preview → **Share on WhatsApp** (`ACTION_SEND`, `text/plain`; falls back to the
  share sheet). Nothing is ever sent automatically.

Sessions are saved on the phone after every change (private app storage, one JSON file per session). The
dashboard lists every session with called / total, the outcome breakdown and the next student to call;
"Continue calling" opens the list at the first student not yet called. The calling list can be filtered
(All / Not called / Follow-up). Call Not Received and Call Later count as called (attempted).

**Marks** (one subject per session): on the setup screen you can upload the marks image and pick Lower than / Higher than plus a mark; calling then starts already filtered. The same controls are in the "By marks" filter. You can also type marks on
each card or use "Read marks from image" (on-device OCR, nothing is uploaded). Names are matched exactly, then
by same words in any order, then by a unique close spelling. "By marks" lists scorers below (lowest first) or above (highest first) the
mark; "Absent" lists AB. OCR can misread digits, so review the marks; the import notice lists
unmatched names and students left without marks. Marks are never added to the report.

## Build

Requires JDK 17+ and an Android SDK (set `sdk.dir` in `local.properties`).

```
./gradlew :report-core:test
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

The report covers only the students in the active filter. Students with no valid phone number are listed as "Contact Not Found" follow-ups. Remarks can be dictated with Speak.
