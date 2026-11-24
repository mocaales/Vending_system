"""Nastavitve storitve. Vse pridejo iz okolja (Compose jih prebere iz .env), nič ni v kodi."""

import os

# Ime klicatelja -> spremenljivka okolja z njegovim notranjim žetonom (glava X-Service-Token).
SERVICE_TOKEN_VARIABLES = {
    "gateway": "GATEWAY_SERVICE_TOKEN",
    "machine": "MACHINE_SERVICE_TOKEN",
    "monitoring": "MONITORING_SERVICE_TOKEN",
    "sales": "SALES_SERVICE_TOKEN",
    "inventory": "INVENTORY_SERVICE_TOKEN",
    "product": "PRODUCT_SERVICE_TOKEN",
}


def database_url() -> str:
    """Povezava do lastne baze, npr. postgresql+psycopg://inventory:<geslo>@inventory-db:5432/inventory."""
    return os.environ["DATABASE_URL"]


def own_service_token() -> str:
    """Žeton, s katerim Inventory kliče druge storitve."""
    return os.environ.get("INVENTORY_SERVICE_TOKEN", "")


def machine_url() -> str:
    return os.environ.get("MACHINE_URL", "http://machine-service:8000")


def product_url() -> str:
    return os.environ.get("PRODUCT_URL", "http://product-service:8000")


def service_tokens() -> dict[str, str]:
    """Znani žetoni klicateljev; nenastavljen (prazen) žeton se ne upošteva."""
    tokens = {}
    for caller, variable in SERVICE_TOKEN_VARIABLES.items():
        token = os.environ.get(variable, "")
        if token:
            tokens[caller] = token
    return tokens
