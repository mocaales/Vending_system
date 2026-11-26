"""Obnašanje avtomatov: kdaj nastane meritev ali prodaja in kdaj je povezava izgubljena."""

import logging
import random
from argparse import Namespace
from datetime import datetime, timedelta

from simulator.events import PAYMENT_METHODS, measurement_event, sale_event
from simulator.sender import MEASUREMENTS_PATH, SALES_PATH, Sender

logger = logging.getLogger("simulator")


def seconds(value: float) -> timedelta:
    return timedelta(seconds=value)


class Simulator:
    def __init__(
        self,
        machines: list[dict],
        sender: Sender,
        settings: Namespace,
        start: datetime,
        rng: random.Random | None = None,
    ):
        self.machines = machines
        self.sender = sender
        self.settings = settings
        self.rng = rng or random.Random()
        # Prva meritev takoj, prva prodaja po enem intervalu.
        self.next_measurement = {m["machineId"]: start for m in machines}
        self.next_sale = {m["machineId"]: start + seconds(settings.sale_every) for m in machines}
        self.offline_from = start + seconds(settings.offline_after)
        self.offline_until = self.offline_from + seconds(settings.offline_for)
        self.was_offline = False

    def is_offline(self, now: datetime) -> bool:
        """Scenarij izgube povezave: dogodki nastajajo, a se ne pošiljajo."""
        return self.offline_from <= now < self.offline_until

    def step(self, now: datetime) -> None:
        """Ustvari dogodke, ki so na vrsti, in pošlje vrsto, če je povezava na voljo."""
        for machine in self.machines:
            machine_id = machine["machineId"]
            if now >= self.next_measurement[machine_id]:
                self.measure(machine, now)
                self.next_measurement[machine_id] = now + seconds(self.settings.measurement_every)
            if machine["slots"] and now >= self.next_sale[machine_id]:
                self.sell(machine, now)
                pause = self.settings.sale_every * self.rng.uniform(0.5, 1.5)
                self.next_sale[machine_id] = now + seconds(pause)

        offline = self.is_offline(now)
        if offline != self.was_offline:
            logger.warning("Connection %s", "lost" if offline else "restored")
            self.was_offline = offline
        if not offline:
            self.sender.flush()

    def measure(self, machine: dict, now: datetime) -> None:
        temperature = self.settings.temperature + self.rng.uniform(-0.3, 0.3)
        event = measurement_event(machine["machineId"], temperature, self.settings.door, now)
        self.sender.add(MEASUREMENTS_PATH, machine["machineKey"], event)

    def sell(self, machine: dict, now: datetime) -> None:
        slot = self.rng.choice(machine["slots"])
        payment = self.rng.choice(PAYMENT_METHODS)
        event = sale_event(machine["machineId"], slot, payment, now)
        self.sender.add(SALES_PATH, machine["machineKey"], event)
