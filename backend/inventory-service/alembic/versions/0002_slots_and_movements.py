"""Zaloga predalov in premiki zaloge.

Revision ID: 0002
Revises: 0001
"""

import sqlalchemy as sa
from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "slots",
        sa.Column("machine_id", sa.Uuid(), primary_key=True),
        sa.Column("slot", sa.String(8), primary_key=True),
        sa.Column("product_id", sa.Uuid(), nullable=True),
        sa.Column("batch_code", sa.String(40), nullable=True),
        sa.Column("expiry_date", sa.Date(), nullable=True),
        sa.Column("quantity", sa.Integer(), nullable=False),
        sa.Column("low_stock_threshold", sa.Integer(), nullable=False),
    )
    # Hitro iskanje predalov z izdelkom (GET /inventory/product/{productId}).
    op.create_index("slots_product_id", "slots", ["product_id"])

    op.create_table(
        "movements",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("machine_id", sa.Uuid(), nullable=False),
        sa.Column("slot", sa.String(8), nullable=False),
        sa.Column("type", sa.String(10), nullable=False),
        sa.Column("product_id", sa.Uuid(), nullable=False),
        sa.Column("batch_code", sa.String(40), nullable=True),
        sa.Column("quantity", sa.Integer(), nullable=False),
        sa.Column(
            "occurred_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )
    op.create_index("movements_machine_id", "movements", ["machine_id"])
    op.create_index("movements_product_id", "movements", ["product_id"])


def downgrade() -> None:
    op.drop_table("movements")
    op.drop_table("slots")
