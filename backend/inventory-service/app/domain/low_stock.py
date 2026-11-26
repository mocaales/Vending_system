"""Nizka zaloga: outbox poročil LOW_STOCK za Monitoring (docs/03 §5 in §7, docs/01 §3).

Za vsak avtomat je v outboxu ena vrstica z zadnjim poročilom. Sprememba zaloge jo posodobi v
isti transakciji, delavec pa jo pošlje kasneje. Monitoring zavrne starejšo ali enako verzijo,
zato je dovolj poslati samo zadnje poročilo; vmesna se lahko preskočijo.
"""

import logging
from uuid import UUID

from sqlalchemy import select, update
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.clients.monitoring import MonitoringClient
from app.repository.models import LowStockReport, Slot

logger = logging.getLogger("app.low_stock")


def low_slots(session: Session, machine_id: UUID) -> list[dict]:
    """Predali z izdelkom in količino do praga; predal brez izdelka v poročilu ni."""
    slots = session.scalars(
        select(Slot)
        .where(
            Slot.machine_id == machine_id,
            Slot.product_id.is_not(None),
            Slot.quantity <= Slot.low_stock_threshold,
        )
        .order_by(Slot.slot)
    ).all()
    return [
        {
            "slotCode": stock.slot,
            "productId": str(stock.product_id),
            "quantity": stock.quantity,
            "lowStockThreshold": stock.low_stock_threshold,
        }
        for stock in slots
    ]


def record_low_stock(session: Session, machine_id: UUID) -> None:
    """Po spremembi zaloge (pred commit): če se je seznam nizkih predalov spremenil, zapiše
    novo poročilo z večjo verzijo. Prazen seznam pomeni, da nizke zaloge ni več."""
    # Vrstica avtomata je zaklenjena do commit, zato sočasne spremembe dobijo zaporedne verzije.
    session.execute(
        insert(LowStockReport)
        .values(machine_id=machine_id, version=0, slots=[], sent_version=0)
        .on_conflict_do_nothing()
    )
    report = session.get(LowStockReport, machine_id, with_for_update=True, populate_existing=True)

    slots = low_slots(session, machine_id)  # šele po zaklepu: vidi vse prejšnje spremembe
    if slots != report.slots:
        report.version += 1
        report.slots = slots


def send_pending(session: Session, monitoring: MonitoringClient) -> None:
    """Pošlje poročila, ki še niso poslana. Neuspešno ostane v outboxu za naslednji poskus."""
    pending = session.scalars(
        select(LowStockReport).where(LowStockReport.sent_version < LowStockReport.version)
    ).all()
    reports = [
        {
            "machineId": str(report.machine_id),
            "type": "LOW_STOCK",
            "version": report.version,
            "slots": report.slots,
        }
        for report in pending
    ]
    session.commit()  # med klici Monitoringa ni odprte transakcije

    for report in reports:
        if not monitoring.report_low_stock(report):
            logger.warning(
                "LOW_STOCK report for machine %s not accepted, will retry", report["machineId"]
            )
            continue
        session.execute(
            update(LowStockReport)
            .where(
                LowStockReport.machine_id == UUID(report["machineId"]),
                LowStockReport.sent_version < report["version"],
            )
            .values(sent_version=report["version"])
        )
        session.commit()
