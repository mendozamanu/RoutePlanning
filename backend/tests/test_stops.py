import httpx
import pytest
from httpx import ASGITransport, AsyncClient

from route_planning_api.dependencies import get_stop_catalog
from route_planning_api.main import app
from route_planning_api.models import Coordinate, StopSearchResponse, TransitStop
from route_planning_api.stops import OtpStopCatalog, StopCatalogUnavailableError


class FakeStopCatalog:
    async def search(self, query: str, limit: int) -> StopSearchResponse:
        assert query == "Rabanales"
        assert limit == 5
        return StopSearchResponse(
            data_version="aucorsa-test-1",
            stops=[
                TransitStop(
                    id="1:808",
                    name="Campus Rabanales",
                    coordinate=Coordinate(latitude=37.9137919, longitude=-4.7176528),
                )
            ],
        )


@pytest.mark.anyio
async def test_stop_search_uses_stable_mobile_contract() -> None:
    app.dependency_overrides[get_stop_catalog] = FakeStopCatalog
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.get(
                "/v1/stops/search", params={"query": "Rabanales", "limit": 5}
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.json() == {
        "data_version": "aucorsa-test-1",
        "stops": [
            {
                "id": "1:808",
                "name": "Campus Rabanales",
                "coordinate": {"latitude": 37.9137919, "longitude": -4.7176528},
            }
        ],
    }


@pytest.mark.anyio
async def test_stop_search_requires_at_least_two_characters() -> None:
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        response = await client.get("/v1/stops/search", params={"query": "R"})

    assert response.status_code == 422


@pytest.mark.anyio
async def test_otp_catalog_matches_accents_and_contains_terms() -> None:
    request_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal request_count
        request_count += 1
        return httpx.Response(
            200,
            json={
                "data": {
                    "stops": [
                        {
                            "gtfsId": "1:60",
                            "name": "Colón Norte",
                            "lat": 37.8910058,
                            "lon": -4.7785671,
                        },
                        {
                            "gtfsId": "1:808",
                            "name": "Campus Rabanales",
                            "lat": 37.9137919,
                            "lon": -4.7176528,
                        },
                        {
                            "gtfsId": "RENFE:18000",
                            "name": "Madrid-Puerta de Atocha",
                            "lat": 40.4066,
                            "lon": -3.6891,
                        },
                    ]
                }
            },
        )

    catalog = OtpStopCatalog(
        base_url="http://otp/otp/gtfs/v1",
        data_version="aucorsa-test-1",
        transport=httpx.MockTransport(handler),
    )

    colon = await catalog.search("colon", 8)
    rabanales = await catalog.search("rabanales", 8)
    outside_area = await catalog.search("atocha", 8)

    assert [stop.id for stop in colon.stops] == ["1:60"]
    assert [stop.id for stop in rabanales.stops] == ["1:808"]
    assert outside_area.stops == []
    assert request_count == 1


@pytest.mark.anyio
async def test_otp_catalog_rejects_graphql_errors() -> None:
    catalog = OtpStopCatalog(
        base_url="http://otp/otp/gtfs/v1",
        data_version="test",
        transport=httpx.MockTransport(
            lambda request: httpx.Response(200, json={"errors": [{"message": "bad query"}]})
        ),
    )

    with pytest.raises(StopCatalogUnavailableError):
        await catalog.search("centro", 8)
