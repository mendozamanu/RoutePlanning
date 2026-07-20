import csv
import hashlib
import io
from dataclasses import dataclass
from datetime import date, datetime
from pathlib import Path, PurePosixPath
from zipfile import BadZipFile, ZipFile

REQUIRED_COLUMNS = {
    "agency.txt": {"agency_name", "agency_url", "agency_timezone"},
    "stops.txt": {"stop_id", "stop_name", "stop_lat", "stop_lon"},
    "routes.txt": {"route_id", "route_type"},
    "trips.txt": {"route_id", "service_id", "trip_id"},
    "stop_times.txt": {"trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence"},
}
CALENDAR_FILES = {"calendar.txt", "calendar_dates.txt"}


@dataclass(frozen=True)
class GtfsIssue:
    code: str
    message: str


@dataclass(frozen=True)
class GtfsValidationReport:
    checksum_sha256: str
    file_size_bytes: int
    row_counts: dict[str, int]
    service_end_date: date | None
    errors: tuple[GtfsIssue, ...]
    warnings: tuple[GtfsIssue, ...]

    @property
    def can_activate(self) -> bool:
        return not self.errors


def inspect_gtfs_snapshot(path: Path, *, today: date | None = None) -> GtfsValidationReport:
    effective_today = today or date.today()
    payload = path.read_bytes()
    checksum = hashlib.sha256(payload).hexdigest()
    errors: list[GtfsIssue] = []
    warnings: list[GtfsIssue] = []
    row_counts: dict[str, int] = {}
    service_dates: list[date] = []

    try:
        with ZipFile(io.BytesIO(payload)) as archive:
            names = _normalized_root_names(archive.namelist(), errors)
            for required_file in REQUIRED_COLUMNS:
                if required_file not in names:
                    errors.append(
                        GtfsIssue("MISSING_FILE", f"Required GTFS file is missing: {required_file}")
                    )
            if not CALENDAR_FILES.intersection(names):
                errors.append(
                    GtfsIssue(
                        "MISSING_CALENDAR",
                        "calendar.txt or calendar_dates.txt is required",
                    )
                )

            for filename, required_columns in REQUIRED_COLUMNS.items():
                if filename not in names:
                    continue
                rows, columns = _read_csv(archive.read(names[filename]), filename, errors)
                row_counts[filename] = len(rows)
                missing_columns = required_columns.difference(columns)
                if missing_columns:
                    errors.append(
                        GtfsIssue(
                            "MISSING_COLUMN",
                            f"{filename} is missing columns: {', '.join(sorted(missing_columns))}",
                        )
                    )
                if not rows:
                    errors.append(GtfsIssue("EMPTY_FILE", f"{filename} contains no data rows"))

            if "calendar.txt" in names:
                rows, _ = _read_csv(archive.read(names["calendar.txt"]), "calendar.txt", errors)
                row_counts["calendar.txt"] = len(rows)
                service_dates.extend(
                    parsed
                    for row in rows
                    if (parsed := _parse_gtfs_date(row.get("end_date"))) is not None
                )
            if "calendar_dates.txt" in names:
                rows, _ = _read_csv(
                    archive.read(names["calendar_dates.txt"]),
                    "calendar_dates.txt",
                    errors,
                )
                row_counts["calendar_dates.txt"] = len(rows)
                service_dates.extend(
                    parsed
                    for row in rows
                    if (parsed := _parse_gtfs_date(row.get("date"))) is not None
                )
    except (BadZipFile, OSError) as exc:
        errors.append(GtfsIssue("INVALID_ZIP", f"Cannot read GTFS ZIP: {exc}"))

    service_end_date = max(service_dates, default=None)
    if service_end_date is None:
        warnings.append(
            GtfsIssue("UNKNOWN_SERVICE_RANGE", "The service end date could not be determined")
        )
    elif service_end_date < effective_today:
        errors.append(
            GtfsIssue(
                "SERVICE_EXPIRED",
                f"GTFS service ended on {service_end_date.isoformat()}",
            )
        )

    return GtfsValidationReport(
        checksum_sha256=checksum,
        file_size_bytes=len(payload),
        row_counts=row_counts,
        service_end_date=service_end_date,
        errors=tuple(errors),
        warnings=tuple(warnings),
    )


def _normalized_root_names(archive_names: list[str], errors: list[GtfsIssue]) -> dict[str, str]:
    normalized: dict[str, str] = {}
    for archive_name in archive_names:
        path = PurePosixPath(archive_name)
        if path.is_absolute() or ".." in path.parts:
            errors.append(GtfsIssue("UNSAFE_PATH", f"Unsafe ZIP entry: {archive_name}"))
            continue
        if len(path.parts) != 1 or archive_name.endswith("/"):
            continue
        normalized[path.name.lower()] = archive_name
    return normalized


def _read_csv(
    payload: bytes,
    filename: str,
    errors: list[GtfsIssue],
) -> tuple[list[dict[str, str]], set[str]]:
    try:
        text = payload.decode("utf-8-sig")
        reader = csv.DictReader(io.StringIO(text))
        reader.fieldnames = [field.strip() for field in (reader.fieldnames or [])]
        rows = [
            {
                (key.strip() if key is not None else key): (
                    value.strip() if isinstance(value, str) else value
                )
                for key, value in row.items()
            }
            for row in reader
        ]
        columns = set(reader.fieldnames)
        return rows, columns
    except (UnicodeDecodeError, csv.Error) as exc:
        errors.append(GtfsIssue("INVALID_CSV", f"Cannot parse {filename}: {exc}"))
        return [], set()


def _parse_gtfs_date(value: str | None) -> date | None:
    if not value:
        return None
    try:
        return datetime.strptime(value, "%Y%m%d").date()
    except ValueError:
        return None
