from copy import deepcopy
from datetime import datetime

import httpx
import pytest

from route_planning_api.models import (
    JourneyMode,
    JourneyProfile,
    JourneySearchRequest,
    RealtimeStatus,
    TransportMode,
)
from route_planning_api.otp import OtpJourneyPlanner, _decode_polyline
from route_planning_api.planner import JourneyPlannerUnavailableError


def _request(
    profile: JourneyProfile = JourneyProfile.FASTEST,
    mode: JourneyMode = JourneyMode.TRANSIT,
) -> JourneySearchRequest:
    return JourneySearchRequest.model_validate(
        {
            "origin": {"latitude": 37.8882, "longitude": -4.7794},
            "destination": {"latitude": 37.9138, "longitude": -4.7211},
            "departure_at": "2026-03-20T08:00:00+01:00",
            "mode": mode,
            "profile": profile,
            "max_walk_meters": 1500,
            "max_transfers": 2,
        }
    )


def _otp_response() -> dict:
    return {
        "data": {
            "planConnection": {
                "routingErrors": [],
                "edges": [
                    {
                        "cursor": "option-a",
                        "node": {
                            "start": "2026-03-20T08:00:00+01:00",
                            "end": "2026-03-20T08:30:00+01:00",
                            "duration": 1800,
                            "numberOfTransfers": 0,
                            "walkDistance": 420.4,
                            "legs": [
                                {
                                    "mode": "WALK",
                                    "realTime": False,
                                    "distance": 420.4,
                                    "headsign": None,
                                    "stopCalls": [],
                                    "start": {"scheduledTime": "2026-03-20T08:00:00+01:00"},
                                    "end": {"scheduledTime": "2026-03-20T08:05:00+01:00"},
                                    "from": {"name": None, "lat": 37.8882, "lon": -4.7794},
                                    "to": {
                                        "name": "Colón Norte",
                                        "lat": 37.8890,
                                        "lon": -4.7780,
                                    },
                                    "agency": None,
                                    "route": None,
                                    "legGeometry": {"points": "_p~iF~ps|U_ulLnnqC_mqNvxq`@"},
                                },
                                {
                                    "mode": "BUS",
                                    "realTime": False,
                                    "distance": 7100.2,
                                    "headsign": "Campus de Rabanales",
                                    "stopCalls": [
                                        {"stopLocation": {"__typename": "Stop"}},
                                        {"stopLocation": {"__typename": "Stop"}},
                                        {"stopLocation": {"__typename": "Stop"}},
                                        {"stopLocation": {"__typename": "Stop"}},
                                    ],
                                    "start": {"scheduledTime": "2026-03-20T08:05:00+01:00"},
                                    "end": {"scheduledTime": "2026-03-20T08:30:00+01:00"},
                                    "from": {
                                        "name": "Colón Norte",
                                        "lat": 37.8890,
                                        "lon": -4.7780,
                                    },
                                    "to": {
                                        "name": "Campus de Rabanales",
                                        "lat": 37.9138,
                                        "lon": -4.7211,
                                    },
                                    "agency": {
                                        "gtfsId": "AUCORSA:aucorsa",
                                        "name": "AUCORSA",
                                    },
                                    "route": {"gtfsId": "AUCORSA:E", "shortName": "E"},
                                    "legGeometry": None,
                                },
                            ],
                        },
                    }
                ],
            }
        }
    }


def _otp_bicycle_response() -> dict:
    return {
        "data": {
            "planConnection": {
                "routingErrors": [],
                "edges": [
                    {
                        "cursor": "bike-option",
                        "node": {
                            "start": "2026-03-20T08:00:00+01:00",
                            "end": "2026-03-20T08:23:00+01:00",
                            "duration": 1380,
                            "numberOfTransfers": 0,
                            "walkDistance": 0,
                            "legs": [
                                {
                                    "mode": "BICYCLE",
                                    "realTime": False,
                                    "distance": 5200.2,
                                    "headsign": None,
                                    "stopCalls": [],
                                    "start": {"scheduledTime": "2026-03-20T08:00:00+01:00"},
                                    "end": {"scheduledTime": "2026-03-20T08:23:00+01:00"},
                                    "from": {
                                        "name": "Origen",
                                        "lat": 37.9022,
                                        "lon": -4.7304,
                                    },
                                    "to": {
                                        "name": "Destino",
                                        "lat": 37.8885,
                                        "lon": -4.7808,
                                    },
                                    "agency": None,
                                    "route": None,
                                    "legGeometry": {"points": "_p~iF~ps|U_ulLnnqC"},
                                }
                            ],
                        },
                    }
                ],
            }
        }
    }


def _otp_walk_response() -> dict:
    payload = deepcopy(_otp_bicycle_response())
    node = payload["data"]["planConnection"]["edges"][0]["node"]
    node["duration"] = 1500
    node["walkDistance"] = 2400
    node["legs"][0]["mode"] = "WALK"
    return payload


