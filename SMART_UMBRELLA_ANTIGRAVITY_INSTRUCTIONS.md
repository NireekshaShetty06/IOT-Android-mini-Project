# Smart Umbrella -- Fix Instructions for Antigravity

## Your role

Act as a careful senior Android, ESP32 and Firebase engineer. Work on
the existing Smart Umbrella project in this repository. **Fix the
existing implementation; do not rebuild the app or redesign its UI.**

## Mandatory working rules

1.  Inspect the repository and understand the current architecture
    before editing.
2.  Treat `firmware/SmartUmbrella_Firebase_ESP32.ino` as the canonical
    ESP32 firmware.
3.  Do not use the duplicate root-level `Main Code` file as the source
    of truth. It contains duplicated/malformed code. Do not merge it
    into the canonical firmware.
4.  Preserve existing working hardware pin assignments, sensor logic,
    Android screens, navigation, visual design and features unless a
    change is required to fix a confirmed defect.
5.  Do not invent Firebase paths, credentials, hardware, pins or
    configuration fields. Verify them against the existing code and
    Firebase configuration.
6.  Never put Firebase service-account credentials, FCM server
    credentials, private keys or user passwords in firmware, Android
    source, or the repository.
7.  Do not make Firebase Realtime Database publicly readable/writable as
    a workaround.
8.  Make small, logically separated changes. After each change, build or
    run the relevant available checks.
9.  If a change requires a real Firebase project setting, certificate,
    secret, hardware test or user decision that is unavailable, do not
    fake it. Explain exactly what the user must provide or do.
10. Before editing, make a short plan. After editing, report changed
    files, fixes, build/test results and remaining manual steps.

## Priority 1 -- Correctness and reliability

### A. Android device online/offline status

Inspect `FirebaseSmartUmbrellaRepository.kt` and all code that parses
the Realtime Database status object.

-   Do not default a missing `deviceOnline` field to `true`.
-   Use `false` when the field is absent or invalid.
-   Do not default a missing `lastUpdated` timestamp to
    `System.currentTimeMillis()`.
-   Use an explicit invalid/missing value (for example `0L`) and treat
    it as stale/offline.
-   Determine online status using both the reported `deviceOnline` value
    and the age of `lastUpdated`.
-   Define one clearly named heartbeat timeout constant. Choose a
    conservative value based on the ESP32 upload interval and document
    it.
-   Handle missing, malformed and stale values without crashing.
-   Ensure the UI displays offline/unknown state honestly; never show an
    old reading as current.

### B. Firebase authentication and database rules

Inspect the Android Firebase dependencies, repository and Firebase
configuration.

-   Determine whether Android signs in to Firebase Authentication.
-   If database rules require authentication and Android currently has
    no sign-in flow, implement the smallest appropriate Firebase
    Authentication integration that fits the existing app architecture.
    Do not silently weaken database rules.
-   Keep credentials out of source control. Use the project's intended
    secure configuration mechanism.
-   Do not claim authentication works unless it can be verified from
    code/build or a documented manual test.
-   Do not change production database rules to public access.

### C. ESP32 responsiveness

Inspect the canonical firmware before changing it.

-   Preserve the existing pin assignments and sensor behavior.
-   Preserve the rain sensor hysteresis and verify the existing
    active-LOW digital output assumption against the current code/module
    documentation.
-   Keep DHT22 sampling at its intended interval and retain sensor
    validity/error information.
-   Keep buzzer and button handling responsive.
-   Identify synchronous HTTP, Firebase authentication and NTP
    operations that block the main loop.
-   Move network operations into a separate FreeRTOS task or another
    safe non-blocking design. Use a queue or protected shared state
    where appropriate.
-   Do not allow multiple tasks to use the same
    `WiFiClient`/`HTTPClient` instance concurrently.
-   Add connection timeouts and bounded retry/backoff. Avoid endless
    blocking loops.
-   Ensure sensor sampling, buzzer cadence and button handling continue
    while Wi-Fi/Firebase is unavailable.
-   Test or reason through Wi-Fi loss, reconnection, Firebase timeout
    and invalid sensor readings.
-   Do not change the token JSON parsing offsets unless you first
    demonstrate they are wrong. The current offsets for `idToken` and
    `expiresIn` were reviewed and are correct.

### D. Firebase status writes

-   Inspect the current RTDB paths and JSON structure before modifying
    writes.
-   The current firmware writes the `/smartUmbrella/status` object with
    PUT. Remember that PUT replaces the entire object at that path.
-   If only selected fields need updating, use PATCH or deliberately
    construct the complete status object. Do not accidentally delete
    sibling fields.
-   Update `lastUpdated` consistently on successful telemetry writes.
-   Do not report the device online merely because Wi-Fi is connected;
    online status must be based on a recent successful device heartbeat.
-   Ensure failed writes do not update the last-successful-upload
    timestamp.

## Priority 2 -- Complete the existing integrations

### E. Remote configuration

Inspect `DeviceConfig.kt` and the ESP32 firmware.

