from datetime import date
from pathlib import Path
from zipfile import ZipFile

from route_planning_api.gtfs.validator import inspect_gtfs_snapshot

VALID_FILES = {
    "agency.txt": (
        "agency_id,agency_name,agency_url,agency_timezone\n"
        "aucorsa,AUCORSA,https://www.aucorsa.es,Europe/Madrid\n"
    ),
    "stops.txt": (
        "stop_id,stop_name,stop_lat,stop_lon\n"
        "stop-1,Centro,37.8882,-4.7794\n"
        "stop-2,Rabanales,37.9138,-4.7211\n"
    ),
    "routes.txt": "route_id,agency_id,route_short_name,route_type\nline-e,aucorsa,E,3\n",
    "trips.txt": "route_id,service_id,trip_id\nline-e,weekday,trip-1\n",
    "stop_times.txt": (
        "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
        "trip-1,08:00:00,08:00:00,stop-1,1\n"
        "trip-1,08:30:00,08:30:00,stop-2,2\n"
    ),
    "calendar.txt": (
        "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,"
        "start_date,end_date\n"
        "weekday,1,1,1,1,1,0,0,20260701,20261231\n"
    ),
}


def write_gtfs(path: Path, files: dict[str, str]) -> None:
    with ZipFile(path, "w") as archive:
        for filename, content in files.items():
            archive.writestr(filename, content)


def test_valid_snapshot_can_be_activated(tmp_path: Path) -> None:
    path = tmp_path / "aucorsa.gtfs.zip"
    write_gtfs(path, VALID_FILES)

    report = inspect_gtfs_snapshot(path, today=date(2026, 7, 18))

    assert report.can_activate is True
    assert report.service_end_date == date(2026, 12, 31)
    assert report.row_counts["stops.txt"] == 2
    assert len(report.checksum_sha256) == 64


def test_padded_csv_headers_and_dates_are_normalized(tmp_path: Path) -> None:
    path = tmp_path / "renfe-padded.gtfs.zip"
    padded = {
        **VALID_FILES,
        "calendar.txt": VALID_FILES["calendar.txt"]
        .replace("end_date\n", "end_date      \n")
        .replace("20261231\n", "20261231      \n"),
    }
    write_gtfs(path, padded)

    report = inspect_gtfs_snapshot(path, today=date(2026, 7, 18))

    assert report.can_activate is True
    assert report.service_end_date == date(2026, 12, 31)


def test_expired_snapshot_is_rejected(tmp_path: Path) -> None:
    path = tmp_path / "aucorsa-expired.gtfs.zip"
    expired = {
        **VALID_FILES,
        "calendar.txt": VALID_FILES["calendar.txt"].replace("20261231", "20260331"),
    }
    write_gtfs(path, expired)

    report = inspect_gtfs_snapshot(path, today=date(2026, 7, 18))

    assert report.can_activate is False
    assert "SERVICE_EXPIRED" in {issue.code for issue in report.errors}


def test_missing_required_file_is_rejected(tmp_path: Path) -> None:
    path = tmp_path / "aucorsa-incomplete.gtfs.zip"
    incomplete = {name: content for name, content in VALID_FILES.items() if name != "trips.txt"}
    write_gtfs(path, incomplete)

    report = inspect_gtfs_snapshot(path, today=date(2026, 7, 18))

    assert report.can_activate is False
    assert "MISSING_FILE" in {issue.code for issue in report.errors}
