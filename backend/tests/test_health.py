import pytest
from httpx import ASGITransport, AsyncClient

from route_planning_api.main import app


@pytest.mark.anyio
async def test_health_reports_service_ready() -> None:
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        response = await client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "route-planning-api"}
