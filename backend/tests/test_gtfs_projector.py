import csv
import io
from datetime import date
from pathlib import Path
from zipfile import ZipFile

import pytest

from route_planning_api.gtfs.projector import GtfsProjectionError, project_weekly_service
from route_planning_api.gtfs.validator import inspect_gtfs_snapshot
from tests.test_gtfs_validator import VALID_FILES, write_gtfs


def _explicit_calendar_feed() -> dict[str, str]:
    return {
        **VALID_FILES,
        "trips.txt": (
            "route_id,service_id,trip_id\n"
            "line-e,weekday,trip-1\n"
            "line-e,saturday,trip-2\n"
            "line-e,sunday,trip-3\n"
            "line-e,special,trip-4\n"
        ),
        "stop_times.txt": (
            "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
            "trip-1,08:00:00,08:00:00,stop-1,1\n"
            "trip-2,08:00:00,08:00:00,stop-1,1\n"
            "trip-3,08:00:00,08:00:00,stop-1,1\n"
            "trip-4,08:00:00,08:00:00,stop-1,1\n"
        ),
        "calendar.txt": (
            "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,"
            "start_date,end_date\n"
            "weekday,0,0,0,0,0,0,0,20260301,20260331\n"
            "saturday,0,0,0,0,0,0,0,20260301,20260331\n"
            "sunday,0,0,0,0,0,0,0,20260301,20260331\n"
            "special,0,0,0,0,0,0,0,20260301,20260331\n"
        ),
        "calendar_dates.txt": (
            "service_id,date,exception_type\n"
            "weekday,20260309,1\n"
            "saturday,20260314,1\n"
            "sunday,20260315,1\n"
            "special,20260331,1\n"
        ),
    }


def _calendar_rows(path: Path) -> dict[str, dict[str, str]]:
    with ZipFile(path) as archive:
        text = archive.read("calendar.txt").decode("utf-8")
    return {row["service_id"]: row for row in csv.DictReader(io.StringIO(text))}


def test_projects_only_the_observed_ordinary_week(tmp_path: Path) -> None:
    source = tmp_path / "official.gtfs.zip"
    output = tmp_path / "assumed-current.gtfs.zip"
    write_gtfs(source, _explicit_calendar_feed())
    source_before = source.read_bytes()

    report = project_weekly_service(
        source,
        output,
        projection_start=date(2026, 4, 1),
        projection_end=date(2027, 7, 19),
        weekday_reference=date(2026, 3, 9),
        saturday_reference=date(2026, 3, 14),
        sunday_reference=date(2026, 3, 15),
    )

    rows = _calendar_rows(output)
    assert source.read_bytes() == source_before
    ordinary_weekdays = ("monday", "tuesday", "wednesday", "thursday", "friday")
    assert [rows["weekday"][day] for day in ordinary_weekdays] == ["1"] * 5
    assert rows["weekday"]["saturday"] == "0"
    assert rows["weekday"]["sunday"] == "0"
    assert rows["saturday"]["saturday"] == "1"
    assert rows["sunday"]["sunday"] == "1"
    assert rows["special"]["end_date"] == "20260331"
    assert rows["special"]["monday"] == "0"
    assert rows["weekday"]["start_date"] == "20260401"
    assert rows["weekday"]["end_date"] == "20270719"
    assert report.projected_service_count == 3
    assert report.added_calendar_service_count == 0
    assert report.source_sha256 != report.output_sha256

    validation = inspect_gtfs_snapshot(output, today=date(2026, 7, 19))
    assert validation.can_activate is True
    assert validation.service_end_date == date(2027, 7, 19)


def test_rejects_a_reference_with_the_wrong_weekday(tmp_path: Path) -> None:
    source = tmp_path / "official.gtfs.zip"
    write_gtfs(source, _explicit_calendar_feed())

    with pytest.raises(GtfsProjectionError, match="Weekday reference"):
        project_weekly_service(
            source,
            tmp_path / "output.gtfs.zip",
            projection_start=date(2026, 4, 1),
            projection_end=date(2027, 7, 19),
            weekday_reference=date(2026, 3, 14),
            saturday_reference=date(2026, 3, 14),
            sunday_reference=date(2026, 3, 15),
        )


def test_does_not_overwrite_an_existing_projection(tmp_path: Path) -> None:
    source = tmp_path / "official.gtfs.zip"
    output = tmp_path / "output.gtfs.zip"
    write_gtfs(source, _explicit_calendar_feed())
    output.write_bytes(b"keep me")

    with pytest.raises(FileExistsError):
        project_weekly_service(
            source,
            output,
            projection_start=date(2026, 4, 1),
            projection_end=date(2027, 7, 19),
            weekday_reference=date(2026, 3, 9),
            saturday_reference=date(2026, 3, 14),
            sunday_reference=date(2026, 3, 15),
        )
    assert output.read_bytes() == b"keep me"


def test_adds_projected_services_missing_from_calendar(tmp_path: Path) -> None:
    source = tmp_path / "official.gtfs.zip"
    output = tmp_path / "output.gtfs.zip"
    files = _explicit_calendar_feed()
    files["calendar.txt"] = files["calendar.txt"].replace(
        "weekday,0,0,0,0,0,0,0,20260301,20260331\n", ""
    )
    write_gtfs(source, files)

    report = project_weekly_service(
        source,
        output,
        projection_start=date(2026, 4, 1),
        projection_end=date(2027, 7, 19),
        weekday_reference=date(2026, 3, 9),
        saturday_reference=date(2026, 3, 14),
        sunday_reference=date(2026, 3, 15),
    )

    rows = _calendar_rows(output)
    assert rows["weekday"]["monday"] == "1"
    assert report.added_calendar_service_count == 1
