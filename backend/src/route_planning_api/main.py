from typing import Annotated

from fastapi import Depends, FastAPI, Query
from fastapi.responses import JSONResponse

from route_planning_api.dependencies import get_journey_planner, get_stop_catalog
from route_planning_api.models import (
    ApiError,
    HealthResponse,
    JourneySearchRequest,
    JourneySearchResponse,
    StopSearchResponse,
)
from route_planning_api.planner import (
    DataNotReadyError,
    JourneyPlanner,
    JourneyPlannerUnavailableError,
)
from route_planning_api.service_area import is_inside_cordoba_service_area
from route_planning_api.stops import StopCatalog, StopCatalogUnavailableError

app = FastAPI(
    title="Route Planning API",
    version="0.1.0",
    description="Stable mobile facade for scheduled AUCORSA journey planning.",
)


@app.get("/health", response_model=HealthResponse, tags=["operations"])
async def health() -> HealthResponse:
    return HealthResponse(status="ok", service="route-planning-api")


@app.post(
    "/v1/journeys/search",
    response_model=JourneySearchResponse,
    responses={400: {"model": ApiError}, 502: {"model": ApiError}, 503: {"model": ApiError}},
    tags=["journeys"],
)
async def search_journeys(
    request: JourneySearchRequest,
    planner: Annotated[JourneyPlanner, Depends(get_journey_planner)],
) -> JourneySearchResponse | JSONResponse:
    if not (
        is_inside_cordoba_service_area(request.origin)
        and is_inside_cordoba_service_area(request.destination)
    ):
        error = ApiError(
            code="OUTSIDE_SERVICE_AREA",
            message="El origen y el destino deben estar dentro del área urbana de Córdoba.",
            retryable=False,
        )
        return JSONResponse(status_code=400, content=error.model_dump(mode="json"))
    try:
        return await planner.search(request)
    except DataNotReadyError:
        error = ApiError(
            code="DATA_NOT_READY",
            message="Los horarios urbanos todavía no están disponibles.",
            retryable=True,
        )
        return JSONResponse(status_code=503, content=error.model_dump(mode="json"))
    except JourneyPlannerUnavailableError:
        error = ApiError(
            code="PLANNER_UNAVAILABLE",
            message="No podemos calcular el trayecto en este momento.",
            retryable=True,
        )
        return JSONResponse(status_code=502, content=error.model_dump(mode="json"))


@app.get(
    "/v1/stops/search",
    response_model=StopSearchResponse,
    responses={503: {"model": ApiError}},
    tags=["stops"],
)
async def search_stops(
    query: Annotated[str, Query(min_length=2, max_length=80)],
    catalog: Annotated[StopCatalog, Depends(get_stop_catalog)],
    limit: Annotated[int, Query(ge=1, le=20)] = 8,
) -> StopSearchResponse | JSONResponse:
    try:
        return await catalog.search(query, limit)
    except StopCatalogUnavailableError:
        error = ApiError(
            code="STOP_CATALOG_UNAVAILABLE",
            message="No podemos consultar las paradas en este momento.",
            retryable=True,
        )
        return JSONResponse(status_code=503, content=error.model_dump(mode="json"))
