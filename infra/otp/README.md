# Local OpenTripPlanner

This stack pins OpenTripPlanner 2.9.0 and exposes its GTFS GraphQL endpoint at
`http://localhost:8080/otp/gtfs/v1`. The container combines the AUCORSA and Renfe Media
Distancia GTFS feeds with one OpenStreetMap PBF in `infra/otp/data`; those generated
inputs and `graph.obj` are ignored by Git. Stable feed IDs are assigned in
`config/build-config.json` so AUCORSA and Renfe agencies remain distinguishable.

The official AUCORSA snapshot is structurally valid but its published calendar ended on
2026-03-31. For this MVP, the source remains immutable and an explicitly labelled derived
feed repeats the normal weekday/Saturday/Sunday service through 2027-07-19. This supports
current-date route planning under the agreed local assumption; it is not an official
extension and must not be described as real-time service.

## Prepare and build

Create the derived GTFS as documented in `data/README.md`, create the Córdoba-area extract
from the downloaded Andalucía PBF, download the public Renfe feed, then prepare OTP:

```powershell
.\infra\osm\extract-cordoba.ps1
.\infra\otp\download-renfe-gtfs.ps1
.\infra\otp\prepare-data.ps1 -OsmPath C:\path\to\cordoba.osm.pbf
.\infra\otp\build-graph.ps1
.\infra\otp\start.ps1
```

No API key is required by OTP or by the public Renfe download. Building the graph is intentionally left gated on the OSM
extract so a large regional download is not pulled into the workspace implicitly.

`infra/backend/start.ps1` derives the combined data version from both ZIP checksums and
uses the earliest GTFS service end date as the activation limit. For a manual backend
start, configure equivalent values:

```powershell
$env:OTP_BASE_URL = "http://localhost:8080/otp/gtfs/v1"
$env:TRANSIT_DATA_VERSION = "cordoba-aucorsa-<sha>-renfe-<sha>"
$env:TRANSIT_DATA_EXPIRES_ON = "<earliest-feed-end-date>"
```

No expired-data override is needed. Refresh the Renfe GTFS before rebuilding the graph;
its publication window is much shorter than the local AUCORSA projection. The backend
will refuse to activate the combined snapshot after either feed expires. If a new official
GTFS is uploaded, validate it, rebuild `graph.obj`, and restart OTP.

Stop the local service with `.\infra\otp\stop.ps1`.
