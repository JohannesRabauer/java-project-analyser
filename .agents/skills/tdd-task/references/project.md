# Project overlay

## Repository state

This repository is **pre-implementation**. There is no committed Quarkus source tree, Maven wrapper, or runnable test suite yet.

## Test strategy for this repository

- Testing is **TDD**.
- Every test should reference the relevant **Use Case ID** or **Business Rule ID** for traceability.
- Choose London or Chicago style as appropriate to the layer under test.
- For user-observable UI behavior in the future Vaadin app, plan both:
  - a focused unit/service/integration test
  - an end-to-end browser test

## Planned test stack

- JUnit 5
- Mockito
- Testcontainers
- WireMock
- Playwright or Vaadin TestBench for end-to-end UI coverage

## Command guidance

Before running build or test commands, verify the current branch actually contains files such as:

- `pom.xml`
- `mvnw`
- test sources
- Playwright or Vaadin test configuration

Do not invent commands that are not yet backed by committed build files.

## Architecture constraints to preserve

- Hexagonal Architecture
- adapters -> ports -> domain dependency direction
- zero framework imports in domain
- DTOs/contracts only across boundaries
- no writes to the analysed project
