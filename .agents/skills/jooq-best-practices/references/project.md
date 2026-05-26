# Project overlay

## Repository state

This repository is **pre-implementation**. Treat this skill as guidance for the planned implementation, not as a description of code that already exists.

## Planned persistence stack

- PostgreSQL 17
- pgvector
- jOOQ + repository pattern
- Flyway migrations
- `generated.jooq` for code generation output
- no JPA

## Planned package locations

- Root package: `dev.analyser`
- Persistence adapters: `dev.analyser.adapter.out.persistence`
- RAG adapter: `dev.analyser.adapter.out.rag`

## Project-specific rules

- Keep SQL access in repository / adapter classes, not UI or domain services.
- Respect Hexagonal Architecture boundaries.
- Do not hand-edit `generated.jooq`.
- Preserve repository terminology from the spec and architecture docs.
- The `rag_chunks` table is central to query behavior and stores chunk text, embeddings, and metadata.

## Database context

Important documented choices:

- cosine similarity via pgvector `<=>`
- configurable similarity threshold (default `0.75`)
- chunk size `512` tokens with `64` token overlap
- jobs become queryable only after status `INDEXED`
