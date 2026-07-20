import csv
import hashlib
import io
import tempfile
from dataclasses import dataclass
from datetime import date
from pathlib import Path, PurePosixPath
from zipfile import BadZipFile, ZipFile

WEEKDAY_COLUMNS = (
    "monday",
    "tuesday",
    "wednesday",
    "thursday",
    "friday",
    "saturday",
    "sunday",
)
REQUIRED_CALENDAR_COLUMNS = {"service_id", "start_date", "end_date", *WEEKDAY_COLUMNS}


class GtfsProjectionError(ValueError):
    """The source feed cannot be projected without making an unsafe assumption."""


@dataclass(frozen=True)
class GtfsProjectionReport:
    source_sha256: str
    output_sha256: str
    projection_start_date: date
    projection_end_date: date
    weekday_reference_date: date
    saturday_reference_date: date
    sunday_reference_date: date
    weekday_service_count: int
    saturday_service_count: int
    sunday_service_count: int
    projected_service_count: int
    added_calendar_service_count: int


def project_weekly_service(
    source_path: Path,
    output_path: Path,
    *,
    projection_start: date,
    projection_end: date,
    weekday_reference: date,
    saturday_reference: date,
    sunday_reference: date,
) -> GtfsProjectionReport:
    """Create a derived GTFS feed by repeating three observed ordinary service days.

    The official source archive is read-only. The selected service IDs keep their
    historical calendar_dates entries, while calendar.txt is changed to activate them
    over the requested future interval.
    """
    if projection_end < projection_start:
        raise GtfsProjectionError("Projection end date must not precede its start date")
    if weekday_reference.weekday() > 4:
        raise GtfsProjectionError("Weekday reference must be a Monday through Friday")
    if saturday_reference.weekday() != 5:
        raise GtfsProjectionError("Saturday reference must be a Saturday")
    if sunday_reference.weekday() != 6:
        raise GtfsProjectionError("Sunday reference must be a Sunday")

    resolved_source = source_path.resolve()
    resolved_output = output_path.resolve()
    if resolved_source == resolved_output:
        raise GtfsProjectionError("Source and output paths must be different")
    if resolved_output.exists():
        raise FileExistsError(f"Projection output already exists: {resolved_output}")

    source_payload = resolved_source.read_bytes()
    try:
        with ZipFile(io.BytesIO(source_payload)) as source_archive:
            names = _root_file_names(source_archive.namelist())
            calendar_name = names.get("calendar.txt")
            calendar_dates_name = names.get("calendar_dates.txt")
            if calendar_name is None or calendar_dates_name is None:
                raise GtfsProjectionError(
                    "Projection requires both calendar.txt and calendar_dates.txt"
                )

            calendar_rows, calendar_columns = _read_rows(
                source_archive.read(calendar_name), "calendar.txt"
            )
            calendar_date_rows, _ = _read_rows(
                source_archive.read(calendar_dates_name), "calendar_dates.txt"
            )
            missing_columns = REQUIRED_CALENDAR_COLUMNS.difference(calendar_columns)
            if missing_columns:
                missing = ", ".join(sorted(missing_columns))
                raise GtfsProjectionError(f"calendar.txt is missing columns: {missing}")

            service_rows = _unique_service_rows(calendar_rows)
            reference_services = {
                "weekday": _active_services_on(
                    weekday_reference, calendar_rows, calendar_date_rows
                ),
                "saturday": _active_services_on(
                    saturday_reference, calendar_rows, calendar_date_rows
                ),
                "sunday": _active_services_on(sunday_reference, calendar_rows, calendar_date_rows),
            }
            for day_type, services in reference_services.items():
                if not services:
                    raise GtfsProjectionError(
                        f"The {day_type} reference date has no active service"
                    )

            projected_days: dict[str, set[str]] = {}
            for service_id in reference_services["weekday"]:
                projected_days.setdefault(service_id, set()).update(WEEKDAY_COLUMNS[:5])
            for service_id in reference_services["saturday"]:
                projected_days.setdefault(service_id, set()).add("saturday")
            for service_id in reference_services["sunday"]:
                projected_days.setdefault(service_id, set()).add("sunday")

            start_value = projection_start.strftime("%Y%m%d")
            end_value = projection_end.strftime("%Y%m%d")
            added_calendar_service_count = 0
            for service_id, active_days in projected_days.items():
                row = service_rows.get(service_id)
                if row is None:
                    row = dict.fromkeys(calendar_columns, "")
                    row["service_id"] = service_id
                    calendar_rows.append(row)
                    service_rows[service_id] = row
                    added_calendar_service_count += 1
                for weekday in WEEKDAY_COLUMNS:
                    row[weekday] = "1" if weekday in active_days else "0"
                row["start_date"] = start_value
                row["end_date"] = end_value

            projected_calendar = _write_rows(calendar_rows, calendar_columns)
            output_sha256 = _write_derived_archive(
                source_archive,
                resolved_output,
                replacement_name=calendar_name,
                replacement_payload=projected_calendar,
            )
    except BadZipFile as exc:
        raise GtfsProjectionError(f"Cannot read GTFS ZIP: {exc}") from exc

    return GtfsProjectionReport(
        source_sha256=hashlib.sha256(source_payload).hexdigest(),
        output_sha256=output_sha256,
        projection_start_date=projection_start,
        projection_end_date=projection_end,
        weekday_reference_date=weekday_reference,
        saturday_reference_date=saturday_reference,
        sunday_reference_date=sunday_reference,
        weekday_service_count=len(reference_services["weekday"]),
        saturday_service_count=len(reference_services["saturday"]),
        sunday_service_count=len(reference_services["sunday"]),
        projected_service_count=len(projected_days),
        added_calendar_service_count=added_calendar_service_count,
    )


