# Route Planning API

FastAPI facade for the Android MVP. The mobile app consumes this API instead of coupling
itself to OpenTripPlanner. Exact user addresses and journey request bodies must not be
persisted or written to logs.

## Local development

Use Python 3.12 and install the editable development dependencies in an isolated virtual
environment:

```bash
python3 -m venv .venv
.venv/bin/pip install -e '.[dev]'
.venv/bin/pytest
.venv/bin/uvicorn route_planning_api.main:app --reload
```

Until validated AUCORSA and Renfe GTFS snapshots and an OTP instance are active,
`POST /v1/journeys/search` deliberately returns the structured `DATA_NOT_READY` error.
When configured, the backend calls OTP's GTFS GraphQL `planConnection` query and maps the
result to the stable mobile contract; the Android app never consumes OTP directly.
The request field `mode` accepts `TRANSIT` (the backwards-compatible default), `BICYCLE`
or `WALK`. `TRANSIT` leaves all OTP public-transport modes enabled, allowing complete
walking, AUCORSA bus and Renfe rail combinations without exposing `RAIL` as a separate
top-level choice. Bicycle and walking searches are sent to OTP as direct journeys over the same
OpenStreetMap graph; no separate cycling or pedestrian tile source is required for route
calculation.

The adapter requests up to 15 OTP alternatives. The `FASTEST` profile orders departure
searches by actual arrival time, not only by the itinerary duration after it starts, so an
earlier bus is not displaced by a shorter train journey that departs and arrives later.

Each journey leg exposes its operator, rounded distance and, for bus and rail legs, the
public headsign and exact number of stops after boarding. The stop count is derived from OTP `stopCalls`
(which includes boarding and alighting), not estimated from route geometry.

Transit alternatives keep transfers and fare validations separate. When OTP chains two
consecutive legs of the same AUCORSA line without counting a transfer (for example, at the
end of an expedition in Colón Norte), the response includes a structured `fare_notices`
entry so the client can show that another ticket or validation is required.
The client counts separate AUCORSA and Renfe operators as separate tickets while keeping
that ticket count independent from the number of physical transfers.

## GTFS activation gate

The official AUCORSA dataset is published through the Spanish National Access Point. Never
commit its downloaded ZIP or any account credentials.

Inspect a downloaded snapshot before using it:

```bash
.venv/bin/gtfs-inspect path/to/aucorsa.gtfs.zip
```

The command exits non-zero for incomplete, unsafe or expired feeds and reports a SHA-256
checksum that will become the immutable snapshot identifier.

The official snapshot received for development is catalogued at
`../data/snapshots/aucorsa-20260225.json`. Its calendar ended on 2026-03-31, so it remains
immutable and the normal runtime gate rejects it. The MVP's separate, explicitly labelled
weekly projection is catalogued at
`../data/snapshots/aucorsa-20260225-assumed-20260719.json`. See
`../data/README.md` for generation and `../infra/otp/README.md` for graph build instructions.

## OTP runtime configuration

The planner activates only when all of these values are present and the expiry date has
not passed:

- `OTP_BASE_URL`, normally `http://localhost:8080/otp/gtfs/v1`
- `TRANSIT_DATA_VERSION`, an immutable snapshot identifier
- `TRANSIT_DATA_EXPIRES_ON`, an ISO date such as `2026-12-31`

`OTP_TIMEOUT_SECONDS` defaults to 15. `ALLOW_EXPIRED_TRANSIT_DATA=true` exists only to test
historical snapshots locally and must not be used in a deployed environment.
