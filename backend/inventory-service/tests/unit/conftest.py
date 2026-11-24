"""Testna aplikacija s predlogo `core` in nekaj poskusnimi potmi (brez baze)."""

from typing import Annotated
from uuid import UUID

import pytest
from app.core.errors import ApiError
from app.core.schemas import Page, PageParams, StrictModel, page_params
from app.core.security import Caller, allowed_callers
from app.core.setup import setup_core
from app.core.trace import setup_logging
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

TOKENS = {
    "GATEWAY_SERVICE_TOKEN": "gateway-token",
    "SALES_SERVICE_TOKEN": "sales-token",
    "INVENTORY_SERVICE_TOKEN": "inventory-token",
}


class ItemIn(StrictModel):
    name: str


def create_test_app() -> FastAPI:
    app = FastAPI()
    setup_core(app)

    @app.get("/test/items/{item_id}")
    def get_item(item_id: UUID) -> dict:
        return {"id": str(item_id)}

    @app.post("/test/items", status_code=201)
    def create_item(item: ItemIn) -> ItemIn:
        return item

    @app.get("/test/items")
    def list_items(params: Annotated[PageParams, Depends(page_params)]) -> Page[ItemIn]:
        return Page(items=[], total=0, limit=params.limit, offset=params.offset)

    @app.get("/test/conflict")
    def conflict() -> dict:
        raise ApiError(409, "SOMETHING_TAKEN", "Something is taken.")

    @app.get("/test/crash")
    def crash() -> dict:
        raise RuntimeError("boom")

    @app.get("/test/sales-only")
    def sales_only(caller: Annotated[Caller, Depends(allowed_callers("sales"))]) -> dict:
        return {"caller": caller.name}

    @app.get("/test/operator-or-sales")
    def operator_or_sales(
        caller: Annotated[Caller, Depends(allowed_callers("sales", operator=True))],
    ) -> dict:
        return {"caller": caller.name, "operatorId": str(caller.operator_id)}

    return app


@pytest.fixture(scope="session", autouse=True)
def logging_with_trace_id():
    setup_logging()


@pytest.fixture
def client(monkeypatch) -> TestClient:
    for variable, token in TOKENS.items():
        monkeypatch.setenv(variable, token)
    return TestClient(create_test_app())
