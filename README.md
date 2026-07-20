# Córdoba Connect

Android-first MVP for recurring urban journeys in Córdoba. The repository contains the
new Compose client, the preserved legacy prototype and a FastAPI facade prepared for
validated AUCORSA GTFS data and OpenTripPlanner.

## Android

Requirements:

- Android Studio Quail 2 (2026.1.2) or newer, with JDK 21
- Android SDK Platform 37.1 and Build Tools 36.0.0
- A local `GOOGLE_MAPS_API` entry in the ignored `local.properties` file for the Google
  Places address selector and the preserved legacy Google Maps screen
- A real `app/google-services.json` downloaded from your Firebase Android app when
  Firestore synchronization is enabled. This file is ignored by Git; CI copies the safe
  `app/google-services.example.json` placeholder because cloud sync is disabled in CI.

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
An optional local-first Firestore layer can synchronize those Room records after its
security prerequisites have been configured. The old XML/Google Maps prototype remains
reachable from the MVP home screen while its features are replaced; its unsafe global
Firestore writer is disabled.

## Optional Firestore synchronization

Firestore synchronization is compiled into the app but is disabled by default. Room stays
the source shown by the UI and accepts saves while offline. When synchronization is enabled,
the app signs in anonymously, merges existing local and remote journeys, and listens under:

```text
/users/{firebaseAuthUid}/savedCommutes/{journeyId}
```

The old global `/routes` collection is neither read nor migrated. Anonymous authentication
keeps a stable UID while the app installation exists, but it does not recover data after an
uninstall and does not synchronize between different devices. That requires adding a real
sign-in provider later and linking the anonymous account to it.

Before enabling synchronization:

1. Use a Firebase project you control. Treat the legacy project/account as compromised,
   rotate any old unrestricted keys, register the Android package
   `com.example.routeplanning`, and place the freshly downloaded configuration at
   `app/google-services.json`. The real file remains local and ignored by Git.
2. In Firebase Authentication, enable the **Anonymous** sign-in provider.
3. Create a Firestore database, then publish the repository rules before allowing client
   access:

   ```powershell
   firebase login
   firebase deploy --only firestore:rules --project YOUR_PROJECT_ID
   ```

4. Register the Android app in Firebase App Check. Debug builds use the App Check debug
   provider; copy the debug token from Logcat into the Firebase console. Release builds use
   Play Integrity and require the release SHA-256 fingerprint. Observe App Check metrics
   before enabling Firestore enforcement.
5. Add this only to the ignored `local.properties` file:

   ```properties
   FIREBASE_SYNC_ENABLED=true
   ```

Saved journeys contain address labels and coordinates. Enabling this option uploads that
personal location data to your Firebase project, so a production release also needs a clear
privacy notice, retention/deletion policy and an account-based delete/export flow.

The Firebase key inside `google-services.json` cannot be hidden from a compiled Android
client and is not what authorizes Firestore access. The real config is nevertheless kept
out of this repository to isolate contributors and Firebase projects. Security comes from
Firebase Authentication, `firestore.rules`, App Check and quotas. In Google Cloud Console,
keep the Firebase key restricted to the required Firebase APIs. Use a separate Maps/Places
key and restrict it to the Android package, every allowed signing SHA-1, Maps SDK for
Android and Places API. Never ship or commit a service-account key.

## Run the current vertical slice

### Local prerequisites

Docker Desktop with Docker Compose is required for the OpenStreetMap extraction and the
OpenTripPlanner infrastructure. Start Docker Desktop before running any script under
`infra\osm` or `infra\otp`. The FastAPI backend itself runs with Python 3.12, but it needs
the local OpenTripPlanner container to calculate routes.

From PowerShell in the repository root, create the backend environment once:

```powershell
py -3.12 -m venv backend\.venv-win
.\backend\.venv-win\Scripts\python.exe -m pip install -e "backend[dev]"
```

### Prepare the routing infrastructure

The preparation scripts are only needed for a first setup or when the OSM/GTFS inputs
change. Place the Andalucía `.osm.pbf` source in the repository root (the extraction
script defaults to `andalucia-260717.osm.pbf`) and make sure the projected AUCORSA GTFS
described in [data/README.md](data/README.md) is available. Then run:

```powershell
.\infra\osm\extract-cordoba.ps1
.\infra\otp\download-renfe-gtfs.ps1
.\infra\otp\prepare-data.ps1 -OsmPath .\data\raw\osm\cordoba.osm.pbf
.\infra\otp\build-graph.ps1
```

These commands create the Córdoba OSM extract, download the current Renfe GTFS, copy the
routing inputs into `infra\otp\data`, and build the OTP graph in Docker.

### Start the backend and infrastructure

With Docker Desktop running, open two PowerShell terminals in the repository root. Start
OpenTripPlanner first:

```powershell
.\infra\otp\start.ps1
```

Then start FastAPI in the second terminal:

```powershell
.\infra\backend\start.ps1
```

Then run the `app` configuration from Android Studio on an emulator image that includes
Google APIs. The Compose launcher can use the device location or a Google Places address
as origin, opens Google Places for the destination, offers native Android date/time
pickers, lets the user choose public transport, bicycle or walking, calls FastAPI through
`http://10.0.2.2:8000`, and displays OTP alternatives. `GOOGLE_MAPS_API` must be present
only in the ignored `local.properties`.

Stop OpenTripPlanner when finished:

```powershell
.\infra\otp\stop.ps1
```

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
- Optional but disabled until configured: local-first Firestore synchronization with
  per-UID rules, anonymous authentication and App Check.
- Not connected yet: PostGIS, alerts, FCM and cloud deployment.
- Real-time urban ETA is intentionally not promised because no reusable public AUCORSA
  real-time feed has been confirmed.

Review [SECURITY.md](SECURITY.md) before any external test.
