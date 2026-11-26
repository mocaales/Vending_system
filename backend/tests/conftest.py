"""Testi skript v backend/ (brez Compose): `python -m pytest backend/tests`."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parents[1]))  # skripte so v backend/, ne v paketu
