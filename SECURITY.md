# Security baseline

## Local development

Keep all usable credentials outside version control. Android build-time values are read
from the ignored `local.properties` file and must have safe empty counterparts in
`local.defaults.properties` so clean checkouts still configure successfully.

Required only while the legacy map screen remains enabled: `GOOGLE_MAPS_API`.

The legacy Firebase account and every Google Maps key previously embedded in source code
must be treated as compromised and rotated before any external test. New Google keys must
be restricted by Android application id, signing certificate and the minimum required APIs.

Do not place service-account JSON files, `.env` files, raw locations, addresses or request
bodies containing coordinates in the repository or application logs.
