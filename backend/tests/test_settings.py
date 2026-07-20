from datetime import date

from route_planning_api.settings import Settings


def test_transit_snapshot_requires_complete_configuration() -> None:
    assert Settings().transit_data_is_active(today=date(2026, 1, 1)) is False


def test_expired_snapshot_is_blocked_by_default() -> None:
    settings = Settings(
        otp_base_url="http://otp/otp/gtfs/v1",
        transit_data_version="aucorsa-test",
        transit_data_expires_on=date(2026, 3, 31),
    )

    assert settings.transit_data_is_active(today=date(2026, 7, 18)) is False


def test_expired_snapshot_can_only_be_enabled_explicitly_for_development() -> None:
    settings = Settings(
        otp_base_url="http://otp/otp/gtfs/v1",
        transit_data_version="aucorsa-test",
        transit_data_expires_on=date(2026, 3, 31),
        allow_expired_transit_data=True,
    )

    assert settings.transit_data_is_active(today=date(2026, 7, 18)) is True
