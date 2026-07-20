# RoutePlanning

Android-first MVP for recurring urban journeys in Córdoba. The repository contains the
new Compose client, the preserved legacy prototype and a FastAPI facade prepared for
validated AUCORSA GTFS data and OpenTripPlanner.

## Android

Requirements:

- Android Studio Quail 2 (2026.1.2) or newer, with JDK 21
- Android SDK Platform 37.1 and Build Tools 36.0.0
- A local `GOOGLE_MAPS_API` entry in the ignored `local.properties` file for the Google
  Places address selector and the preserved legacy Google Maps screen

The app compiles against API 37.1 and targets API 36, while keeping `minSdk 26`.
It therefore remains installable on Android 8.0 and newer, including Android 13.

On Windows, use Android Studio's JDK if the system Java version is newer than the Gradle
version supports:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' `
  -classpath 'gradle\wrapper\gradle-wrapper.jar' `
  org.gradle.wrapper.GradleWrapperMain testDebugUnitTest lintDebug assembleDebug
```

The launcher is now `MvpActivity`. It uses Compose and stores recurring journeys in a
SQLCipher-encrypted Room database whose random passphrase is wrapped by Android Keystore.
The old XML/Google Maps prototype remains reachable from the MVP home screen while its
features are replaced.

## Run the current vertical slice

With Docker Desktop running, open two PowerShell terminals in the repository root:

```powershell
.\infra\otp\start.ps1
```

```powershell
.\infra\backend\start.ps1
```

Then run the `app` configuration from Android Studio on an emulator image that includes
Google APIs. The Compose launcher can use the device location or a Google Places address
as origin, opens Google Places for the destination, offers native Android date/time
pickers, lets the user choose public transport, bicycle or walking, calls FastAPI through
`http://10.0.2.2:8000`, and displays OTP alternatives. `GOOGLE_MAPS_API` must be present
only in the ignored `local.properties`.

## Backend

See [backend/README.md](backend/README.md). The API and GTFS validation suite can be run
without cloud credentials. The received AUCORSA snapshot is catalogued and kept outside
Git. The pinned local OTP stack is documented in [infra/otp/README.md](infra/otp/README.md).

## Current boundary

- Available: Compose shell, Google Places address search, encrypted saved commutes,
  current location as journey origin, immediate or scheduled departure using native
  Android date/time dialogs,
  public-transport routing that can combine walking, AUCORSA buses and Renfe Proximidad,
  direct-bicycle and direct-walking routing, Google Maps route rendering
  with distinct walking, bus, rail and bicycle segments, active-mobility recommendations when
  they beat transit, reopening a saved commute with a freshly calculated map, separate
  ticket/transfer indications, bus headsigns, exact stop counts, walking distance and wait
  guidance, actionable empty/offline/location/out-of-area states, a stable journey-search
  contract, GTFS activation gates
  and an Android HTTP client restricted to HTTPS except for the local emulator host in
  debug builds.
- Available locally: a pinned OTP 2.9.0 container stack, the Córdoba OpenStreetMap extract,
  a combined AUCORSA and official Renfe Media Distancia graph and the FastAPI GraphQL
  adapter. Current-date bus planning uses an explicitly labelled weekly projection of the
  last official AUCORSA schedule; rail planning uses the latest downloaded Renfe schedule.
- Not connected yet: PostGIS, alerts, FCM and cloud deployment.
- Real-time urban ETA is intentionally not promised because no reusable public AUCORSA
  real-time feed has been confirmed.

Review [SECURITY.md](SECURITY.md) before any external test.
