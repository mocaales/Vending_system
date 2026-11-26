# 1. stopnja: namestitev simulatorja in odvisnosti v virtualno okolje /venv
FROM python:3.12-alpine AS build
WORKDIR /build
RUN python -m venv /venv
COPY pyproject.toml ./
COPY simulator ./simulator
RUN /venv/bin/pip install --no-cache-dir .

# 2. stopnja: samo tisto, kar potrebuje zagon
FROM python:3.12-alpine
RUN adduser -D -H simulator
COPY --from=build /venv /venv
USER simulator
CMD ["/venv/bin/python", "-m", "simulator"]
