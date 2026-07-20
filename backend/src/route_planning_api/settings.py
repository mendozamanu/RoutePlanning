import os
from dataclasses import dataclass
from datetime import date


def _optional_date(value: str | None) -> date | None:
    return date.fromisoformat(value) if value else None


def _boolean(value: str | None) -> bool:
    return value is not None and value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    otp_base_url: str | None = None
    transit_data_version: str | None = None
    transit_data_expires_on: date | None = None
    allow_expired_transit_data: bool = False
    otp_timeout_seconds: float = 15.0

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            otp_base_url=os.getenv("OTP_BASE_URL"),
            transit_data_version=os.getenv("TRANSIT_DATA_VERSION"),
            transit_data_expires_on=_optional_date(os.getenv("TRANSIT_DATA_EXPIRES_ON")),
            allow_expired_transit_data=_boolean(os.getenv("ALLOW_EXPIRED_TRANSIT_DATA")),
            otp_timeout_seconds=float(os.getenv("OTP_TIMEOUT_SECONDS", "15")),
        )

    def transit_data_is_active(self, *, today: date | None = None) -> bool:
        if not self.otp_base_url or not self.transit_data_version:
            return False
        if self.transit_data_expires_on is None:
            return False
        return self.allow_expired_transit_data or self.transit_data_expires_on >= (
            today or date.today()
        )
