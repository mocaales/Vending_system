"""Preverjanje klicatelja z notranjim žetonom (X-Service-Token).

Klicatelja prepoznamo po žetonu. Operaterja prepoznamo samo, kadar pride zahteva od gatewaya
(njegov žeton) z veljavnim UUID v X-Operator-Id; gateway to glavo nastavi po preverjanju JWT.
"""

import hmac
from dataclasses import dataclass
from typing import Annotated
from uuid import UUID

from fastapi import Security
from fastapi.security import APIKeyHeader

from app.core.config import service_tokens
from app.core.errors import ApiError

service_token_header = APIKeyHeader(
    name="X-Service-Token", scheme_name="ServiceToken", auto_error=False
)
operator_id_header = APIKeyHeader(name="X-Operator-Id", scheme_name="OperatorId", auto_error=False)


@dataclass
class Caller:
    name: str  # npr. "gateway", "sales", "inventory"
    operator_id: UUID | None = None  # nastavljen samo za operaterja


def allowed_callers(*callers: str, operator: bool = False):
    """Odvisnost poti, npr. Depends(allowed_callers("sales", operator=True)).

    401 UNAUTHENTICATED: žetona ni ali ni znan (ali napačen X-Operator-Id).
    403 CALLER_NOT_ALLOWED: klicatelj ni na seznamu dovoljenih za to pot.
    """

    def check(
        token: Annotated[str | None, Security(service_token_header)],
        operator_id: Annotated[str | None, Security(operator_id_header)],
    ) -> Caller:
        caller = identify_caller(token)
        if caller is None:
            raise ApiError(401, "UNAUTHENTICATED", "Missing or invalid service token.")

        if caller == "gateway" and operator_id and operator_id.strip():
            try:
                operator_uuid = UUID(operator_id)
            except ValueError:
                raise ApiError(401, "UNAUTHENTICATED", "Invalid operator identity.") from None
            if operator:
                return Caller(name="gateway", operator_id=operator_uuid)

        if caller not in callers:
            raise ApiError(403, "CALLER_NOT_ALLOWED", "Caller is not allowed to use this route.")
        return Caller(name=caller)

    return check


def identify_caller(token: str | None) -> str | None:
    """Vrne ime klicatelja, čigar žeton se ujema, sicer None (primerjava v konstantnem času)."""
    if not token:
        return None
    for caller, known_token in service_tokens().items():
        if hmac.compare_digest(token.encode(), known_token.encode()):
            return caller
    return None
