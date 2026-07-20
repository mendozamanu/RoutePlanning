import argparse
import json
from dataclasses import asdict
from pathlib import Path

from route_planning_api.gtfs.profiler import profile_gtfs_snapshot
from route_planning_api.gtfs.validator import inspect_gtfs_snapshot


def main() -> None:
    parser = argparse.ArgumentParser(description="Inspect a GTFS ZIP before activation")
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    report = inspect_gtfs_snapshot(args.path)
    output = {"validation": asdict(report)}
    if report.row_counts:
        output["profile"] = asdict(profile_gtfs_snapshot(args.path))
    print(json.dumps(output, default=str, indent=2))
    raise SystemExit(0 if report.can_activate else 1)


if __name__ == "__main__":
    main()
