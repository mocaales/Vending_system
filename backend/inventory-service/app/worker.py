"""Delavec v ozadju (Polling Publisher): vsake 2 s pošlje čakajoča poročila nizke zaloge."""

import logging
import threading

from sqlalchemy.orm import Session

from app.clients.monitoring import get_monitoring_client
from app.core.database import get_engine
from app.core.trace import end_trace, start_trace
from app.domain.low_stock import send_pending

INTERVAL_SECONDS = 2

logger = logging.getLogger("app.worker")


def start_low_stock_worker() -> threading.Event:
    """Zažene nit delavca; vrne dogodek, ki ga ob zaustavitvi storitve ustavi."""
    stop = threading.Event()
    threading.Thread(target=_run, args=(stop,), name="low-stock-outbox", daemon=True).start()
    return stop


def _run(stop: threading.Event) -> None:
    while not stop.wait(INTERVAL_SECONDS):
        token = start_trace(None)  # vsak prehod ima svoj trace id
        try:
            with Session(get_engine()) as session:
                send_pending(session, get_monitoring_client())
        except Exception:
            logger.exception("Low stock outbox pass failed")  # npr. baza ni dosegljiva
        finally:
            end_trace(token)
