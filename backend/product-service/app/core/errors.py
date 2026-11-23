"""Enotno telo napake {code, message, traceId} (docs/03 §2); `details` samo pri VALIDATION_ERROR."""

from http import HTTPStatus

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from app.core.trace import current_trace_id


class ApiError(Exception):
    """Napaka, ki jo storitev vrne namerno, npr. ApiError(404, "PRODUCT_NOT_FOUND", "...")."""

    def __init__(self, status: int, code: str, message: str):
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


def error_response(
    status: int, code: str, message: str, details: list[dict] | None = None
) -> JSONResponse:
    body = {"code": code, "message": message, "traceId": current_trace_id()}
    if details is not None:
        body["details"] = details
    return JSONResponse(status_code=status, content=body)


async def handle_api_error(request: Request, error: ApiError) -> JSONResponse:
    return error_response(error.status, error.code, error.message)


async def handle_validation_error(request: Request, error: RequestValidationError) -> JSONResponse:
    """Neberljiv JSON ali napačen tip v poti/poizvedbi je 400; neveljavno polje je 422."""
    problems = error.errors()
    if any(_is_bad_request(problem) for problem in problems):
        return error_response(400, "BAD_REQUEST", "Malformed request.")

    details = [
        {"field": _field_name(problem["loc"]), "issue": problem["msg"]} for problem in problems
    ]
    return error_response(422, "VALIDATION_ERROR", "Request validation failed.", details)


async def handle_http_error(request: Request, error: HTTPException) -> JSONResponse:
    """Napake ogrodja, npr. neznana pot (404) ali napačna metoda (405)."""
    code = "NOT_FOUND" if error.status_code == 404 else "BAD_REQUEST"
    return error_response(error.status_code, code, HTTPStatus(error.status_code).phrase + ".")


def _is_bad_request(problem: dict) -> bool:
    if problem["type"] == "json_invalid":
        return True
    in_url = problem["loc"][0] in ("path", "query")
    return in_url and problem["type"].endswith("_parsing")


def _field_name(location: tuple) -> str:
    """("body", "name") -> "name"; ("query", "limit") -> "limit"."""
    return ".".join(str(part) for part in location[1:]) or str(location[0])
