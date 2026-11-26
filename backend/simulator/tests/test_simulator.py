"""Simulator: dogodki po pogodbi, ponavljanje neposlanih in izguba povezave (issue #30)."""

import json
import random
from datetime import UTC, datetime, timedelta
from uuid import UUID

import httpx
import pytest
from simulator.config import ConfigError, load_machines, parse_settings
from simulator.device import Simulator
from simulator.events import measurement_event, sale_event
from simulator.sender import MEASUREMENTS_PATH, SALES_PATH, Sender

NOW = datetime(2026, 11, 26, 8, 30, tzinfo=UTC)
SLOT = {
    "slotId": "A1",
    "productId": "c3a8f7d4-12ab-4e55-b6c9-5d0e8a1f2b73",
    "unitPrice": "1.20",
    "currency": "EUR",
}
MACHINE = {
    "machineId": "0d9c4a52-3b1e-4f6a-8c27-91e5a7b3d410",
    "code": "MB-FERI",
    "machineKey": "mk_secret",
    "slots": [SLOT],
}


@pytest.fixture
def gateway():
    """Lažni gateway: zapiše prejete dogodke; `status` je odgovor, `down` pomeni brez odgovora."""
    fake = {"received": [], "status": 201, "down": False}

    def handle(request: httpx.Request) -> httpx.Response:
        if fake["down"]:
            raise httpx.ConnectError("gateway is down")
        fake["received"].append(
            (request.url.path, request.headers["X-Machine-Key"], json.loads(request.content))
        )
        return httpx.Response(fake["status"], json={})

    fake["sender"] = Sender(
        httpx.Client(base_url="http://gateway", transport=httpx.MockTransport(handle))
    )
    return fake


def simulator(gateway, **changes):
    settings = parse_settings([])
    for name, value in changes.items():
        setattr(settings, name, value)
    return Simulator([dict(MACHINE)], gateway["sender"], settings, NOW, random.Random(1))


# --- dogodki ---------------------------------------------------------------------------


def test_sale_event_follows_contract():
    event = sale_event(MACHINE["machineId"], SLOT, "CARD", NOW)

    assert UUID(event.pop("saleId"))
    assert event == {
        "machineId": MACHINE["machineId"],
        "slotId": "A1",
        "productId": SLOT["productId"],
        "quantity": 1,
        "unitPricePaid": "1.20",
        "currency": "EUR",
        "paymentMethod": "CARD",
        "occurredAt": "2026-11-26T08:30:00Z",
    }


def test_measurement_event_follows_contract():
    event = measurement_event(MACHINE["machineId"], 4.26, "CLOSED", NOW)

    assert UUID(event.pop("measurementId"))
    assert event == {
        "machineId": MACHINE["machineId"],
        "observedAt": "2026-11-26T08:30:00Z",
        "temperatureC": "4.3",
        "doorState": "CLOSED",
    }


def test_each_event_gets_new_id():
    first = sale_event(MACHINE["machineId"], SLOT, "CASH", NOW)
    second = sale_event(MACHINE["machineId"], SLOT, "CASH", NOW)

    assert first["saleId"] != second["saleId"]


# --- pošiljanje -------------------------------------------------------------------------


def test_delivered_event_leaves_queue(gateway):
    gateway["sender"].add(SALES_PATH, "mk_secret", {"saleId": "1"})

    gateway["sender"].flush()

    assert gateway["received"] == [(SALES_PATH, "mk_secret", {"saleId": "1"})]
    assert gateway["sender"].pending == []


@pytest.mark.parametrize("status", [503, 504])
def test_unavailable_gateway_resends_same_event(gateway, status):
    gateway["sender"].add(SALES_PATH, "mk_secret", {"saleId": "1"})
    gateway["status"] = status
    gateway["sender"].flush()

    gateway["status"] = 201
    gateway["sender"].flush()

    assert [event for _, _, event in gateway["received"]] == [{"saleId": "1"}, {"saleId": "1"}]
    assert gateway["sender"].pending == []


