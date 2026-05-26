# Java Project Analyser

This repository now contains the issue #4 scaffold for Quarkus, PostgreSQL, Flyway, jOOQ, and Docker Compose profiles.

## Quickstart

Run the full local stack with one command:

```sh
docker compose --profile local up
```

The local profile starts:
- `db` with `pgvector/pgvector:pg17`
- `ollama` with `ollama/ollama`
- `app` with the Quarkus JVM image

## Environment

Copy `.env.example` to `.env` before using cloud profiles.

- `QUARKUS_PROFILE=local` keeps the local Ollama setup.
- `QUARKUS_PROFILE=openai` plus `OPENAI_API_KEY` starts the OpenAI profile.
- `QUARKUS_PROFILE=anthropic` reserves the Anthropic profile for later work.

## Local development

Run the Maven verification build:

```sh
./mvnw clean verify
```

Start Quarkus dev mode:

```sh
./mvnw quarkus:dev
```

The Quarkus Dev UI is available at <http://localhost:8080/q/dev/>.
