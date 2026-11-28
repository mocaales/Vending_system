"""Lazni gateway za teste skript (httpx.MockTransport), brez Compose."""

import json

import httpx


class FakeGateway:
    """Odgovori po tabeli (metoda, pot) -> (status, telo); seznam pomeni zaporedne odgovore."""

    def __init__(self, routes):
        self.routes = routes
        self.requests = []

    def handle(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        answer = self.routes[(request.method, request.url.path)]
        if isinstance(answer, list):
            answer = answer.pop(0) if len(answer) > 1 else answer[0]
        status, body = answer
        return httpx.Response(status, json=body)

    def sent(self, index):
        return json.loads(self.requests[index].content)
