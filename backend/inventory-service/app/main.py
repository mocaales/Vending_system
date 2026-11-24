"""Inventory Service: zaloga v predalih avtomatov."""

from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.v1 import stock
from app.core.database import run_migrations
from app.core.setup import setup_core
from app.core.trace import setup_logging


@asynccontextmanager
async def lifespan(app: FastAPI):
    run_migrations()  # tabele ustvarijo samo migracije, ob vsakem zagonu
    yield


setup_logging()

app = FastAPI(
    title="Inventory Service",
    version="1.0.0",
    description=(
        "Zaloga v predalih avtomatov (docs/03 §7). Poti imajo predpono `/v1`, gateway jih "
        "izpostavi kot `/api/v1/...`. Dovoljeni klicatelji so v `x-callers`. Statične poti "
        "(`movements`, `refill`, `expired`, `product/...`) so registrirane pred potmi z "
        "`{machine_id}`."
    ),
    servers=[{"url": "/", "description": "Service-relative; paths already include `/v1`."}],
    docs_url=None,  # storitev je dosegljiva samo znotraj Compose; specifikacija je /openapi.json
    redoc_url=None,
    lifespan=lifespan,
)
setup_core(app)
app.include_router(stock.router)
