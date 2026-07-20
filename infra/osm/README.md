# Córdoba OpenStreetMap extract

The AUCORSA GTFS stops span approximately `-4.937..-4.654` longitude and
`37.832..38.017` latitude. The extraction script adds an 8–10 km buffer and uses the
`complete_ways` strategy so roads crossing the bounding box remain usable by
OpenTripPlanner.

Run from the repository root:

```powershell
.\infra\osm\extract-cordoba.ps1
```

The generated `data/raw/osm/cordoba.osm.pbf` is ignored by Git. The regional source file
is retained unchanged and root-level `.osm.pbf` files are also ignored.
