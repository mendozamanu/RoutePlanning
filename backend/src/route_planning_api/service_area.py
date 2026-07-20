from route_planning_api.models import Coordinate

MIN_LATITUDE = 37.75
MAX_LATITUDE = 38.10
MIN_LONGITUDE = -5.04
MAX_LONGITUDE = -4.55


def is_inside_cordoba_service_area(coordinate: Coordinate) -> bool:
    return (
        MIN_LATITUDE <= coordinate.latitude <= MAX_LATITUDE
        and MIN_LONGITUDE <= coordinate.longitude <= MAX_LONGITUDE
    )
