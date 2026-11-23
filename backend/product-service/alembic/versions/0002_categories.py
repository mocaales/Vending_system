"""Tabela kategorij z enoličnim imenom ne glede na velike in male črke.

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
        "categories",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("name", sa.String(60), nullable=False),
        sa.Column("description", sa.String(200), nullable=True),
    )
    op.create_index("categories_name_unique", "categories", [sa.text("lower(name)")], unique=True)


def downgrade() -> None:
    op.drop_table("categories")
