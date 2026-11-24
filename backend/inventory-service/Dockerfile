# 1. stopnja: namestitev storitve in odvisnosti v virtualno okolje /venv
FROM python:3.12-alpine AS build
WORKDIR /build
RUN python -m venv /venv
COPY pyproject.toml ./
COPY app ./app
RUN /venv/bin/pip install --no-cache-dir .

# 2. stopnja: samo tisto, kar potrebuje zagon (brez pip predpomnilnika in izvorne kode gradnje)
FROM python:3.12-alpine
RUN adduser -D -H service
WORKDIR /service
COPY --from=build /venv /venv
COPY alembic.ini ./
COPY alembic ./alembic
USER service
EXPOSE 8000
CMD ["/venv/bin/uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-access-log"]
