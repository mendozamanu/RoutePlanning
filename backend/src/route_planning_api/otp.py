import hashlib
from collections.abc import Mapping
from datetime import UTC, datetime
from typing import Any

import httpx

from route_planning_api.models import (
    Coordinate,
    FareNoticeCode,
    JourneyFareNotice,
    JourneyLeg,
    JourneyMode,
    JourneyOption,
    JourneyProfile,
    JourneySearchRequest,
    JourneySearchResponse,
    RealtimeStatus,
    TransportMode,
)
from route_planning_api.planner import JourneyPlannerUnavailableError

PLAN_QUERY = """
query PlanJourney(
  $origin: PlanLabeledLocationInput!
  $destination: PlanLabeledLocationInput!
  $dateTime: PlanDateTimeInput!
  $modes: PlanModesInput
  $first: Int
) {
  planConnection(
    origin: $origin
    destination: $destination
    dateTime: $dateTime
    modes: $modes
    first: $first
  ) {
    routingErrors {
      code
      description
      inputField
    }
    edges {
      cursor
      node {
        start
        end
        duration
        numberOfTransfers
        walkDistance
        legs {
          mode
          realTime
          distance
          headsign
          start { scheduledTime }
          end { scheduledTime }
          from { name lat lon }
          to { name lat lon }
          agency { gtfsId name }
          route { gtfsId shortName }
          stopCalls { stopLocation { __typename } }
          legGeometry { points }
        }
      }
    }
  }
}
"""


class OtpJourneyPlanner:
    def __init__(
        self,
        *,
        base_url: str,
        data_version: str,
        timeout_seconds: float = 15.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = base_url
        self._data_version = data_version
        self._timeout_seconds = timeout_seconds
        self._transport = transport

    async def search(self, request: JourneySearchRequest) -> JourneySearchResponse:
        try:
            async with httpx.AsyncClient(
                transport=self._transport,
                timeout=self._timeout_seconds,
            ) as client:
                response = await client.post(
                    self._base_url,
                    json={
                        "query": PLAN_QUERY,
                        "operationName": "PlanJourney",
                        "variables": _variables(request),
                    },
                    headers={"Accept-Language": "es"},
                )
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError) as error:
            raise JourneyPlannerUnavailableError("OpenTripPlanner is unavailable") from error

        if payload.get("errors"):
            raise JourneyPlannerUnavailableError("OpenTripPlanner rejected the planning query")

        try:
            connection = payload["data"]["planConnection"]
            options = [
                _journey_option(edge)
                for edge in connection.get("edges") or []
                if edge and edge.get("node")
            ]
        except (KeyError, TypeError, ValueError) as error:
            raise JourneyPlannerUnavailableError(
                "OpenTripPlanner returned an invalid response"
            ) from error

        options = [
            option
            for option in options
            if option.transfers <= request.max_transfers
            and (
                request.mode is JourneyMode.WALK
                or option.walk_distance_meters <= request.max_walk_meters
            )
        ]
        options.sort(key=_sort_key(request.profile))
        has_realtime = any(
            bool(leg.get("realTime"))
            for edge in connection.get("edges") or []
            for leg in (edge.get("node") or {}).get("legs") or []
        )

        return JourneySearchResponse(
            data_version=self._data_version,
            generated_at=datetime.now(UTC),
            realtime_status=(
                RealtimeStatus.REALTIME if has_realtime else RealtimeStatus.SCHEDULED_ONLY
            ),
            itineraries=options,
        )


def _variables(request: JourneySearchRequest) -> dict[str, Any]:
    def location(coordinate: Coordinate, label: str) -> dict[str, Any]:
        return {
            "label": label,
            "location": {
                "coordinate": {
                    "latitude": coordinate.latitude,
                    "longitude": coordinate.longitude,
                }
            },
        }

    variables = {
        "origin": location(request.origin, "Origen"),
        "destination": location(request.destination, "Destino"),
        "dateTime": {"earliestDeparture": request.departure_at.isoformat()},
        "first": 15,
    }
    if request.mode in {JourneyMode.BICYCLE, JourneyMode.WALK}:
        variables["modes"] = {"direct": [request.mode.value], "directOnly": True}
    else:
        # Keep every public-transport mode enabled (BUS, RAIL, etc.) while excluding
        # direct walking results from the public-transport tab.
        variables["modes"] = {"transitOnly": True}
    return variables


def _journey_option(edge: Mapping[str, Any]) -> JourneyOption:
    node = edge["node"]
    starts_at = _date_time(node["start"])
    ends_at = _date_time(node["end"])
    fingerprint = f"{edge.get('cursor', '')}|{starts_at.isoformat()}|{ends_at.isoformat()}"
    option_id = "otp-" + hashlib.sha256(fingerprint.encode()).hexdigest()[:24]
    legs = [_journey_leg(leg) for leg in node["legs"]]
    transfers = int(node["numberOfTransfers"])
    return JourneyOption(
        id=option_id,
        starts_at=starts_at,
        ends_at=ends_at,
        duration_seconds=int(node["duration"]),
        transfers=transfers,
        walk_distance_meters=round(float(node["walkDistance"])),
        fare_notices=_fare_notices(legs, transfers),
        legs=legs,
    )


