"""Smoke step for the alarm e-mail (issue #38): an alarm must reach Mailpit.

Standalone: ``python backend/smoke_email.py`` against a running ``docker compose up`` stack.
The functions are written to be imported into backend/smoke_test.py (#36):

* ``check_alarm_email(client, ...)`` opens TEMPERATURE_HIGH through the gateway device path and
  waits for the mail in Mailpit.
* ``check_low_stock_email(...)`` only waits for the LOW_STOCK mail (the caller makes the stock
  fall below the threshold first; Inventory's LOW_STOCK outbox, #33, raises the alarm).

Environment: BOOTSTRAP_OPERATOR_EMAIL, BOOTSTRAP_OPERATOR_PASSWORD (required), GATEWAY_URL
(default http://localhost:8080), MAILPIT_URL (default http://localhost:8025).
"""

from __future__ import annotations

import os
import sys
import time
import uuid
from datetime import UTC, datetime, timedelta

import httpx

GATEWAY_URL = os.environ.get("GATEWAY_URL", "http://localhost:8080")
MAILPIT_URL = os.environ.get("MAILPIT_URL", "http://localhost:8025")


def _check(response: httpx.Response, expected: int | tuple[int, ...]) -> httpx.Response:
    codes = (expected,) if isinstance(expected, int) else expected
    if response.status_code not in codes:
        raise AssertionError(
            f"{response.request.method} {response.request.url.path}: "
            f"expected {codes}, got {response.status_code}"
        )
    return response


def login(client: httpx.Client, email: str, password: str) -> dict[str, str]:
    """Logs in through the gateway and returns the Authorization header."""
    response = _check(
        client.post(
            f"{GATEWAY_URL}/api/v1/auth/login", json={"email": email, "password": password}
        ),
        200,
    )
    return {"Authorization": f"Bearer {response.json()['accessToken']}"}


def create_machine(client: httpx.Client, auth: dict[str, str], name: str) -> tuple[str, str]:
    """Creates a machine; returns (machineId, machineKey)."""
    body = {
        "code": name[:32],
        "name": name,
        "address": "Smoke test",
        "latitude": 46.5547,
        "longitude": 15.6459,
        "slots": [{"slotCode": "A1"}],
    }
    created = _check(
        client.post(f"{GATEWAY_URL}/api/v1/machines", json=body, headers=auth), 201
    ).json()
    machine_id = created["id"]
    key = created.get("machineKey")
    if key is None:
        key = _check(
            client.post(f"{GATEWAY_URL}/api/v1/machines/{machine_id}/keys", headers=auth), 201
        ).json()["machineKey"]
    return machine_id, key


def send_measurement(
    client: httpx.Client, machine_id: str, key: str, observed_at: datetime, temperature: str
) -> None:
    body = {
        "measurementId": str(uuid.uuid4()),
        "machineId": machine_id,
        "observedAt": observed_at.isoformat().replace("+00:00", "Z"),
        "temperatureC": temperature,
        "doorState": "CLOSED",
    }
    _check(
        client.post(
            f"{GATEWAY_URL}/api/v1/device/measurements", json=body, headers={"X-Machine-Key": key}
        ),
        (200, 201),
    )


def wait_for_mail(
    client: httpx.Client,
    subject_contains: list[str],
    body_contains: list[str] | None = None,
    timeout: float = 90.0,
) -> dict:
    """Polls Mailpit until a message whose subject has every fragment (and body has every
    fragment in ``body_contains``) arrives; returns the Mailpit message detail."""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        listing = _check(client.get(f"{MAILPIT_URL}/api/v1/messages?limit=100"), 200).json()
        for item in listing.get("messages", []):
            subject = item.get("Subject", "")
            if not all(fragment in subject for fragment in subject_contains):
                continue
            detail = _check(client.get(f"{MAILPIT_URL}/api/v1/message/{item['ID']}"), 200).json()
            text = detail.get("Text", "")
            if all(fragment in text for fragment in body_contains or []):
                return detail
        time.sleep(2)
    raise AssertionError(f"no e-mail with subject {subject_contains} in Mailpit within {timeout}s")


def check_alarm_email(client: httpx.Client, email: str, password: str) -> None:
    """TEMPERATURE_HIGH through the gateway device path -> mail in Mailpit; cleans up."""
    auth = login(client, email, password)
    name = f"smoke-email-{uuid.uuid4().hex[:8]}"
    machine_id, key = create_machine(client, auth, name)
    try:
        now = datetime.now(UTC).replace(microsecond=0)
        send_measurement(client, machine_id, key, now - timedelta(seconds=20), "9.50")
        send_measurement(client, machine_id, key, now - timedelta(seconds=10), "9.50")
        mail = wait_for_mail(client, ["TEMPERATURE_HIGH", name], [machine_id])
        print(f"OK alarm e-mail received: {mail.get('Subject')}")
    finally:
        _check(client.delete(f"{GATEWAY_URL}/api/v1/machines/{machine_id}", headers=auth), 204)


def check_low_stock_email(
    client: httpx.Client, machine_name: str, product_name: str, timeout: float = 90.0
) -> None:
    """Waits for the LOW_STOCK mail of a machine; it must list the product by name.

    Call after the main flow made the stock fall below the threshold (needs #33)."""
    mail = wait_for_mail(client, ["LOW_STOCK", machine_name], [product_name], timeout)
    print(f"OK low-stock e-mail received: {mail.get('Subject')}")


def main() -> int:
    email = os.environ.get("BOOTSTRAP_OPERATOR_EMAIL")
    password = os.environ.get("BOOTSTRAP_OPERATOR_PASSWORD")
    if not email or not password:
        print("Set BOOTSTRAP_OPERATOR_EMAIL and BOOTSTRAP_OPERATOR_PASSWORD.", file=sys.stderr)
        return 2
    with httpx.Client(timeout=10.0) as client:
        check_alarm_email(client, email, password)
    return 0


if __name__ == "__main__":
    sys.exit(main())
