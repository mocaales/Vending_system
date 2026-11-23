"""Poveže dele predloge `core` z aplikacijo FastAPI."""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException

from app.core import health
from app.core.errors import (
    ApiError,
    error_response,
    handle_api_error,
    handle_http_error,
    handle_validation_error,
)
from app.core.trace import TRACE_HEADER, current_trace_id, end_trace, start_trace

logger = logging.getLogger("app.request")


def setup_core(app: FastAPI) -> None:
    app.add_exception_handler(ApiError, handle_api_error)
    app.add_exception_handler(RequestValidationError, handle_validation_error)
    app.add_exception_handler(HTTPException, handle_http_error)
    app.middleware("http")(trace_requests)
    app.include_router(health.router)


async def trace_requests(request: Request, call_next):
    """Za vsako zahtevo: nastavi trace id, ujame nepričakovane napake (500) in zapiše dnevnik."""
    token = start_trace(request.headers.get(TRACE_HEADER))
    try:
        try:
            response = await call_next(request)
        except Exception:
            logger.exception("Unexpected error")
            response = error_response(500, "INTERNAL_ERROR", "Unexpected server error.")

        response.headers[TRACE_HEADER] = current_trace_id()
        if not request.url.path.startswith("/health"):
            logger.info("%s %s -> %s", request.method, request.url.path, response.status_code)
        return response
    finally:
        end_trace(token)
