# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

Pre-implementation — documentation is complete; no source code exists yet.
`AGENTS.md` is the authoritative process contract; read it before doing anything non-trivial.

## Key Documents

| Document | Location |
|---|---|
| PRD | `src/docs/specs/prd.adoc` |
| Specifications | `src/docs/specs/` |
| Architecture (arc42) | `src/docs/arc42/arc42.adoc` |
| Reviews | `src/docs/reviews/` |

## Technology Stack

| Concern | Technology | Notes |
|---|---|---|
| Application framework | Quarkus 3 | JVM 21+ required (ADR-001) |
| Browser UI | Vaadin Flow | `vaadin-quarkus-extension`; server-push via WebSocket (ADR-002) |
| LLM integration | LangChain4J + `quarkus-langchain4j` | Ollama, OpenAI, Anthropic via CDI provider selection (ADR-003) |
| Embedding models | LangChain4J `EmbeddingModel` | Same CDI selection; Ollama `nomic-embed-text`, OpenAI `text-embedding-3-small` |
| Architecture style | Hexagonal (Ports & Adapters) | Root package `dev.analyser` (ADR-004) |
| Deployment | Docker Compose profiles | `local` (Ollama), `openai`, `anthropic` (ADR-005) |
| Database | PostgreSQL 17 + pgvector | `pgvector/pgvector:pg17` image; JSONB for phase results; vector columns for RAG; Flyway migrations (ADR-007, ADR-008) |
| Data access | jOOQ + repository pattern | Type-safe SQL DSL; codegen from schema including `rag_chunks`; no JPA (ADR-006) |
| Build tool | Maven | Standard Quarkus Maven wrapper; jOOQ codegen in `generate-sources` |

## System Operating Modes

The system has **two sequential operating modes**:

1. **Analyse** (Phases 1–10) — source code → LLM pipeline → static report + RAG knowledge base indexed in PostgreSQL.
2. **Query** (UC-004 to UC-007) — user question → semantic retrieval from knowledge base → LLM-generated answer.

Query capabilities are only available after a job reaches `INDEXED` status (Phase 10 complete).

## Use Cases

| ID | Title | Mode |
|---|---|---|
| UC-001 | Analyse a Java Project | Analyse |
| UC-002 | Resume an Interrupted Analysis | Analyse |
| UC-003 | Configure the Analyser | Configuration |
| UC-004 | Analyse Risks (security / usability / readability) | Query |
| UC-005 | Generate System Documentation | Query |
| UC-006 | Find Bugs Using the RAG Knowledge Base | Query |
| UC-007 | LLM-Assisted Feature Implementation Guidance | Query |

## Package Structure (`dev.analyser`)

```
dev.analyser
├── domain.model              ← pure entities, no framework annotations
├── domain.service.phase      ← PhaseExecutor interface + 9 implementations
├── domain.service            ← RagIndexingService, PromptTemplateLoader
├── domain.service.query      ← RiskAnalyserService, DocumentationComposerService,
│                               BugFinderService, FeatureAdvisorService
├── application.port.in       ← AnalysisPort (inbound), QueryPort (inbound)
├── application.port.out      ← LlmPort, EmbeddingPort, RagPort,
│                               ProjectSourcePort, CachePort, ReportPort
├── application.service       ← AnalysisPipelineService, QueryOrchestrationService
├── adapter.in.ui             ← Vaadin views (AnalysisView, ProgressView, ReportView,
│                               QueryView, QueryResultView)
├── adapter.out.llm           ← LangChain4JLlmAdapter, LangChain4JEmbeddingAdapter
├── adapter.out.project       ← FilesystemProjectAdapter, GitProjectAdapter
├── adapter.out.cache         ← FilesystemCacheAdapter
├── adapter.out.report        ← AsciiDocReportAdapter
├── adapter.out.rag           ← PostgresRagAdapter
├── adapter.out.persistence   ← AnalysisJobRepository, LlmCallRepository,
│                               ReportRepository, KnowledgeGraphRepository,
│                               RagChunkRepository, QueryResultRepository (jOOQ)
└── infrastructure.config     ← AnalysisConfig, QueryConfig (@ConfigMapping)
generated.jooq                ← jOOQ codegen output (never hand-edited)
```

Dependency rule: adapters → ports → domain. Domain has zero framework imports.

## Conventions (summary — `AGENTS.md` is authoritative)

**Commits:** Conventional Commits format, reference the GitHub issue number.

**Branching:** Trunk-based development; feature branches per EPIC, not per story.

**Testing:** TDD. Every test references a Use Case ID for traceability. London school (mock collaborators) or Chicago school (state-based) as appropriate to the layer under test.

**Code:** DRY, SOLID, KISS. Use DDD Ubiquitous Language — terms in code must match terms in the specification exactly.

**Documentation:** AsciiDoc + PlantUML + docToolchain (`dtcw` wrapper). PlantUML diagrams use the C4-PlantUML stdlib via `!include <C4/...>` (angle-bracket form only — no remote URLs, no vendored copies). Diagrams are PlantUML, not Mermaid.

**Architecture docs:** arc42 template via docToolchain `downloadTemplate`. Decisions are Nygard ADRs with a 3-point Pugh Matrix.

**Specifications:** Persona Use Cases in Cockburn Fully Dressed format + System Use Cases + Gherkin acceptance criteria + EARS requirements. See `AGENTS.md § Specification` for the full contract.

## Layer Boundaries

- Expose only DTOs across layer seams — never domain entities.
- Dependency direction points inward (DIP).
- Apply Anti-Corruption Layers when integrating external systems.
- Explicit mapping at every boundary.

## RAG Knowledge Base Key Facts

- Table: `rag_chunks` — columns: `job_id`, `phase_id`, `chunk_index`, `chunk_text`, `embedding VECTOR(N)`, `metadata JSONB`
- Similarity: pgvector cosine distance `<=>` operator; threshold 0.75 (configurable via `QueryConfig`)
- Chunk size: 512 tokens, 64-token overlap (configurable via `AnalysisConfig`)
- Embedding dimension N is set per deployment profile: Ollama `nomic-embed-text` → 768; OpenAI `text-embedding-3-small` → 1536
- Jobs become queryable only when `analysis_jobs.status = 'INDEXED'`

## Architecture Crosscutting Concepts (arc42 Chapter 8)

The five required Chapter 8 sections, in order: Threat Model (STRIDE), Security (mitigations reference T-IDs), Test (pyramid tracing to Use Cases), Observability, Error Handling. Add further concepts only when the system actually has that concern.

## Implementation Order

1. Scaffold arc42 template via `dtcw downloadTemplate`.
2. Build the walking skeleton (deployable end-to-end slice through all layers, does almost nothing).
3. Grow via thin vertical slices. Use spike solutions (throwaway) to de-risk unknowns before committing to a slice.
4. Suggested slice order: Phases 1–9 pipeline → Phase 10 indexing → UC-004 Risk Analysis → remaining query capabilities.
5. See `AGENTS.md § Implement Next` for the per-issue workflow.
