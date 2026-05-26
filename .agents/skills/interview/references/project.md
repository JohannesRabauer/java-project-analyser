# Project overlay

## Repository state

This repository is **pre-implementation** and documentation-first.

## Interview style for this project

- Follow the **Socratic Method** from `AGENTS.md`.
- Ask focused questions that challenge assumptions.
- Keep questions MECE.
- Prefer resolving questions from existing docs first; ask only what changes the design.

## Where to read first

- `src/docs/specs/prd.adoc`
- `src/docs/specs/business-rules.adoc`
- `src/docs/specs/use-cases/`
- `src/docs/arc42/`
- `AGENTS.md`

## Roles to keep in mind

Primary user groups currently documented in the PRD:

- Software Architects
- Onboarding Developers
- Technical Writers
- Project Managers / Leads
- Developers working on the analysed project

## Cross-cutting invariants to probe

Read and preserve:

- `src/docs/specs/business-rules.adoc`
- `src/docs/arc42/chapters/08-crosscutting-concepts.adoc`

Key recurring concerns are:

- fixed phase ordering
- cache-after-each-phase resumability
- read-only treatment of the analysed project
- explicit uncertainty instead of hallucinated answers
- RAG indexing before query availability

## Real-time / UX assumptions

The planned UI is Vaadin Flow with **server-push via WebSocket** for live progress updates.