-   The Android settings currently appear to be local-only while the
    ESP32 uses hardcoded rain thresholds and buzzer settings.
-   Implement a shared RTDB configuration path only after verifying the
    current project structure. Prefer `/smartUmbrella/config` if it does
    not conflict with existing paths.
-   Define and document matching field names and types on Android and
    ESP32.
-   ESP32 should fetch configuration periodically and on reconnection,
    validate ranges and types, and retain safe compiled defaults when
    Firebase is unavailable.
-   Apply new settings safely without rebooting unless the
    hardware/software design requires it.
-   Do not let malformed remote values disable safety or cause unstable
    threshold behavior.
-   Make it clear in the UI whether a setting was saved remotely or only
    changed locally.

### F. Persistent history

-   Inspect `FirebaseSmartUmbrellaRepository.kt` and
    `DashboardViewModel.kt`.
-   History currently appears to be held in an in-memory
    `MutableStateFlow<List<SensorHistoryItem>>`.
-   Persist history using the existing Firebase architecture or Room,
    choosing the least disruptive option.
-   Avoid unbounded downloads. Use a sensible query limit and ordering.
-   Handle duplicate timestamps/records and loading/error states.
-   Preserve the existing history UI and data model where possible.

### G. Background rain notifications

-   Inspect `SmartUmbrellaFcmService.kt`, notification setup and Android
    manifest.
-   The ESP32 currently writes to Realtime Database; it does not
    directly send FCM.
-   Implement or document a secure server-side flow: RTDB status change
    -\> Firebase Cloud Function (or another trusted backend) -\> FCM -\>
    Android app.
-   The Android app must register/update its FCM token securely and
    handle notification permissions/channels correctly for the target
    Android version.
-   Do not embed FCM server credentials or service-account keys in ESP32
    or Android.
-   Avoid duplicate rain alerts for repeated database updates. Trigger
    on a genuine state transition.
-   Do not rely only on a UI-collected Flow for notifications when the
    app is open/closed.
-   If backend deployment credentials are unavailable, implement only
    the client-side pieces that can be safely completed and provide
    exact deployment instructions. Do not claim end-to-end notifications
    are verified.

## Priority 3 -- Security and maintainability

### H. HTTPS certificate verification

-   The firmware currently uses `client.setInsecure()`, which disables
    TLS certificate verification.
-   Replace it with proper root CA certificate verification for the
    Firebase endpoint, using a maintainable certificate-update approach.
-   Do not invent or hardcode an unverified certificate.
-   If a valid CA certificate cannot be obtained/verified in this
    environment, leave the limitation clearly documented rather than
    pretending TLS is secure.

### I. Sensor data validity

-   Do not silently reuse old DHT readings after a failed read as if
    they were current.
-   Expose sensor validity and/or reading age to Android.
-   Keep rain analog and digital sensor behavior consistent with the
    actual module wiring and polarity.
-   Confirm analog output never exceeds the ESP32 ADC input limit (3.3 V
    maximum). Do not change wiring assumptions without evidence.
-   Do not alter RGB LED common-anode/common-cathode logic without
    checking the actual LED type and wiring.

### J. Repository structure and documentation

-   Keep the canonical firmware in
    `firmware/SmartUmbrella_Firebase_ESP32.ino`.
-   Do not treat the malformed duplicate root `Main Code` file as
    canonical. Recommend archiving/removing it only after confirming it
    is not referenced by the project workflow.
-   Keep standalone legacy sensor sketches clearly separated from the
    unified firmware.
-   Update the root README with verified setup instructions: hardware
    connections, firmware libraries/board setup, Firebase configuration
    and rules, Android setup, build/run steps, and known limitations.
-   Do not invent screenshots, test results or wiring details.

## Required workflow

1.  Inspect the relevant files and report the exact defects found before
    editing.
2.  Make a prioritized implementation plan.
3.  Fix Priority 1 first. Do not start optional enhancements before core
    correctness is stable.
4.  Build the Android app using the repository's existing Gradle wrapper
    and available SDK. Report exact build result.
5.  Check firmware for compile errors if the toolchain is available. If
    it is not available, state that firmware compilation was not run.
6.  Review the final diff for accidental UI, pin, credential or
    configuration changes.
7.  Provide a concise final report:
    -   Confirmed issues fixed
    -   Files changed
    -   Build/tests run and their actual results
    -   Firebase console/backend steps still required
    -   Hardware tests the user must perform

## Important acceptance criteria

-   Missing `deviceOnline` or `lastUpdated` never makes the device
    appear online.
-   A stale heartbeat eventually makes the app show offline.
-   Sensor sampling and local buzzer/button behavior are not held
    hostage by slow Firebase/network calls.
-   A Firebase write does not unintentionally erase unrelated status
    fields.
-   Android settings do not pretend to control the physical umbrella
    unless the ESP32 receives and applies them.
-   Notifications are not described as background-capable until the
    server-side FCM path is configured and tested.
-   Existing UI and working hardware wiring remain intact.

**Start by inspecting the repository. Do not immediately rewrite
files.**
