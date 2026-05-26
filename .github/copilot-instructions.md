# Copilot instructions for this repository

## Project state

This repository is **pre-implementation**. It currently contains architecture and specification documents under `src/docs/`, plus process guidance in `AGENTS.md` and `CLAUDE.md`. Do not assume application code, Maven wrappers, Docker Compose files, or test suites already exist just because the docs describe the planned system.

## Build, test, and lint commands

There are **no committed build, test, or lint commands yet** because the repository does not contain application source or build files.

Before suggesting or running commands, first check whether files such as `pom.xml`, `mvnw`, `docker-compose.yml`, `dtcw`, or test sources have actually been added in the current branch.

The documentation describes the intended future tooling:

- **Build tool:** Maven for the Quarkus application.
- **Documentation toolchain:** docToolchain via `dtcw`.
- **Planned deployment profiles:** Docker Compose profiles `local`, `openai`, and `anthropic`.
- **Planned test stack:** JUnit 5, Mockito, Testcontainers, WireMock, and Playwright or Vaadin TestBench for end-to-end UI coverage.

Only turn those into runnable commands when the corresponding files are present.

## High-level architecture

The planned product is a **browser-based Java Project Analyser**: a Quarkus application with a Vaadin UI that analyses a Java codebase, writes a structured report, and persists a RAG knowledge base in PostgreSQL with pgvector.

The system has **two sequential operating modes**:

1. **Analyse**: a 10-phase pipeline reads a Java project, runs phases 1-9 of LLM-assisted analysis, caches every completed phase result, generates an AsciiDoc report, and then performs Phase 10 RAG indexing.
2. **Query**: once a job reaches `INDEXED`, the system answers four query types against the indexed knowledge base: risk analysis, documentation generation, bug finding, and feature implementation guidance.

The planned application structure is **Hexagonal Architecture (Ports & Adapters)** under the root package `dev.analyser`:

- **Inbound adapters:** Vaadin views
- **Application layer:** `AnalysisPipelineService`, `QueryOrchestrationService`
- **Domain services:** phase executors, RAG indexing, and query-specific services
- **Outbound ports:** `LlmPort`, `EmbeddingPort`, `RagPort`, `ProjectSourcePort`, `CachePort`, `ReportPort`
- **Outbound adapters:** LangChain4J adapters, filesystem and Git project adapters, filesystem cache, AsciiDoc report adapter, PostgreSQL/jOOQ persistence

Keep the big separation clear: **analysis produces the knowledge base; query uses the knowledge base**. Query features are not available before Phase 10 finishes successfully.

## Key conventions

Follow these repository-specific conventions from `AGENTS.md` and `CLAUDE.md`:

- Use **DDD ubiquitous language** from the specification. Terms in code, tests, and docs should match the spec exactly.
- Preserve **inward dependency direction**: adapters -> ports -> domain. The domain layer should have **zero framework imports**.
- At every layer boundary, expose **DTOs/contracts only**, never domain entities. Use explicit mapping at every seam.
- This project is **docs-as-code first**. Key source documents are `src/docs/specs/prd.adoc`, `src/docs/specs/`, and `src/docs/arc42/arc42.adoc`.
- Architecture documentation follows **arc42**. Decisions are **Nygard ADRs** with a **3-point Pugh Matrix**.
- Diagrams are **PlantUML**, not Mermaid. For C4 diagrams, use the bundled stdlib include form: `!include <C4/...>`. Do not use remote URLs or vendored C4 files.
- Documentation is written in **plain English** following Strunk & White.
- Testing is **TDD**, and tests should reference the relevant **Use Case ID** or **Business Rule ID** for traceability.
- The intended stack is fixed enough to respect in new work unless the docs are being changed deliberately: **Quarkus 3**, **Vaadin Flow**, **LangChain4J**, **PostgreSQL 17 + pgvector**, **jOOQ + repository pattern**, **Docker Compose profiles** for provider selection.
- The analysed project must be treated as **read-only**. The analyser may read source and write outputs to cache/output locations, but it must not modify, compile, or execute the analysed project.
- When implementing query behavior, preserve documented RAG rules such as **cosine similarity via pgvector `<=>`**, configurable threshold/top-K, and the special requirement that feature guidance always includes architecture and class-analysis context.

## Source documents to consult first

- `AGENTS.md` — authoritative process contract
- `CLAUDE.md` — concise repo summary and planned package structure
- `src/docs/specs/prd.adoc` — product scope and 10 analysis phases
- `src/docs/specs/business-rules.adoc` — stable BR-IDs and behavioral rules
- `src/docs/arc42/chapters/03-context-and-scope.adoc`
- `src/docs/arc42/chapters/04-solution-strategy.adoc`
- `src/docs/arc42/chapters/05-building-block-view.adoc`
- `src/docs/arc42/chapters/06-runtime-view.adoc`
- `src/docs/arc42/chapters/07-deployment-view.adoc`
- `src/docs/arc42/chapters/08-crosscutting-concepts.adoc`

## Installed local agent skills

This repository now includes selected local skills under `.agents/skills/`:

- `interview`
- `spec`
- `tdd-task`
- `ui-review`
- `jooq-best-practices`

Each installed skill has a `references/project.md` overlay with repository-specific guidance. Read that overlay before using the skill.
