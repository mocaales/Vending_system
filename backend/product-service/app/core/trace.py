"""Sled zahteve (X-Trace-Id): prebere se iz glave ali ustvari in se izpiše v vsak zapis dnevnika."""

import logging
import re
from contextvars import ContextVar, Token
from uuid import uuid4

TRACE_HEADER = "X-Trace-Id"
_VALID_TRACE_ID = re.compile(r"[A-Za-z0-9._-]{1,64}")

# Trace id trenutne zahteve; vsaka zahteva ima svojo vrednost.
_trace_id: ContextVar[str] = ContextVar("trace_id", default="-")


def start_trace(header_value: str | None) -> Token[str]:
    """Uporabi prejeti trace id, če je veljaven, sicer ustvari novega."""
    if header_value and _VALID_TRACE_ID.fullmatch(header_value):
        trace_id = header_value
    else:
        trace_id = str(uuid4())
    return _trace_id.set(trace_id)


def end_trace(token: Token[str]) -> None:
    _trace_id.reset(token)


def current_trace_id() -> str:
    return _trace_id.get()


def setup_logging() -> None:
    """Vsak zapis v dnevniku dobi polje trace_id, npr. `INFO [abc-123] app.request: ...`."""
    default_factory = logging.getLogRecordFactory()

    def factory_with_trace_id(*args, **kwargs) -> logging.LogRecord:
        record = default_factory(*args, **kwargs)
        record.trace_id = current_trace_id()
        return record

    logging.setLogRecordFactory(factory_with_trace_id)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(trace_id)s] %(name)s: %(message)s",
    )
