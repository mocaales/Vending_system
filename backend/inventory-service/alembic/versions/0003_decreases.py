"""Zmanjšanja zaloge po prodaji (idempotenca po saleId) in saleId pri premiku SALE.

Revision ID: 0003
Revises: 0002
"""

import sqlalchemy as sa
from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "decreases",
        sa.Column("sale_id", sa.Uuid(), primary_key=True),
        sa.Column("machine_id", sa.Uuid(), nullable=False),
        sa.Column("slot", sa.String(8), nullable=False),
        sa.Column("product_id", sa.Uuid(), nullable=False),
        sa.Column("quantity", sa.Integer(), nullable=False),
        sa.Column("result", sa.String(25), nullable=False),
        sa.Column("reason", sa.String(20), nullable=True),
        sa.Column("quantity_after", sa.Integer(), nullable=False),
    )
    op.add_column("movements", sa.Column("sale_id", sa.Uuid(), nullable=True))


def downgrade() -> None:
    op.drop_column("movements", "sale_id")
    op.drop_table("decreases")
