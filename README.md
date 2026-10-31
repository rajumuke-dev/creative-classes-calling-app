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
  share sheet), or **Save for Later** to keep the previewed text with the session and share it later in one tap
  from the dashboard (**Share Saved Report**). Nothing is ever sent automatically.

Sessions are saved on the phone after every change (private app storage, one JSON file per session). The
dashboard lists every session with called / total, the outcome breakdown and the next student to call;
"Continue calling" opens the list at the first student not yet called. The calling list can be filtered
(All / Not called / Follow-up). Call Not Received and Call Later count as called (attempted).
Student cards minimise once an outcome is recorded (Contacted, Will Come to Visit and Call Later after a
short pause; Concern Raised and Call Not Received via **Done ✓**) and show a coloured outcome badge, follow-up
flag and remark preview. Tap a card heading to reopen it.
Short synthesised sounds and a brief full-screen animation mark outcomes: confetti and a "ta-da" for Contacted,
a check-mark "visit booked" ripple and doorbell for Will Come to Visit, gentle rain and a falling tune for Call Not
Received, one soft note for the others. When the last student in the list being called gets an outcome (the By marks / Absent
selection when that filter is on, otherwise the whole class), a short dance plays (dancers bouncing to a party
tune under confetti); if it hasn't played yet, Share Report / Next play it before the preview opens, and a three-note chime when the report preview opens. They play as interface sounds and stay
silent when the phone is on silent/vibrate or system "Touch sounds" is off.

**Take a break** (works like YouTube's reminder): after 15 minutes of continuous work a reminder appears with a
glass filling with water, a soft "water drop" sound and a short health quote, asking you to drink water, stretch
and rest your eyes. Tap **OK** to start the next 15 minutes, or **Change reminder** to pick every 15, 30, 45 or 60
minutes (remembered). Time on parent calls counts as work; leaving the app for 30 minutes or closing it restarts
the timer, and a reminder that falls due during a call appears when you return.

**Marks** (one subject per session): on the setup screen you can upload the marks image and pick Lower than / Higher than plus a mark; calling then starts already filtered. The same controls are in the "By marks" filter. You can also type marks on
each card or use "Read marks from image" (on-device OCR, nothing is uploaded). Names are matched exactly, then
by same words in any order, then by a unique close spelling. "By marks" lists scorers below (lowest first) or above (highest first) the
mark; "Absent" lists AB. OCR can misread digits, so review the marks; the import notice lists
unmatched names and students left without marks. Marks are never added to the report.

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

The report covers only the students in the active filter. Students with no valid phone number are listed as "Contact Not Found" follow-ups. Remarks can be dictated with Speak.
