import csv
import io
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from zipfile import ZipFile


@dataclass(frozen=True)
class FieldCoverage:
    populated: int
    total: int

    @property
    def percentage(self) -> float:
        return round((self.populated / self.total * 100) if self.total else 0.0, 2)


@dataclass(frozen=True)
class GtfsProfile:
    agency_names: tuple[str, ...]
    agency_timezones: tuple[str, ...]
    bounding_box: tuple[float, float, float, float]
    route_type_counts: dict[str, int]
    stop_wheelchair_coverage: FieldCoverage
    trip_wheelchair_coverage: FieldCoverage
    dangling_trip_route_references: int
    dangling_stop_time_trip_references: int
    dangling_stop_time_stop_references: int

    @property
    def referential_integrity_ok(self) -> bool:
        return (
            self.dangling_trip_route_references == 0
            and self.dangling_stop_time_trip_references == 0
            and self.dangling_stop_time_stop_references == 0
        )


def profile_gtfs_snapshot(path: Path) -> GtfsProfile:
    with ZipFile(path) as archive:
        agencies = _read_rows(archive, "agency.txt")
        stops = _read_rows(archive, "stops.txt")
        routes = _read_rows(archive, "routes.txt")
        trips = _read_rows(archive, "trips.txt")
        stop_times = _read_rows(archive, "stop_times.txt")

    latitudes = [float(row["stop_lat"]) for row in stops]
    longitudes = [float(row["stop_lon"]) for row in stops]
    route_ids = {row["route_id"] for row in routes}
    trip_ids = {row["trip_id"] for row in trips}
    stop_ids = {row["stop_id"] for row in stops}

    return GtfsProfile(
        agency_names=tuple(sorted({row["agency_name"] for row in agencies})),
        agency_timezones=tuple(sorted({row["agency_timezone"] for row in agencies})),
        bounding_box=(
            min(latitudes),
            min(longitudes),
            max(latitudes),
            max(longitudes),
        ),
        route_type_counts=dict(Counter(row["route_type"] for row in routes)),
        stop_wheelchair_coverage=_coverage(stops, "wheelchair_boarding"),
        trip_wheelchair_coverage=_coverage(trips, "wheelchair_accessible"),
        dangling_trip_route_references=sum(row["route_id"] not in route_ids for row in trips),
        dangling_stop_time_trip_references=sum(
            row["trip_id"] not in trip_ids for row in stop_times
        ),
        dangling_stop_time_stop_references=sum(
            row["stop_id"] not in stop_ids for row in stop_times
        ),
    )


def _read_rows(archive: ZipFile, filename: str) -> list[dict[str, str]]:
    text = archive.read(filename).decode("utf-8-sig")
    return list(csv.DictReader(io.StringIO(text)))


def _coverage(rows: list[dict[str, str]], field: str) -> FieldCoverage:
    return FieldCoverage(
        populated=sum(bool(row.get(field, "").strip()) for row in rows),
        total=len(rows),
    )
