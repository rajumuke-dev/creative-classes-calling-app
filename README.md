# Creative Classes Calling App

Android app for teachers to call parents from an Excel list, record outcomes and share a professional report.

- `report-core` – pure Kotlin/JVM: `ReportGenerator` (session + call records → report), `XlsxReader`, `StudentImporter`.
  Deterministic; uses only session data, outcomes, concern selections and teacher remarks; never invents facts.
- `app` – Jetpack Compose UI: import `.xlsx` (needs a Student Name column; Phone/Class/Board/Exam columns optional) →
  confirm session (purpose, class, etc.) → record calls (dial, outcome, concerns, remark, follow-up) →
  **Share Report** → editable preview → **Share on WhatsApp** (`ACTION_SEND`, `text/plain`; falls back to the
  share sheet). Nothing is ever sent automatically.

State is kept in memory only (survives rotation, not process death).

## Build

Requires JDK 17+ and an Android SDK (set `sdk.dir` in `local.properties`).

```
./gradlew :report-core:test
./gradlew :app:assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```
