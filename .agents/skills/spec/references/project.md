# Project overlay

## Repository state

This repository is **pre-implementation**. It currently contains specifications and architecture documents, not the Quarkus application code yet.

## Authoritative sources

- `AGENTS.md`
- `CLAUDE.md`
- `src/docs/specs/prd.adoc`
- `src/docs/specs/business-rules.adoc`
- `src/docs/arc42/arc42.adoc`

## Output conventions for this repository

- Write specs in **AsciiDoc**, not Markdown.
- Put product/specification work under `src/docs/specs/`.
- When adding or updating use cases, follow the repository contract:
  - Persona use cases in Cockburn Fully Dressed format
  - System use cases per interface
  - Gherkin acceptance criteria
  - EARS requirements where applicable
  - Business Rules with stable `BR-XXX` identifiers

## Domain context

The planned product is a **Java Project Analyser** with two operating modes:

1. **Analyse** — 10-phase analysis pipeline
2. **Query** — RAG-backed risk, documentation, bug-finding, and feature-guidance queries

## Planned stack

- Quarkus 3
- Vaadin Flow
- LangChain4J
- PostgreSQL 17 + pgvector
- jOOQ + repository pattern
- Docker Compose profiles: `local`, `openai`, `anthropic`

## Writing rules

- Use plain English according to Strunk & White.
- Use ubiquitous language from the spec consistently.
- Prefer updating existing spec documents over creating parallel ad-hoc docs.
