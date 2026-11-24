"""Pravilo poteka B4: zaloga je potekla, ko je lokalni datum v Ljubljani po expiryDate."""

from datetime import UTC, date, datetime

from app.domain.expiry import is_expired, local_today

EXPIRY = date(2026, 11, 30)


def test_valid_until_end_of_expiry_day_in_winter():
    # 22:59 UTC je pozimi (UTC+1) še 23:59 v Ljubljani na dan poteka.
    assert not is_expired(EXPIRY, datetime(2026, 11, 30, 22, 59, tzinfo=UTC))


def test_expired_after_local_midnight_in_winter():
    # 23:00 UTC je pozimi že 00:00 naslednji dan v Ljubljani.
    assert is_expired(EXPIRY, datetime(2026, 11, 30, 23, 0, tzinfo=UTC))


def test_summer_time_is_two_hours_ahead():
    # Poleti (UTC+2) je 22:00 UTC že polnoč naslednjega dne.
    assert local_today(datetime(2026, 7, 14, 21, 59, tzinfo=UTC)) == date(2026, 7, 14)
    assert local_today(datetime(2026, 7, 14, 22, 0, tzinfo=UTC)) == date(2026, 7, 15)


def test_not_expired_before_expiry_day():
    assert not is_expired(EXPIRY, datetime(2026, 11, 1, 12, 0, tzinfo=UTC))
