import unicodedata
from typing import Any, Protocol

import httpx

from route_planning_api.models import Coordinate, StopSearchResponse, TransitStop
from route_planning_api.service_area import is_inside_cordoba_service_area

STOPS_QUERY = """
query AllStops {
  stops {
    gtfsId
    name
    lat
    lon
  }
}
"""


class StopCatalogUnavailableError(RuntimeError):
    """Raised when the active stop catalog cannot be queried safely."""


class StopCatalog(Protocol):
    async def search(self, query: str, limit: int) -> StopSearchResponse: ...


class UnavailableStopCatalog:
    async def search(self, query: str, limit: int) -> StopSearchResponse:
        del query, limit
        raise StopCatalogUnavailableError("No validated transit stop catalog is active")


class OtpStopCatalog:
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
        self._stops: tuple[TransitStop, ...] | None = None

    async def search(self, query: str, limit: int) -> StopSearchResponse:
        stops = self._stops
        if stops is None:
            stops = await self._load_stops()
            self._stops = stops

        normalized_query = _normalize(query)
        matches = [stop for stop in stops if normalized_query in _normalize(stop.name)]
        matches.sort(key=lambda stop: _match_sort_key(stop, normalized_query))
        return StopSearchResponse(
            data_version=self._data_version,
            stops=matches[:limit],
        )

    async def _load_stops(self) -> tuple[TransitStop, ...]:
        try:
            async with httpx.AsyncClient(
                transport=self._transport,
                timeout=self._timeout_seconds,
            ) as client:
                response = await client.post(
                    self._base_url,
                    json={"query": STOPS_QUERY, "operationName": "AllStops"},
                    headers={"Accept-Language": "es"},
                )
                response.raise_for_status()
                payload = response.json()
        except (httpx.HTTPError, ValueError) as error:
            raise StopCatalogUnavailableError("OpenTripPlanner is unavailable") from error

        if payload.get("errors"):
            raise StopCatalogUnavailableError("OpenTripPlanner rejected the stops query")
        try:
            raw_stops: list[dict[str, Any]] = payload["data"]["stops"]
            stops = tuple(_transit_stop(stop) for stop in raw_stops)
            return tuple(stop for stop in stops if is_inside_cordoba_service_area(stop.coordinate))
        except (KeyError, TypeError, ValueError) as error:
            raise StopCatalogUnavailableError(
                "OpenTripPlanner returned an invalid stops response"
            ) from error


def _transit_stop(value: dict[str, Any]) -> TransitStop:
    return TransitStop(
        id=value["gtfsId"],
        name=value["name"],
        coordinate=Coordinate(latitude=value["lat"], longitude=value["lon"]),
    )


def _normalize(value: str) -> str:
    decomposed = unicodedata.normalize("NFKD", value.casefold().strip())
    return "".join(character for character in decomposed if not unicodedata.combining(character))


def _match_sort_key(stop: TransitStop, query: str) -> tuple[int, int, str, str]:
    normalized_name = _normalize(stop.name)
    if normalized_name.startswith(query):
        match_type = 0
    elif any(word.startswith(query) for word in normalized_name.split()):
        match_type = 1
    else:
        match_type = 2
    return match_type, len(normalized_name), normalized_name, stop.id
