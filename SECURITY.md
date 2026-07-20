# Security baseline

## Local development

Keep all secret credentials outside version control. Android build-time values are read
from the ignored `local.properties` file and must have safe empty counterparts in
`local.defaults.properties` so clean checkouts still configure successfully.

Required only while the legacy map screen remains enabled: `GOOGLE_MAPS_API`.

The Firebase client configuration in `app/google-services.json` is public by design: its API
key identifies the Firebase project but is not authorization. The real file is still ignored
so each checkout supplies its own Firebase project; CI uses
`app/google-services.example.json`, which contains no usable project or key. The real key
must be limited to the required Firebase APIs. Firestore authorization is enforced by
Firebase Authentication and `firestore.rules`; App Check adds app/device attestation. Never
put a Firebase Admin SDK or service-account credential in the Android app.

The legacy Firebase account and every Google Maps key previously embedded in source code
must be treated as compromised and rotated before any external test. Use a separate
Maps/Places key restricted by Android application id, every allowed signing certificate and
the minimum required APIs. Client keys are extractable from an APK, so restrictions matter
more than obfuscation.

Firestore sync is off unless the ignored `local.properties` contains
`FIREBASE_SYNC_ENABLED=true`. Before setting it, enable anonymous authentication, deploy the
checked-in `firestore.rules`, configure App Check, and verify the project quotas. The rules
deny the legacy global `routes` collection and only allow an authenticated UID to access its
own `/users/{uid}/savedCommutes` documents.

Do not place service-account JSON files, `.env` files, raw locations, addresses or request
bodies containing coordinates in the repository or application logs.
