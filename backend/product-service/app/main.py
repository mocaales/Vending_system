"""Product Service: katalog izdelkov in kategorije."""

from contextlib import asynccontextmanager

from fastapi import FastAPI

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
    docs_url=None,  # storitev je dosegljiva samo znotraj Compose; specifikacija je /openapi.json
    redoc_url=None,
    lifespan=lifespan,
)
setup_core(app)
