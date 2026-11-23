"""Glave, s katerimi v testih kličejo gateway (v imenu operaterja) in druge storitve."""

OPERATOR_ID = "3f2b8c1e-7d4a-4c9e-9b1f-0a2d3e4f5a6b"
OPERATOR = {"X-Service-Token": "gateway-token", "X-Operator-Id": OPERATOR_ID}
FRONTEND_API = {"X-Service-Token": "gateway-token"}
SALES = {"X-Service-Token": "sales-token"}
INVENTORY = {"X-Service-Token": "inventory-token"}
MONITORING = {"X-Service-Token": "monitoring-token"}

# Žetoni, ki jih testna aplikacija pozna (spremenljivka okolja -> vrednost).
SERVICE_TOKENS = {
    "GATEWAY_SERVICE_TOKEN": "gateway-token",
    "SALES_SERVICE_TOKEN": "sales-token",
    "INVENTORY_SERVICE_TOKEN": "inventory-token",
    "MONITORING_SERVICE_TOKEN": "monitoring-token",
}
