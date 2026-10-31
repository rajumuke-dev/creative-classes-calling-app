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

State is kept in memory only (survives rotation, not process death).

## Build

Requires JDK 17+ and an Android SDK (set `sdk.dir` in `local.properties`).

```
./gradlew :report-core:test
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```
