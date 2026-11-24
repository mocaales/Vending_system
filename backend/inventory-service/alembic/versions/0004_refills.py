"""Polnitve (idempotenca po refillId), refillId pri premiku REFILL in iskanje premikov po času.

Revision ID: 0004
Revises: 0003
"""

import sqlalchemy as sa
from alembic import op

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "refills",
        sa.Column("refill_id", sa.Uuid(), primary_key=True),
        sa.Column("machine_id", sa.Uuid(), nullable=False),
        sa.Column("slot", sa.String(8), nullable=False),
        sa.Column("product_id", sa.Uuid(), nullable=False),
        sa.Column("batch_code", sa.String(40), nullable=False),
        sa.Column("expiry_date", sa.Date(), nullable=False),
        sa.Column("quantity", sa.Integer(), nullable=False),
    )
    op.add_column("movements", sa.Column("refill_id", sa.Uuid(), nullable=True))
    # Seznam premikov je razvrščen po času (najnovejši najprej) in filtriran po from/to.
    op.create_index("movements_occurred_at", "movements", ["occurred_at"])


def downgrade() -> None:
    op.drop_index("movements_occurred_at", "movements")
    op.drop_column("movements", "refill_id")
    op.drop_table("refills")