def _otp_same_line_continuation_response() -> dict:
    payload = deepcopy(_otp_response())
    node = payload["data"]["planConnection"]["edges"][0]["node"]
    walk_leg, bus_template = node["legs"]
    first_bus = deepcopy(bus_template)
    first_bus.update(
        {
            "start": {"scheduledTime": "2026-03-20T08:05:00+01:00"},
            "end": {"scheduledTime": "2026-03-20T08:15:00+01:00"},
            "from": {"name": "Jesús Rescatado", "lat": 37.887, "lon": -4.77},
            "to": {"name": "Colón Norte", "lat": 37.889, "lon": -4.778},
        }
    )
    second_bus = deepcopy(bus_template)
    second_bus.update(
        {
            "start": {"scheduledTime": "2026-03-20T08:15:00+01:00"},
            "end": {"scheduledTime": "2026-03-20T08:18:00+01:00"},
            "from": {"name": "Colón Norte", "lat": 37.889, "lon": -4.778},
            "to": {"name": "Gran Capitán", "lat": 37.884, "lon": -4.781},
        }
    )
    node["legs"] = [walk_leg, first_bus, second_bus]
    return payload


def _otp_rail_response() -> dict:
    payload = deepcopy(_otp_response())
    node = payload["data"]["planConnection"]["edges"][0]["node"]
    rail_leg = node["legs"][1]
    rail_leg.update(
        {
            "mode": "RAIL",
            "headsign": "Campus Universitario de Rabanales",
            "from": {
                "name": "Córdoba-Julio Anguita",
                "lat": 37.888291,
                "lon": -4.789453,
            },
            "to": {
                "name": "Campus Universitario de Rabanales",
                "lat": 37.91256,
                "lon": -4.72086,
            },
            "agency": {"gtfsId": "RENFE:1071", "name": "RENFE OPERADORA"},
            "route": {"gtfsId": "RENFE:5050050417VRN", "shortName": "PROXIMDAD"},
        }
    )
    return payload


def _otp_early_arrival_vs_short_duration_response() -> dict:
    payload = deepcopy(_otp_response())
    template = payload["data"]["planConnection"]["edges"][0]
    early_bus = deepcopy(template)
    early_bus["cursor"] = "early-bus"
    early_bus["node"].update(
        {
            "start": "2026-03-20T08:00:00+01:00",
            "end": "2026-03-20T08:30:00+01:00",
            "duration": 1800,
        }
    )
    later_short_trip = deepcopy(template)
    later_short_trip["cursor"] = "later-short-trip"
    later_short_trip["node"].update(
        {
            "start": "2026-03-20T08:20:00+01:00",
            "end": "2026-03-20T08:35:00+01:00",
            "duration": 900,
        }
    )
    payload["data"]["planConnection"]["edges"] = [later_short_trip, early_bus]
    return payload


@pytest.mark.anyio
async def test_otp_adapter_maps_to_stable_mobile_contract() -> None:
    captured_request: httpx.Request | None = None

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal captured_request
        captured_request = request
        return httpx.Response(200, json=_otp_response())

    planner = OtpJourneyPlanner(
        base_url="http://otp/otp/gtfs/v1",
        data_version="aucorsa-20260225-4fa3e7652467",
        transport=httpx.MockTransport(handler),
    )

    result = await planner.search(_request())

    assert captured_request is not None
    request_body = __import__("json").loads(captured_request.content)
    assert request_body["operationName"] == "PlanJourney"
    assert request_body["variables"]["dateTime"]["earliestDeparture"].endswith("+01:00")
    assert request_body["variables"]["first"] == 15
    assert request_body["variables"]["modes"] == {"transitOnly": True}
    assert result.data_version == "aucorsa-20260225-4fa3e7652467"
    assert result.realtime_status is RealtimeStatus.SCHEDULED_ONLY
    assert result.itineraries[0].walk_distance_meters == 420
    assert result.itineraries[0].legs[1].route_short_name == "E"
    assert result.itineraries[0].legs[1].agency_id == "AUCORSA:aucorsa"
    assert result.itineraries[0].legs[1].agency_name == "AUCORSA"
    assert result.itineraries[0].legs[1].headsign == "Campus de Rabanales"
    assert result.itineraries[0].legs[1].distance_meters == 7100
    assert result.itineraries[0].legs[1].stop_count == 3
    assert result.itineraries[0].legs[1].accessibility_confirmed is None
    assert result.itineraries[0].fare_notices == []
    assert "stopCalls" in request_body["query"]
    assert "agency { gtfsId name }" in request_body["query"]


