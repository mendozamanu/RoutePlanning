from datetime import UTC, datetime

import pytest
from httpx import ASGITransport, AsyncClient

from route_planning_api.dependencies import get_journey_planner
from route_planning_api.main import app
from route_planning_api.models import (
    Coordinate,
    JourneyLeg,
    JourneyOption,
    JourneySearchRequest,
    JourneySearchResponse,
    RealtimeStatus,
    TransportMode,
)

VALID_REQUEST = {
    "origin": {"latitude": 37.8882, "longitude": -4.7794},
    "destination": {"latitude": 37.9138, "longitude": -4.7211},
    "departure_at": "2026-07-20T08:00:00+02:00",
    "profile": "FASTEST",
}


class FakeJourneyPlanner:
    async def search(self, request: JourneySearchRequest) -> JourneySearchResponse:
        starts_at = request.departure_at
        ends_at = starts_at.replace(minute=30)
        return JourneySearchResponse(
            data_version="aucorsa-test-1",
            generated_at=datetime.now(UTC),
            realtime_status=RealtimeStatus.SCHEDULED_ONLY,
            itineraries=[
                JourneyOption(
                    id="option-1",
                    starts_at=starts_at,
                    ends_at=ends_at,
                    duration_seconds=1_800,
                    transfers=0,
                    walk_distance_meters=450,
                    legs=[
                        JourneyLeg(
                            mode=TransportMode.BUS,
                            starts_at=starts_at,
                            ends_at=ends_at,
                            from_name="Centro",
                            to_name="Rabanales",
                            route_id="aucorsa:line-e",
                            route_short_name="E",
                            agency_id="AUCORSA:aucorsa",
                            agency_name="AUCORSA",
                            headsign="Campus de Rabanales",
                            distance_meters=7_100,
                            stop_count=8,
                            geometry=[
                                Coordinate(latitude=37.8882, longitude=-4.7794),
                                Coordinate(latitude=37.9138, longitude=-4.7211),
                            ],
                        )
                    ],
                )
            ],
        )


@pytest.mark.anyio
async def test_search_returns_data_not_ready_until_feed_is_active() -> None:
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        response = await client.post("/v1/journeys/search", json=VALID_REQUEST)

    assert response.status_code == 503
    assert response.json()["code"] == "DATA_NOT_READY"
    assert response.json()["retryable"] is True


@pytest.mark.anyio
async def test_search_uses_stable_mobile_contract() -> None:
    app.dependency_overrides[get_journey_planner] = FakeJourneyPlanner
    try:
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.post("/v1/journeys/search", json=VALID_REQUEST)
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    payload = response.json()
    assert payload["data_version"] == "aucorsa-test-1"
    assert payload["realtime_status"] == "SCHEDULED_ONLY"
    assert payload["itineraries"][0]["fare_notices"] == []
    assert payload["itineraries"][0]["legs"][0]["route_short_name"] == "E"
    assert payload["itineraries"][0]["legs"][0]["agency_id"] == "AUCORSA:aucorsa"
    assert payload["itineraries"][0]["legs"][0]["agency_name"] == "AUCORSA"
    assert payload["itineraries"][0]["legs"][0]["headsign"] == "Campus de Rabanales"
    assert payload["itineraries"][0]["legs"][0]["distance_meters"] == 7100
    assert payload["itineraries"][0]["legs"][0]["stop_count"] == 8


@pytest.mark.anyio
async def test_search_accepts_bicycle_as_a_stable_journey_mode() -> None:
    class BicycleAwarePlanner(FakeJourneyPlanner):
        async def search(self, request: JourneySearchRequest) -> JourneySearchResponse:
            assert request.mode.value == "BICYCLE"
            result = await super().search(request)
            result.itineraries[0].legs[0].mode = TransportMode.BICYCLE
            result.itineraries[0].legs[0].route_id = None
            result.itineraries[0].legs[0].route_short_name = None
            return result

    app.dependency_overrides[get_journey_planner] = BicycleAwarePlanner
    try:
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.post(
                "/v1/journeys/search",
                json={**VALID_REQUEST, "mode": "BICYCLE"},
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.json()["itineraries"][0]["legs"][0]["mode"] == "BICYCLE"


@pytest.mark.anyio
async def test_search_accepts_walking_as_a_stable_journey_mode() -> None:
    class WalkingAwarePlanner(FakeJourneyPlanner):
        async def search(self, request: JourneySearchRequest) -> JourneySearchResponse:
            assert request.mode.value == "WALK"
            result = await super().search(request)
            result.itineraries[0].legs[0].mode = TransportMode.WALK
            result.itineraries[0].legs[0].route_id = None
            result.itineraries[0].legs[0].route_short_name = None
            return result

    app.dependency_overrides[get_journey_planner] = WalkingAwarePlanner
    try:
        async with AsyncClient(
            transport=ASGITransport(app=app),
            base_url="http://test",
        ) as client:
            response = await client.post(
                "/v1/journeys/search",
                json={**VALID_REQUEST, "mode": "WALK"},
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.json()["itineraries"][0]["legs"][0]["mode"] == "WALK"


@pytest.mark.anyio
async def test_search_rejects_departure_without_timezone() -> None:
    request = {**VALID_REQUEST, "departure_at": "2026-07-20T08:00:00"}

    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        response = await client.post("/v1/journeys/search", json=request)

    assert response.status_code == 422


@pytest.mark.anyio
async def test_search_rejects_coordinates_outside_cordoba_service_area() -> None:
    request = {
        **VALID_REQUEST,
        "origin": {"latitude": 40.4168, "longitude": -3.7038},
    }

    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        response = await client.post("/v1/journeys/search", json=request)

    assert response.status_code == 400
    assert response.json() == {
        "code": "OUTSIDE_SERVICE_AREA",
        "message": "El origen y el destino deben estar dentro del área urbana de Córdoba.",
        "retryable": False,
    }
