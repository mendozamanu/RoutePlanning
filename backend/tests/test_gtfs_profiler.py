from pathlib import Path

from route_planning_api.gtfs.profiler import profile_gtfs_snapshot
from tests.test_gtfs_validator import VALID_FILES, write_gtfs


def test_profile_reports_accessibility_and_integrity(tmp_path: Path) -> None:
    path = tmp_path / "aucorsa.gtfs.zip"
    files = {
        **VALID_FILES,
        "stops.txt": VALID_FILES["stops.txt"].replace(
            "stop_lat,stop_lon", "stop_lat,stop_lon,wheelchair_boarding"
        ).replace("-4.7794\n", "-4.7794,1\n").replace("-4.7211\n", "-4.7211,\n"),
        "trips.txt": VALID_FILES["trips.txt"].replace(
            "trip_id\n", "trip_id,wheelchair_accessible\n"
        ).replace("trip-1\n", "trip-1,1\n"),
    }
    write_gtfs(path, files)

    profile = profile_gtfs_snapshot(path)

    assert profile.agency_timezones == ("Europe/Madrid",)
    assert profile.stop_wheelchair_coverage.percentage == 50.0
    assert profile.trip_wheelchair_coverage.percentage == 100.0
    assert profile.referential_integrity_ok is True