@pytest.mark.anyio
async def test_fastest_profile_prefers_earliest_arrival_over_shorter_later_trip() -> None:
    planner = OtpJourneyPlanner(
        base_url="http://otp/otp/gtfs/v1",
        data_version="test",
        transport=httpx.MockTransport(
            lambda request: httpx.Response(
                200,
                json=_otp_early_arrival_vs_short_duration_response(),
            )
        ),
    )

    itineraries = (await planner.search(_request())).itineraries

    assert itineraries[0].ends_at.isoformat() == "2026-03-20T08:30:00+01:00"
    assert itineraries[0].duration_seconds == 1800
    assert itineraries[1].ends_at.isoformat() == "2026-03-20T08:35:00+01:00"
    assert itineraries[1].duration_seconds == 900


@pytest.mark.anyio
async def test_otp_adapter_maps_rail_inside_a_transit_search() -> None:
    planner = OtpJourneyPlanner(
        base_url="http://otp/otp/gtfs/v1",
        data_version="aucorsa-renfe-test",
        transport=httpx.MockTransport(
            lambda request: httpx.Response(200, json=_otp_rail_response())
        ),
    )

    itinerary = (await planner.search(_request())).itineraries[0]
    rail_leg = itinerary.legs[1]

    assert rail_leg.mode is TransportMode.RAIL
    assert rail_leg.route_short_name == "PROXIMDAD"
    assert rail_leg.agency_id == "RENFE:1071"
    assert rail_leg.agency_name == "RENFE OPERADORA"
    assert rail_leg.stop_count == 3


@pytest.mark.anyio
async def test_otp_marks_same_line_expedition_split_as_an_extra_ticket() -> None:
    planner = OtpJourneyPlanner(
        base_url="http://otp/otp/gtfs/v1",
        data_version="aucorsa-test",
        transport=httpx.MockTransport(
            lambda request: httpx.Response(
                200,
                json=_otp_same_line_continuation_response(),
            )
        ),
    )

    itinerary = (await planner.search(_request())).itineraries[0]

    assert itinerary.transfers == 0
    assert len(itinerary.fare_notices) == 1
    notice = itinerary.fare_notices[0]
    assert notice.code.value == "SAME_LINE_NEW_TICKET"
    assert notice.stop_name == "Colón Norte"
    assert notice.route_short_name == "E"
    assert notice.additional_ticket_count == 1


@pytest.mark.anyio
async def test_otp_adapter_requests_and_maps_a_direct_bicycle_route() -> None:
    captured_body: dict | None = None

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal captured_body
        captured_body = __import__("json").loads(request.content)
        return httpx.Response(200, json=_otp_bicycle_response())

    planner = OtpJourneyPlanner(
        base_url="http://otp/otp/gtfs/v1",
        data_version="aucorsa-test",
        transport=httpx.MockTransport(handler),
    )

    result = await planner.search(_request(mode=JourneyMode.BICYCLE))

    assert captured_body is not None
    assert captured_body["variables"]["modes"] == {
        "direct": ["BICYCLE"],
        "directOnly": True,
    }
    itinerary = result.itineraries[0]
    assert itinerary.transfers == 0
    assert itinerary.walk_distance_meters == 0
    assert itinerary.legs[0].mode is TransportMode.BICYCLE
    assert itinerary.legs[0].distance_meters == 5200
    assert itinerary.legs[0].stop_count is None


@pytest.mark.anyio
async def test_otp_adapter_requests_a_direct_walk_without_transit_walk_limit() -> None:
    captured_body: dict | None = None

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal captured_body
        captured_body = __import__("json").loads(request.content)
        return httpx.Response(200, json=_otp_walk_response())

    planner = OtpJourneyPlanner(
        base_url="http://otp/otp/gtfs/v1",
        data_version="aucorsa-test",
        transport=httpx.MockTransport(handler),
    )

    result = await planner.search(_request(mode=JourneyMode.WALK))

    assert captured_body is not None
    assert captured_body["variables"]["modes"] == {
        "direct": ["WALK"],
        "directOnly": True,
    }
    itinerary = result.itineraries[0]
    assert itinerary.walk_distance_meters == 2400
    assert itinerary.legs[0].mode is TransportMode.WALK


@pytest.mark.anyio
async def test_otp_adapter_converts_graphql_errors_to_upstream_failure() -> None:
    planner = OtpJourneyPlanner(
        base_url="http://otp/otp/gtfs/v1",
        data_version="test",
        transport=httpx.MockTransport(
            lambda request: httpx.Response(200, json={"errors": [{"message": "bad query"}]})
        ),
    )

    with pytest.raises(JourneyPlannerUnavailableError):
        await planner.search(_request())


def test_google_encoded_polyline_is_decoded() -> None:
    geometry = _decode_polyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

    assert geometry[0].latitude == pytest.approx(38.5)
    assert geometry[0].longitude == pytest.approx(-120.2)
    assert geometry[-1].latitude == pytest.approx(43.252)
    assert geometry[-1].longitude == pytest.approx(-126.45301)


def test_test_fixture_departure_is_timezone_aware() -> None:
    assert isinstance(_request().departure_at, datetime)
    assert _request().departure_at.utcoffset() is not None
