import argparse
import json
from dataclasses import asdict
from datetime import date
from pathlib import Path

from route_planning_api.gtfs.projector import project_weekly_service


def _date(value: str) -> date:
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("Expected an ISO date: YYYY-MM-DD") from exc


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Create an explicitly labelled assumed-current GTFS projection"
    )
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--start", type=_date, required=True)
    parser.add_argument("--end", type=_date, required=True)
    parser.add_argument("--weekday-reference", type=_date, required=True)
    parser.add_argument("--saturday-reference", type=_date, required=True)
    parser.add_argument("--sunday-reference", type=_date, required=True)
    args = parser.parse_args()

    report = project_weekly_service(
        args.source,
        args.output,
        projection_start=args.start,
        projection_end=args.end,
        weekday_reference=args.weekday_reference,
        saturday_reference=args.saturday_reference,
        sunday_reference=args.sunday_reference,
    )
    print(json.dumps(asdict(report), default=str, indent=2))


if __name__ == "__main__":
    main()
