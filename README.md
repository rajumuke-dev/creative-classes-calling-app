# Creative Classes Calling App

Parent-calling app for teachers. This repo currently contains:

- `report-core` – pure Kotlin/JVM `ReportGenerator`: `CallingSession` + `CallRecord`s → `ProfessionalReport`
  (deterministic, uses only session data, outcomes, concern selections and teacher remarks; never invents facts).

Planned (not yet built): Android `app` module with Excel import, calling flow, report preview/edit, and
sharing via `Intent.ACTION_SEND` (`text/plain`). The report is never sent automatically.

```
./gradlew :report-core:test
```
