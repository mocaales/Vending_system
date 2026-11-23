"""Tabela izdelkov; kategorije, ki jo uporablja izdelek, ni mogoče izbrisati.

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
        "products",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("name", sa.String(100), nullable=False),
        sa.Column("category_id", sa.Uuid(), sa.ForeignKey("categories.id"), nullable=False),
        sa.Column("reference_price", sa.Numeric(12, 2), nullable=False),
        sa.Column("currency", sa.String(3), nullable=False),
        sa.Column("weight_grams", sa.Integer(), nullable=False),
        sa.Column("allergens", sa.ARRAY(sa.String(50)), nullable=False),
        sa.Column("image_url", sa.String(500), nullable=False),
        sa.Column("status", sa.String(10), nullable=False),
    )
    # Hitro iskanje izdelkov po kategoriji (filter categoryId in preverjanje pred izbrisom).
    op.create_index("products_category_id", "products", ["category_id"])


def downgrade() -> None:
    op.drop_table("products")