def test_network_error_keeps_all_events_in_order(gateway):
    gateway["sender"].add(MEASUREMENTS_PATH, "mk_secret", {"measurementId": "1"})
    gateway["sender"].add(SALES_PATH, "mk_secret", {"saleId": "2"})
    gateway["down"] = True

    gateway["sender"].flush()

    assert [event for _, _, event in gateway["sender"].pending] == [
        {"measurementId": "1"},
        {"saleId": "2"},
    ]


def test_rejected_event_is_not_retried(gateway):
    gateway["sender"].add(SALES_PATH, "mk_secret", {"saleId": "1"})
    gateway["status"] = 422

    gateway["sender"].flush()

    assert gateway["sender"].pending == []


# --- zanka simulatorja -------------------------------------------------------------------


def test_first_measurement_immediately_and_sale_after_interval(gateway):
    sim = simulator(gateway, sale_every=30, measurement_every=60)

    sim.step(NOW)
    sim.step(NOW + timedelta(seconds=30))

    paths = [path for path, _, _ in gateway["received"]]
    assert paths == [MEASUREMENTS_PATH, SALES_PATH]
    assert all(key == "mk_secret" for _, key, _ in gateway["received"])


def test_temperature_and_door_from_settings(gateway):
    sim = simulator(gateway, temperature=9.0, door="OPEN")

    sim.step(NOW)

    measurement = gateway["received"][0][2]
    assert 8.7 <= float(measurement["temperatureC"]) <= 9.3
    assert measurement["doorState"] == "OPEN"


def test_connection_loss_queues_events_and_sends_them_later(gateway):
    sim = simulator(gateway, measurement_every=10, offline_after=0, offline_for=25)

    for second in (0, 10, 20):
        sim.step(NOW + timedelta(seconds=second))
    assert gateway["received"] == []
    assert len(sim.sender.pending) == 3

    sim.step(NOW + timedelta(seconds=25))  # povezava je spet na voljo

    assert len(gateway["received"]) == 3
    observed = [event["observedAt"] for _, _, event in gateway["received"]]
    assert observed == sorted(observed)


# --- nastavitve in avtomati --------------------------------------------------------------


@pytest.fixture
def machines_file(tmp_path):
    path = tmp_path / "machines.local.json"
    other = dict(MACHINE, machineId="7c893d84-bdec-44f3-83d1-f5b82aad80c6", code="LJ-CENTER")
    path.write_text(json.dumps({"machines": [MACHINE, other]}), encoding="utf-8")
    return path


def test_loads_all_machines_from_seed_file(machines_file):
    assert [m["code"] for m in load_machines(machines_file)] == ["MB-FERI", "LJ-CENTER"]


def test_selects_one_machine_and_replaces_key(machines_file):
    machines = load_machines(machines_file, "LJ-CENTER", "mk_other")

    assert [(m["code"], m["machineKey"]) for m in machines] == [("LJ-CENTER", "mk_other")]


def test_unknown_machine_or_missing_file(machines_file, tmp_path):
    with pytest.raises(ConfigError):
        load_machines(machines_file, "NI-GA")
    with pytest.raises(ConfigError):
        load_machines(tmp_path / "missing.json")


def test_settings_from_environment_and_command_line(monkeypatch):
    monkeypatch.setenv("SIM_TEMPERATURE", "9.5")
    monkeypatch.setenv("GATEWAY_URL", "http://gateway:8000")

    settings = parse_settings(["--door", "OPEN", "--offline-after", "60", "--offline-for", "400"])

    assert (settings.temperature, settings.door, settings.gateway) == (
        9.5,
        "OPEN",
        "http://gateway:8000",
    )
    assert (settings.offline_after, settings.offline_for) == (60, 400)


def test_key_only_with_machine():
    with pytest.raises(SystemExit):
        parse_settings(["--key", "mk_other"])
