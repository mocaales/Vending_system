"""Product Service: katalog izdelkov in kategorije."""

from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.v1 import categories, products
from app.core.database import run_migrations
from app.core.setup import setup_core
from app.core.trace import setup_logging


@asynccontextmanager
async def lifespan(app: FastAPI):
    run_migrations()  # tabele ustvarijo samo migracije, ob vsakem zagonu
    yield


setup_logging()

app = FastAPI(
    title="Product Service",
    version="1.0.0",
    description=(
        "Katalog izdelkov in kategorij (docs/03 §6). Poti imajo predpono `/v1`, gateway jih "
        "izpostavi kot `/api/v1/...`. Dovoljeni klicatelji so v `x-callers`."
    ),
    servers=[{"url": "/", "description": "Service-relative; paths already include `/v1`."}],
    docs_url=None,  # storitev je dosegljiva samo znotraj Compose; specifikacija je /openapi.json
    redoc_url=None,
    lifespan=lifespan,
)
setup_core(app)
app.include_router(categories.router)
app.include_router(products.router)
