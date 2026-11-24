"""Kdaj je zaloga potekla (B4, docs/01 §6.4).

Zaloga je potekla, ko je lokalni datum v Europe/Ljubljana po `expiryDate`; izdelek velja do
konca dneva poteka.
"""

from datetime import UTC, date, datetime
from zoneinfo import ZoneInfo

LJUBLJANA = ZoneInfo("Europe/Ljubljana")


def local_today(now: datetime | None = None) -> date:
    """Današnji datum v Ljubljani; `now` (v UTC) nastavijo testi."""
    return (now or datetime.now(UTC)).astimezone(LJUBLJANA).date()


def is_expired(expiry_date: date, now: datetime | None = None) -> bool:
    return local_today(now) > expiry_date
