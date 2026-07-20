# Transit data

`raw/` is intentionally ignored because source GTFS archives are runtime inputs rather
than source code. Keep local downloads there and identify them by their SHA-256 checksum.

Tracked records in `snapshots/` contain provenance and quality results without redistributing
the original archive.

The official `aucorsa-20260225` snapshot ends on 2026-03-31. It is preserved byte-for-byte
and remains marked `development_expired`. For the MVP, a separate derived archive repeats
the ordinary service observed on 2026-03-09 (weekday), 2026-03-14 (Saturday) and 2026-03-15
(Sunday). Its metadata is tracked as `assumed_current`: this is a product assumption based
on local knowledge, not a new publication by AUCORSA and not real-time data.

Generate the current local projection without modifying the official ZIP:

```powershell
& 'backend\.venv-win\Scripts\python.exe' -m route_planning_api.gtfs.project_cli `
  'data\raw\aucorsa\aucorsa-20260225.gtfs.zip' `
  'data\raw\aucorsa\aucorsa-20260225-assumed-20260719.gtfs.zip' `
  --start 2026-04-01 --end 2027-07-19 `
  --weekday-reference 2026-03-09 `
  --saturday-reference 2026-03-14 `
  --sunday-reference 2026-03-15
```

The output is intentionally ignored. Replace it with a newly validated official feed as
soon as one is uploaded; do not apply the old projection automatically to a structurally
different future feed.

## Renfe Proximidad

The Córdoba-Julio Anguita to Campus Universitario de Rabanales service is published in
Renfe's official Media Distancia GTFS, not in its Cercanías archive. Download the current
public feed without an API key:

```powershell
.\infra\otp\download-renfe-gtfs.ps1
```

The ignored output is `data/raw/renfe/renfe-md-latest.gtfs.zip`. The downloader rejects a
ZIP that lacks the Córdoba and Rabanales stations. Refresh it and rebuild the OTP graph
whenever Renfe publishes a new timetable; do not extend its calendar by assumption.
