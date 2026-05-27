# Java Project Analyser

This repository now contains two separate applications:

- the MCP server at the repository root
- the standalone Vaadin demo client in `demo-client/`

## Quickstart

Run the MCP server stack with one command:

```sh
docker compose --profile local up
```

The local profile starts:
- `db` with `pgvector/pgvector:pg17`
- `ollama` with `ollama/ollama`
- `app` with the MCP server Quarkus JVM image

## Environment

Copy `.env.example` to `.env` before using cloud profiles.

- `QUARKUS_PROFILE=local` keeps the local Ollama setup.
- `QUARKUS_PROFILE=openai` plus `OPENAI_API_KEY` starts the OpenAI profile.
- `QUARKUS_PROFILE=anthropic` reserves the Anthropic profile for later work.

## Local development

Run the MCP server verification build:

```sh
./mvnw clean verify
```

Start the MCP server in Quarkus dev mode:

```sh
./mvnw quarkus:dev
```

The Quarkus Dev UI is available at <http://localhost:8080/q/dev/>.

## Demo client

The Vaadin demo client is a separate application under `demo-client/`.

Run its tests:

```sh
cd demo-client && ./mvnw test
```

Start it in dev mode:

```sh
cd demo-client && ./mvnw quarkus:dev
```

The demo client defaults to port `8082` and expects the MCP server at `http://localhost:8080`.