def _journey_leg(leg: Mapping[str, Any]) -> JourneyLeg:
    route = leg.get("route") or {}
    agency = leg.get("agency") or {}
    origin = leg["from"]
    destination = leg["to"]
    mode = _transport_mode(leg["mode"])
    stop_calls = leg.get("stopCalls")
    stop_count = (
        len(stop_calls) - 1
        if mode in {TransportMode.BUS, TransportMode.RAIL}
        and isinstance(stop_calls, list)
        and len(stop_calls) >= 2
        else None
    )
    distance = leg.get("distance")
    return JourneyLeg(
        mode=mode,
        starts_at=_date_time(leg["start"]["scheduledTime"]),
        ends_at=_date_time(leg["end"]["scheduledTime"]),
        from_name=origin.get("name") or "Origen",
        to_name=destination.get("name") or "Destino",
        route_id=route.get("gtfsId"),
        route_short_name=route.get("shortName"),
        agency_id=agency.get("gtfsId"),
        agency_name=agency.get("name"),
        headsign=leg.get("headsign") or None,
        distance_meters=round(float(distance)) if distance is not None else None,
        stop_count=stop_count,
        geometry=_decode_polyline((leg.get("legGeometry") or {}).get("points")),
        # The active feeds do not provide enough wheelchair metadata. Null is intentional:
        # it means "not confirmed", never "accessible" or "inaccessible" by inference.
        accessibility_confirmed=None,
    )


def _date_time(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("OTP datetime has no timezone")
    return parsed


def _transport_mode(value: str) -> TransportMode:
    if value == "WALK":
        return TransportMode.WALK
    if value == "BUS":
        return TransportMode.BUS
    if value == "RAIL":
        return TransportMode.RAIL
    if value == "BICYCLE":
        return TransportMode.BICYCLE
    raise ValueError(f"Unsupported OTP mode: {value}")


def _fare_notices(
    legs: list[JourneyLeg],
    transfers: int,
) -> list[JourneyFareNotice]:
    bus_leg_count = sum(leg.mode is TransportMode.BUS for leg in legs)
    uncounted_bus_boundaries = max(bus_leg_count - 1 - transfers, 0)
    if uncounted_bus_boundaries == 0:
        return []

    same_line_boundaries = [
        (current, following)
        for current, following in zip(legs, legs[1:], strict=False)
        if current.mode is TransportMode.BUS
        and following.mode is TransportMode.BUS
        and _same_route(current, following)
    ]
    return [
        JourneyFareNotice(
            code=FareNoticeCode.SAME_LINE_NEW_TICKET,
            stop_name=current.to_name,
            route_short_name=current.route_short_name or following.route_short_name,
        )
        for current, following in same_line_boundaries[:uncounted_bus_boundaries]
    ]


def _same_route(current: JourneyLeg, following: JourneyLeg) -> bool:
    if (
        current.route_id
        and following.route_id
        and current.route_id.casefold() == following.route_id.casefold()
    ):
        return True
    return bool(
        current.route_short_name
        and following.route_short_name
        and current.route_short_name.casefold() == following.route_short_name.casefold()
    )


def _sort_key(profile: JourneyProfile):
    if profile is JourneyProfile.FEWER_TRANSFERS:
        return lambda option: (
            option.transfers,
            option.ends_at,
            option.duration_seconds,
        )
    if profile is JourneyProfile.LESS_WALKING:
        return lambda option: (
            option.walk_distance_meters,
            option.ends_at,
            option.transfers,
        )
    # A departure search asks "what gets me there first from this time?". OTP's
    # duration starts when the itinerary itself starts and excludes any initial wait,
    # so sorting by duration can put a later-arriving train ahead of an earlier bus.
    return lambda option: (
        option.ends_at,
        option.transfers,
        option.walk_distance_meters,
    )


def _decode_polyline(value: str | None) -> list[Coordinate]:
    if not value:
        return []

    coordinates: list[Coordinate] = []
    latitude = 0
    longitude = 0
    index = 0
    while index < len(value):
        latitude_delta, index = _decode_polyline_value(value, index)
        longitude_delta, index = _decode_polyline_value(value, index)
        latitude += latitude_delta
        longitude += longitude_delta
        coordinates.append(Coordinate(latitude=latitude / 100_000, longitude=longitude / 100_000))
    return coordinates


def _decode_polyline_value(value: str, index: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while True:
        if index >= len(value):
            raise ValueError("Truncated encoded polyline")
        chunk = ord(value[index]) - 63
        index += 1
        result |= (chunk & 0x1F) << shift
        shift += 5
        if chunk < 0x20:
            break
    return (~(result >> 1) if result & 1 else result >> 1), index
