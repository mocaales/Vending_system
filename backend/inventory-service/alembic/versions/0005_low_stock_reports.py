"""Outbox nizke zaloge: zadnje poročilo LOW_STOCK za vsak avtomat.

Revision ID: 0005
Revises: 0004
"""

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import JSONB

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "low_stock_reports",
        sa.Column("machine_id", sa.Uuid(), primary_key=True),
        sa.Column("version", sa.BigInteger(), nullable=False),
        sa.Column("slots", JSONB(), nullable=False),
        sa.Column("sent_version", sa.BigInteger(), nullable=False),
    )


def downgrade() -> None:
    op.drop_table("low_stock_reports")
