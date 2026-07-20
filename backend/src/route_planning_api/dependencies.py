from functools import lru_cache

from route_planning_api.otp import OtpJourneyPlanner
from route_planning_api.planner import JourneyPlanner, UnavailableJourneyPlanner
from route_planning_api.settings import Settings
from route_planning_api.stops import OtpStopCatalog, StopCatalog, UnavailableStopCatalog


@lru_cache
def get_journey_planner() -> JourneyPlanner:
    settings = Settings.from_environment()
    if not settings.transit_data_is_active():
        return UnavailableJourneyPlanner()
    return OtpJourneyPlanner(
        base_url=settings.otp_base_url,
        data_version=settings.transit_data_version,
        timeout_seconds=settings.otp_timeout_seconds,
    )


@lru_cache
def get_stop_catalog() -> StopCatalog:
    settings = Settings.from_environment()
    if not settings.transit_data_is_active():
        return UnavailableStopCatalog()
    return OtpStopCatalog(
        base_url=settings.otp_base_url,
        data_version=settings.transit_data_version,
        timeout_seconds=settings.otp_timeout_seconds,
    )
