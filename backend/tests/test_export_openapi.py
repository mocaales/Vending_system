"""Izvoz in preverjanje openapi.yaml (issue #4); storitve nadomesti httpx.MockTransport."""

import export_openapi
import httpx
import pytest
import yaml


def spec(title):
    return {"openapi": "3.1.0", "info": {"title": title, "version": "1.0.0"}, "paths": {}}


@pytest.fixture
def backend(tmp_path, monkeypatch):
    """Začasna mapa backend/ z mapami storitev in gatewayevim openapi.yaml."""
    for name in export_openapi.SERVICES:
        (tmp_path / f"{name}-service").mkdir()
    (tmp_path / "gateway").mkdir()
    (tmp_path / "gateway" / "openapi.yaml").write_text("openapi: 3.1.0\n", encoding="utf-8")
    monkeypatch.setattr(export_openapi, "BACKEND", tmp_path)
    return tmp_path


@pytest.fixture
def services():
    """Lažne storitve: ime gostitelja -> specifikacija; manjkajoča storitev ne odgovarja."""
    fake = {"specs": {f"{name}-service": spec(name) for name in export_openapi.SERVICES}}
    fake["requests"] = []

    def handle(request: httpx.Request) -> httpx.Response:
        fake["requests"].append(str(request.url))
        if request.url.host not in fake["specs"]:
            raise httpx.ConnectError("not running")
        return httpx.Response(200, json=fake["specs"][request.url.host])

    fake["transport"] = httpx.MockTransport(handle)
    return fake


def run(services, *args):
    return export_openapi.main(list(args), transport=services["transport"])


def read(backend, name):
    return (backend / f"{name}-service" / "openapi.yaml").read_text(encoding="utf-8")


def test_export_writes_yaml_for_every_service(backend, services):
    assert run(services) == 0

    for name in export_openapi.SERVICES:
        assert yaml.safe_load(read(backend, name)) == spec(name)
    assert "http://product-service:8000/openapi.json" in services["requests"]


def test_gateway_spec_is_never_touched(backend, services):
    run(services)

    assert (backend / "gateway" / "openapi.yaml").read_text(encoding="utf-8") == "openapi: 3.1.0\n"
    assert not any("gateway" in url for url in services["requests"])


def test_gateway_cannot_be_exported(services):
    with pytest.raises(SystemExit) as error:
        run(services, "gateway")

    assert error.value.code == 2


def test_repeated_export_does_not_change_files(backend, services):
    run(services)
    before = read(backend, "product")

    run(services)

    assert read(backend, "product") == before


def test_same_content_in_other_format_is_kept(backend, services):
    hand_written = "# ročno\nopenapi: 3.1.0\ninfo: {title: product, version: 1.0.0}\npaths: {}\n"
    (backend / "product-service" / "openapi.yaml").write_text(hand_written, encoding="utf-8")

    assert run(services, "product") == 0

    assert read(backend, "product") == hand_written


def test_export_of_selected_services_only(backend, services):
    run(services, "inventory")

    assert (backend / "inventory-service" / "openapi.yaml").exists()
    assert not (backend / "product-service" / "openapi.yaml").exists()


def test_check_passes_when_specs_match(backend, services):
    run(services)

    assert run(services, "--check") == 0


def test_check_fails_and_writes_nothing_when_spec_differs(backend, services):
    run(services)
    before = read(backend, "sales")
    services["specs"]["sales-service"]["paths"] = {"/v1/sales": {}}

    assert run(services, "--check") == 1

    assert read(backend, "sales") == before


def test_check_fails_when_yaml_is_missing(backend, services):
    assert run(services, "--check", "machine") == 1

    assert not (backend / "machine-service" / "openapi.yaml").exists()


def test_unreachable_service_is_an_error(backend, services, capsys):
    del services["specs"]["monitoring-service"]

    assert run(services) == 1

    assert "monitoring: ni dosegljiva" in capsys.readouterr().out
    assert (backend / "product-service" / "openapi.yaml").exists()  # ostale se vseeno izvozijo


def test_service_url_from_environment(backend, services, monkeypatch):
    monkeypatch.setenv("PRODUCT_URL", "http://localhost:8001")
    services["specs"]["localhost"] = spec("product")

    assert run(services, "product") == 0

    assert services["requests"] == ["http://localhost:8001/openapi.json"]
