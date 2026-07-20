from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, model_validator


class ApiModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class Coordinate(ApiModel):
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)


class JourneyProfile(StrEnum):
    FASTEST = "FASTEST"
    FEWER_TRANSFERS = "FEWER_TRANSFERS"
    LESS_WALKING = "LESS_WALKING"
    ACCESSIBLE = "ACCESSIBLE"


class JourneyMode(StrEnum):
    TRANSIT = "TRANSIT"
    BICYCLE = "BICYCLE"
    WALK = "WALK"


class RealtimeStatus(StrEnum):
    SCHEDULED_ONLY = "SCHEDULED_ONLY"
    REALTIME = "REALTIME"


class TransportMode(StrEnum):
    WALK = "WALK"
    BUS = "BUS"
    RAIL = "RAIL"
    BICYCLE = "BICYCLE"


class FareNoticeCode(StrEnum):
    SAME_LINE_NEW_TICKET = "SAME_LINE_NEW_TICKET"


class JourneySearchRequest(ApiModel):
    origin: Coordinate
    destination: Coordinate
    departure_at: datetime
    mode: JourneyMode = JourneyMode.TRANSIT
    profile: JourneyProfile = JourneyProfile.FASTEST
    max_walk_meters: int = Field(default=1_500, ge=0, le=10_000)
    max_transfers: int = Field(default=3, ge=0, le=5)

    @model_validator(mode="after")
    def require_timezone(self) -> "JourneySearchRequest":
        if self.departure_at.tzinfo is None or self.departure_at.utcoffset() is None:
            raise ValueError("departure_at must include a timezone offset")
        return self


class JourneyLeg(ApiModel):
    mode: TransportMode
    starts_at: datetime
    ends_at: datetime
    from_name: str = Field(min_length=1, max_length=200)
    to_name: str = Field(min_length=1, max_length=200)
    route_id: str | None = Field(default=None, max_length=100)
    route_short_name: str | None = Field(default=None, max_length=50)
    agency_id: str | None = Field(default=None, max_length=100)
    agency_name: str | None = Field(default=None, max_length=200)
    headsign: str | None = Field(default=None, max_length=200)
    distance_meters: int | None = Field(default=None, ge=0)
    stop_count: int | None = Field(default=None, ge=1)
    geometry: list[Coordinate] = Field(default_factory=list)
    accessibility_confirmed: bool | None = None


class JourneyFareNotice(ApiModel):
    code: FareNoticeCode
    stop_name: str = Field(min_length=1, max_length=200)
    route_short_name: str | None = Field(default=None, max_length=50)
    additional_ticket_count: int = Field(default=1, ge=1)


class JourneyOption(ApiModel):
    id: str = Field(min_length=1, max_length=200)
    starts_at: datetime
    ends_at: datetime
    duration_seconds: int = Field(ge=0)
    transfers: int = Field(ge=0)
    walk_distance_meters: int = Field(ge=0)
    fare_notices: list[JourneyFareNotice] = Field(default_factory=list)
    legs: list[JourneyLeg] = Field(min_length=1)


class JourneySearchResponse(ApiModel):
    data_version: str = Field(min_length=1, max_length=200)
    generated_at: datetime
    realtime_status: RealtimeStatus
    itineraries: list[JourneyOption]


class TransitStop(ApiModel):
    id: str = Field(min_length=1, max_length=100)
    name: str = Field(min_length=1, max_length=200)
    coordinate: Coordinate


class StopSearchResponse(ApiModel):
    data_version: str = Field(min_length=1, max_length=200)
    stops: list[TransitStop]


class ApiError(ApiModel):
    code: str = Field(pattern=r"^[A-Z][A-Z0-9_]+$")
    message: str
    retryable: bool


class HealthResponse(ApiModel):
    status: str
    service: str
