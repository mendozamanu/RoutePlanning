from typing import Protocol

from route_planning_api.models import JourneySearchRequest, JourneySearchResponse


class DataNotReadyError(RuntimeError):
    """Raised while no validated transit snapshot is active."""


class JourneyPlannerUnavailableError(RuntimeError):
    """Raised when the active journey-planning service cannot answer safely."""


class JourneyPlanner(Protocol):
    async def search(self, request: JourneySearchRequest) -> JourneySearchResponse: ...


class UnavailableJourneyPlanner:
    async def search(self, request: JourneySearchRequest) -> JourneySearchResponse:
        del request
        raise DataNotReadyError("No validated transit snapshot is active")
