# Project overlay

## Repository state

This repository is **pre-implementation**. The Vaadin UI is planned, but no UI code or browser test harness is committed yet.

## Planned UI stack

- Vaadin Flow
- server-push via WebSocket
- Quarkus backend

## End-to-end review guidance

- Prefer **Playwright** or **Vaadin TestBench** once the UI and test harness exist.
- Review flows that map to documented use cases, especially:
  - starting an analysis
  - monitoring progress
  - downloading a report
  - running query workflows

## Command guidance

Before running UI tests, verify the branch contains the actual test harness and commands.

## Screenshot guidance

Use the project's real screenshot path if one exists. If not, discover it from the test setup instead of assuming a default.