def _root_file_names(archive_names: list[str]) -> dict[str, str]:
    normalized: dict[str, str] = {}
    for archive_name in archive_names:
        path = PurePosixPath(archive_name)
        if path.is_absolute() or ".." in path.parts:
            raise GtfsProjectionError(f"Unsafe ZIP entry: {archive_name}")
        if len(path.parts) == 1 and not archive_name.endswith("/"):
            normalized[path.name.lower()] = archive_name
    return normalized


def _read_rows(payload: bytes, filename: str) -> tuple[list[dict[str, str]], list[str]]:
    try:
        reader = csv.DictReader(io.StringIO(payload.decode("utf-8-sig")))
        columns = list(reader.fieldnames or [])
        return list(reader), columns
    except (UnicodeDecodeError, csv.Error) as exc:
        raise GtfsProjectionError(f"Cannot parse {filename}: {exc}") from exc


def _write_rows(rows: list[dict[str, str]], columns: list[str]) -> bytes:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=columns, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue().encode("utf-8")


def _unique_service_rows(rows: list[dict[str, str]]) -> dict[str, dict[str, str]]:
    services: dict[str, dict[str, str]] = {}
    for row in rows:
        service_id = row.get("service_id", "")
        if not service_id:
            raise GtfsProjectionError("calendar.txt contains an empty service_id")
        if service_id in services:
            raise GtfsProjectionError(f"Duplicate calendar service_id: {service_id}")
        services[service_id] = row
    return services


def _active_services_on(
    service_date: date,
    calendar_rows: list[dict[str, str]],
    calendar_date_rows: list[dict[str, str]],
) -> set[str]:
    formatted_date = service_date.strftime("%Y%m%d")
    weekday_column = WEEKDAY_COLUMNS[service_date.weekday()]
    active = {
        row["service_id"]
        for row in calendar_rows
        if row.get("start_date", "") <= formatted_date <= row.get("end_date", "")
        and row.get(weekday_column) == "1"
    }
    for row in calendar_date_rows:
        if row.get("date") != formatted_date:
            continue
        if row.get("exception_type") == "1":
            active.add(row.get("service_id", ""))
        elif row.get("exception_type") == "2":
            active.discard(row.get("service_id", ""))
    active.discard("")
    return active


def _write_derived_archive(
    source_archive: ZipFile,
    output_path: Path,
    *,
    replacement_name: str,
    replacement_payload: bytes,
) -> str:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            prefix=f".{output_path.name}.", suffix=".tmp", dir=output_path.parent, delete=False
        ) as temporary_file:
            temporary_path = Path(temporary_file.name)
        with ZipFile(temporary_path, "w") as output_archive:
            for entry in source_archive.infolist():
                payload = (
                    replacement_payload
                    if entry.filename == replacement_name
                    else source_archive.read(entry.filename)
                )
                output_archive.writestr(entry, payload)
        temporary_path.replace(output_path)
    finally:
        if temporary_path is not None and temporary_path.exists():
            temporary_path.unlink()
    return hashlib.sha256(output_path.read_bytes()).hexdigest()
